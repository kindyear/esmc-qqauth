package cn.kindyear.esmc.qqauth.service;

import cn.kindyear.esmc.qqauth.config.PluginSettings;
import java.time.Duration;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class VerificationDisplayService {
    private final Server server;
    private final VerificationSessionService sessions;
    private final PluginSettings settings;

    public VerificationDisplayService(
        Server server,
        VerificationSessionService sessions,
        PluginSettings settings
    ) {
        this.server = server;
        this.sessions = sessions;
        this.settings = settings;
    }

    public void refreshPendingTitles() {
        for (var player : server.getOnlinePlayers()) {
            if (sessions.isRestricted(player.getUniqueId())) showPending(player);
        }
    }

    public void showPending(Player player) {
        long stayMillis = (long) (settings.titleRefreshTicks() + 20) * 50L;
        player.showTitle(Title.title(
            Component.text(settings.pendingTitle(), NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD),
            Component.text(settings.pendingSubtitle(), NamedTextColor.YELLOW),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(stayMillis), Duration.ofMillis(250))
        ));
    }

    public void showBound(Player player) {
        player.clearTitle();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        player.showTitle(Title.title(
            Component.text(settings.boundTitle(), NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD),
            Component.text(settings.boundSubtitle(), NamedTextColor.GREEN),
            Title.Times.times(
                Duration.ofMillis(250),
                Duration.ofMillis((long) settings.boundTitleStayTicks() * 50L),
                Duration.ofMillis(500)
            )
        ));
    }

    public void clear(UUID playerUuid) {
        var player = server.getPlayer(playerUuid);
        if (player != null) player.clearTitle();
    }

    public void clearAll() {
        for (var player : server.getOnlinePlayers()) {
            if (sessions.isRestricted(player.getUniqueId())) player.clearTitle();
        }
    }
}
