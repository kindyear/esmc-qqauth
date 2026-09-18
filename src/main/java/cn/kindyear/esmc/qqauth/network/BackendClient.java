package cn.kindyear.esmc.qqauth.network;

import cn.kindyear.esmc.qqauth.config.PluginSettings;
import cn.kindyear.esmc.qqauth.protocol.Protocol;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BackendClient implements WebSocket.Listener {
    @FunctionalInterface
    public interface MessageHandler {
        void handle(Protocol.Envelope envelope) throws Exception;
    }

    private static final int MAX_PENDING = 100;
    private final PluginSettings settings;
    private final String pluginVersion;
    private final MessageHandler handler;
    private final Logger logger;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicBoolean connecting = new AtomicBoolean();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();
    private final Deque<String> pending = new ArrayDeque<>();
    private final StringBuilder incoming = new StringBuilder();
    private volatile WebSocket socket;
    private int reconnectAttempt;

    public BackendClient(
        PluginSettings settings, String pluginVersion, MessageHandler handler, Logger logger
    ) {
        this.settings = settings;
        this.pluginVersion = pluginVersion;
        this.handler = handler;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task, "esmc-qqauth-websocket");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        connect();
    }

    public void stop() {
        if (!stopped.compareAndSet(false, true)) return;
        var current = socket;
        socket = null;
        if (current != null) current.sendClose(WebSocket.NORMAL_CLOSURE, "Plugin disabled");
        scheduler.shutdownNow();
        synchronized (pending) { pending.clear(); }
    }

    public void send(String type, Object payload) {
        sendJson(Protocol.message(type, payload));
    }

    private void connect() {
        reconnectScheduled.set(false);
        if (stopped.get() || socket != null || !connecting.compareAndSet(false, true)) return;
        httpClient.newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer " + settings.bearerToken())
            .buildAsync(settings.backendUri(), this)
            .whenComplete((webSocket, error) -> {
                connecting.set(false);
                if (error != null) {
                    logger.log(Level.WARNING, "无法连接 QQAuth Backend: " + safeMessage(error));
                    scheduleReconnect();
                }
            });
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        socket = webSocket;
        reconnectAttempt = 0;
        logger.info("已连接 QQAuth Backend");
        var ready = Protocol.message(
            "client.ready", new Protocol.ClientReady(settings.serverId(), pluginVersion)
        );
        webSocket.sendText(ready, true).whenComplete((_ignored, error) -> {
            if (error == null) flushPending(webSocket);
            else failSocket(webSocket, error);
        });
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        incoming.append(data);
        if (last) {
            var message = incoming.toString();
            incoming.setLength(0);
            try {
                handler.handle(Protocol.parse(message));
            } catch (Exception error) {
                logger.log(Level.WARNING, "拒绝无效 Backend 消息: " + safeMessage(error));
            }
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
        webSocket.request(1);
        return webSocket.sendPong(message);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        if (socket == webSocket) socket = null;
        if (!stopped.get()) {
            logger.warning("QQAuth Backend 连接关闭 code=" + statusCode);
            scheduleReconnect();
        }
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        if (socket == webSocket) socket = null;
        if (!stopped.get()) {
            logger.log(Level.WARNING, "QQAuth WebSocket 异常: " + safeMessage(error));
            scheduleReconnect();
        }
    }

    private void sendJson(String message) {
        var current = socket;
        if (current == null) {
            enqueue(message);
            return;
        }
        current.sendText(message, true).whenComplete((_ignored, error) -> {
            if (error != null) {
                enqueue(message);
                failSocket(current, error);
            }
        });
    }

    private void flushPending(WebSocket webSocket) {
        while (socket == webSocket && !stopped.get()) {
            final String message;
            synchronized (pending) { message = pending.pollFirst(); }
            if (message == null) return;
            webSocket.sendText(message, true).whenComplete((_ignored, error) -> {
                if (error != null) {
                    enqueueFirst(message);
                    failSocket(webSocket, error);
                }
            });
        }
    }

    private void enqueue(String message) {
        synchronized (pending) {
            if (pending.size() >= MAX_PENDING) pending.removeFirst();
            pending.addLast(message);
        }
    }

    private void enqueueFirst(String message) {
        synchronized (pending) {
            if (pending.size() >= MAX_PENDING) pending.removeLast();
            pending.addFirst(message);
        }
    }

    private void failSocket(WebSocket webSocket, Throwable error) {
        logger.log(Level.WARNING, "QQAuth 消息发送失败: " + safeMessage(error));
        if (socket == webSocket) socket = null;
        webSocket.abort();
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (stopped.get() || socket != null || !reconnectScheduled.compareAndSet(false, true)) return;
        var multiplier = 1L << Math.min(reconnectAttempt++, 10);
        var delay = Math.min(
            settings.reconnectMaxSeconds(), settings.reconnectMinSeconds() * multiplier
        );
        scheduler.schedule(this::connect, delay, TimeUnit.SECONDS);
    }

    private static String safeMessage(Throwable error) {
        var cause = error.getCause() == null ? error : error.getCause();
        return cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage());
    }
}
