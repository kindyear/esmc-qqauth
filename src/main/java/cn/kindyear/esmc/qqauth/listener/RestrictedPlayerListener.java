package cn.kindyear.esmc.qqauth.listener;

import cn.kindyear.esmc.qqauth.config.PluginSettings;
import cn.kindyear.esmc.qqauth.service.VerificationDisplayService;
import cn.kindyear.esmc.qqauth.service.VerificationSessionService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class RestrictedPlayerListener implements Listener {
    private final VerificationSessionService sessions;
    private final PluginSettings settings;
    private final VerificationDisplayService display;

    public RestrictedPlayerListener(
        VerificationSessionService sessions,
        PluginSettings settings,
        VerificationDisplayService display
    ) {
        this.sessions = sessions;
        this.settings = settings;
        this.display = display;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (!restricted(player)) return;
        if (player.isWhitelisted()) {
            sessions.clear(player.getUniqueId());
            return;
        }
        display.showPending(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!restricted(event.getPlayer()) || !event.hasChangedPosition()) return;
        var from = event.getFrom();
        var requested = event.getTo();
        var constrained = from.clone();
        constrained.setYaw(requested.getYaw());
        constrained.setPitch(requested.getPitch());
        // A hard teleport back on every Y change can leave a player suspended and
        // trigger the vanilla "flying is not enabled" kick. Preserve gravity while
        // preventing horizontal movement and upward jumps during verification.
        if (requested.getY() < from.getY()) constrained.setY(requested.getY());
        event.setTo(constrained);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (restricted(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!restricted(event.getPlayer())) return;
        var command = event.getMessage().trim().toLowerCase(java.util.Locale.ROOT);
        if (command.equals("/verify") || command.startsWith("/verify ")) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text(settings.restrictedMessage()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (restricted(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (restricted(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && restricted(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (restricted(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && restricted(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (restricted(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (restricted(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && restricted(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByPlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && restricted(player)) event.setCancelled(true);
    }

    private boolean restricted(Player player) {
        return sessions.isRestricted(player.getUniqueId());
    }
}
