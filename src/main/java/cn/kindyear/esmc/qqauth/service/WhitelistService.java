package cn.kindyear.esmc.qqauth.service;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;

public final class WhitelistService {
    private final Server server;
    private final VerificationSessionService sessions;
    private final VerificationDisplayService display;

    public WhitelistService(
        Server server,
        VerificationSessionService sessions,
        VerificationDisplayService display
    ) {
        this.server = server;
        this.sessions = sessions;
        this.display = display;
    }

    public void add(UUID playerUuid) {
        server.getOfflinePlayer(playerUuid).setWhitelisted(true);
        sessions.clear(playerUuid);
        var player = server.getPlayer(playerUuid);
        if (player != null) {
            display.showBound(player);
            player.sendMessage(Component.text("白名单已生效，认证限制已解除。"));
        }
    }

    public void remove(UUID playerUuid, String reason) {
        server.getOfflinePlayer(playerUuid).setWhitelisted(false);
        sessions.clear(playerUuid);
        display.clear(playerUuid);
        var player = server.getPlayer(playerUuid);
        if (player != null) player.kick(Component.text(nonBlank(reason, "你的白名单已被移除。")));
    }

    public void kick(UUID playerUuid, String reason) {
        var player = server.getPlayer(playerUuid);
        if (player != null) player.kick(Component.text(nonBlank(reason, "你已被移出服务器。")));
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
