package cn.kindyear.esmc.qqauth.config;

import java.net.URI;
import org.bukkit.configuration.file.FileConfiguration;

public record PluginSettings(
    URI backendUri,
    String bearerToken,
    String serverId,
    int reconnectMinSeconds,
    int reconnectMaxSeconds,
    String pendingTitle,
    String pendingSubtitle,
    String boundTitle,
    String boundSubtitle,
    int titleRefreshTicks,
    int boundTitleStayTicks,
    int maxAttempts,
    String successMessage,
    String invalidCodeMessage,
    String attemptsExhaustedMessage,
    String restrictedMessage
) {
    public static PluginSettings from(FileConfiguration config) {
        var uri = URI.create(required(config.getString("backend.url"), "backend.url"));
        if (!"ws".equalsIgnoreCase(uri.getScheme()) && !"wss".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("backend.url 必须使用 ws:// 或 wss://");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("backend.url 不得包含凭证");
        }
        var token = required(config.getString("backend.bearer-token"), "backend.bearer-token");
        if (token.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("backend.bearer-token 至少需要 32 字节");
        }
        var serverId = required(config.getString("backend.server-id"), "backend.server-id");
        int minimum = config.getInt("backend.reconnect-min-seconds", 2);
        int maximum = config.getInt("backend.reconnect-max-seconds", 60);
        if (minimum < 1 || maximum < minimum || maximum > 600) {
            throw new IllegalArgumentException("WebSocket 重连间隔无效");
        }
        int refreshTicks = config.getInt("verification.title.refresh-ticks", 40);
        int boundStayTicks = config.getInt("verification.title.bound-stay-ticks", 80);
        if (refreshTicks < 20 || refreshTicks > 200) {
            throw new IllegalArgumentException("verification.title.refresh-ticks 必须在 20 到 200 之间");
        }
        if (boundStayTicks < 20 || boundStayTicks > 400) {
            throw new IllegalArgumentException("verification.title.bound-stay-ticks 必须在 20 到 400 之间");
        }
        int maxAttempts = config.getInt("verification.max-attempts", 3);
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException("verification.max-attempts 必须在 1 到 10 之间");
        }
        return new PluginSettings(
            uri, token, serverId, minimum, maximum,
            required(config.getString("verification.title.pending", "请完成 QQ 绑定"), "verification.title.pending"),
            required(config.getString("verification.title.pending-subtitle", "请在聊天栏输入 /verify <验证码>"), "verification.title.pending-subtitle"),
            required(config.getString("verification.title.bound", "绑定成功"), "verification.title.bound"),
            required(config.getString("verification.title.bound-subtitle", "开始探索吧"), "verification.title.bound-subtitle"),
            refreshTicks, boundStayTicks, maxAttempts,
            required(config.getString("verification.success-message"), "verification.success-message"),
            required(config.getString("verification.invalid-code-message"), "verification.invalid-code-message"),
            required(config.getString("verification.attempts-exhausted-message"), "verification.attempts-exhausted-message"),
            required(config.getString("verification.restricted-message"), "verification.restricted-message")
        );
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
        return value.trim();
    }
}
