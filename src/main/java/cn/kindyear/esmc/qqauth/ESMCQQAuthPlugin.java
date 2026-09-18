package cn.kindyear.esmc.qqauth;

import cn.kindyear.esmc.qqauth.command.VerifyCommand;
import cn.kindyear.esmc.qqauth.config.PluginSettings;
import cn.kindyear.esmc.qqauth.listener.RestrictedPlayerListener;
import cn.kindyear.esmc.qqauth.listener.WhitelistVerifyListener;
import cn.kindyear.esmc.qqauth.network.BackendClient;
import cn.kindyear.esmc.qqauth.protocol.Protocol;
import cn.kindyear.esmc.qqauth.protocol.Protocol.CommandResult;
import cn.kindyear.esmc.qqauth.protocol.Protocol.PlayerKick;
import cn.kindyear.esmc.qqauth.protocol.Protocol.RewardGive;
import cn.kindyear.esmc.qqauth.protocol.Protocol.VerificationCancel;
import cn.kindyear.esmc.qqauth.protocol.Protocol.VerificationCreate;
import cn.kindyear.esmc.qqauth.protocol.Protocol.VerificationExpired;
import cn.kindyear.esmc.qqauth.protocol.Protocol.WhitelistChange;
import cn.kindyear.esmc.qqauth.service.ChallengeService;
import cn.kindyear.esmc.qqauth.service.VerificationDisplayService;
import cn.kindyear.esmc.qqauth.service.VerificationSessionService;
import cn.kindyear.esmc.qqauth.service.WhitelistService;
import cn.kindyear.esmc.qqauth.service.RewardService;
import java.time.Instant;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

public final class ESMCQQAuthPlugin extends JavaPlugin {
    private PluginSettings settings;
    private ChallengeService challenges;
    private VerificationSessionService sessions;
    private VerificationDisplayService display;
    private WhitelistService whitelist;
    private BackendClient backend;
    private RewardService rewards;
    private BukkitTask expiryTask;
    private BukkitTask titleTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            settings = PluginSettings.from(getConfig());
        } catch (RuntimeException error) {
            getLogger().severe("配置无效: " + error.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        challenges = new ChallengeService(settings.maxAttempts());
        sessions = new VerificationSessionService();
        display = new VerificationDisplayService(getServer(), sessions, settings);
        whitelist = new WhitelistService(getServer(), sessions, display);
        backend = new BackendClient(
            settings, getPluginMeta().getVersion(), this::handleBackendMessage, getLogger()
        );
        rewards = new RewardService(this, settings.serverId(), result -> backend.send("command.result", result));

        getServer().getPluginManager().registerEvents(
            new WhitelistVerifyListener(challenges, sessions), this
        );
        getServer().getPluginManager().registerEvents(
            new RestrictedPlayerListener(sessions, settings, display), this
        );
        getServer().getPluginManager().registerEvents(rewards, this);
        var verify = getCommand("verify");
        if (verify == null) throw new IllegalStateException("plugin.yml 缺少 verify 命令");
        verify.setExecutor(new VerifyCommand(challenges, sessions, backend, settings, display));

        expiryTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            this, this::expireChallenges, 20L, 20L
        );
        titleTask = Bukkit.getScheduler().runTaskTimer(
            this, display::refreshPendingTitles,
            1L, settings.titleRefreshTicks()
        );
        backend.start();
        getLogger().info("ESMCQQAuth 已启用；等待 Backend 下发验证挑战");
    }

    @Override
    public void onDisable() {
        if (expiryTask != null) expiryTask.cancel();
        if (titleTask != null) titleTask.cancel();
        if (backend != null) backend.stop();
        if (challenges != null) challenges.clear();
        if (display != null) display.clearAll();
        if (sessions != null) sessions.clearAll();
    }

    private void handleBackendMessage(Protocol.Envelope envelope) {
        switch (envelope.type()) {
            case "verification.create" -> {
                var message = Protocol.payload(envelope, VerificationCreate.class);
                requireServer(message.serverId());
                challenges.put(message);
            }
            case "verification.cancel" -> {
                var message = Protocol.payload(envelope, VerificationCancel.class);
                requireServer(message.serverId());
                var removed = challenges.remove(message.verificationId());
                if (removed != null) {
                    sessions.clear(removed.playerUuid());
                    Bukkit.getScheduler().runTask(this, () -> display.clear(removed.playerUuid()));
                }
            }
            case "whitelist.add" -> runWhitelistOperation(
                "whitelist.add", Protocol.payload(envelope, WhitelistChange.class), true
            );
            case "whitelist.remove" -> runWhitelistOperation(
                "whitelist.remove", Protocol.payload(envelope, WhitelistChange.class), false
            );
            case "player.kick" -> runKick(Protocol.payload(envelope, PlayerKick.class));
            case "reward.give" -> runReward(Protocol.payload(envelope, RewardGive.class));
            case "ping" -> backend.send("pong", java.util.Map.of("serverId", settings.serverId()));
            default -> throw new IllegalArgumentException("Backend 不得发送消息类型: " + envelope.type());
        }
    }

    private void runReward(RewardGive message) {
        requireServer(message.serverId());
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                rewards.accept(message);
            } catch (Exception error) {
                sendFailure(message.requestId(), "reward.give", error);
            }
        });
    }

    private void runWhitelistOperation(String operation, WhitelistChange message, boolean add) {
        requireServer(message.serverId());
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                var uuid = UUID.fromString(message.playerUuid());
                if (add) {
                    whitelist.add(uuid);
                    var challenge = challenges.find(uuid);
                    if (challenge != null) challenges.remove(challenge.verificationId());
                } else {
                    whitelist.remove(uuid, message.reason());
                }
                backend.send("command.result", new CommandResult(
                    settings.serverId(), message.requestId(), operation, true, null
                ));
            } catch (Exception error) {
                sendFailure(message.requestId(), operation, error);
            }
        });
    }

    private void runKick(PlayerKick message) {
        requireServer(message.serverId());
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                whitelist.kick(UUID.fromString(message.playerUuid()), message.reason());
                backend.send("command.result", new CommandResult(
                    settings.serverId(), message.requestId(), "player.kick", true, null
                ));
            } catch (Exception error) {
                sendFailure(message.requestId(), "player.kick", error);
            }
        });
    }

    private void sendFailure(String requestId, String operation, Exception error) {
        getLogger().warning(operation + " 执行失败: " + error.getMessage());
        backend.send("command.result", new CommandResult(
            settings.serverId(), requestId, operation, false,
            error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage())
        ));
    }

    private void expireChallenges() {
        for (var challenge : challenges.removeExpired()) {
            sessions.clear(challenge.playerUuid());
            Bukkit.getScheduler().runTask(this, () -> {
                display.clear(challenge.playerUuid());
                whitelist.kick(challenge.playerUuid(), "QQ 白名单认证已过期，请回到 QQ 重新发起绑定。");
            });
            backend.send("verification.expired", new VerificationExpired(
                settings.serverId(), challenge.verificationId(),
                challenge.playerUuid().toString(), Instant.now().toString(), "expired"
            ));
        }
    }

    private void requireServer(String serverId) {
        if (!settings.serverId().equals(serverId)) {
            throw new IllegalArgumentException("serverId 不匹配");
        }
    }
}
