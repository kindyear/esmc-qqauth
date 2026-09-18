package cn.kindyear.esmc.qqauth.service;

import cn.kindyear.esmc.qqauth.protocol.Protocol.CommandResult;
import cn.kindyear.esmc.qqauth.protocol.Protocol.RewardGive;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

public final class RewardService implements Listener {
    private static final int MAX_PROCESSED_IDS = 10_000;

    private final JavaPlugin plugin;
    private final String serverId;
    private final Consumer<CommandResult> resultSender;
    private final Map<UUID, List<RewardGive>> pending = new ConcurrentHashMap<>();
    private final Set<String> pendingIds = ConcurrentHashMap.newKeySet();
    private final LinkedHashSet<String> processed = new LinkedHashSet<>();
    private final File stateFile;

    public RewardService(JavaPlugin plugin, String serverId, Consumer<CommandResult> resultSender) {
        this.plugin = plugin;
        this.serverId = serverId;
        this.resultSender = resultSender;
        this.stateFile = new File(plugin.getDataFolder(), "processed-rewards.yml");
        load();
    }

    public void accept(RewardGive reward) {
        validate(reward);
        if (processed.contains(reward.requestId())) {
            success(reward);
            return;
        }
        if (!pendingIds.add(reward.requestId())) return;
        var uuid = UUID.fromString(reward.playerUuid());
        var player = plugin.getServer().getPlayer(uuid);
        if (player != null && player.isOnline()) {
            deliver(player, reward);
            return;
        }
        pending.computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(reward);
        plugin.getLogger().info("签到奖励等待玩家上线: " + (reward.playerName() == null ? uuid : reward.playerName()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var rewards = pending.remove(event.getPlayer().getUniqueId());
        if (rewards == null || rewards.isEmpty()) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (var reward : rewards) deliver(event.getPlayer(), reward);
        });
    }

    private void deliver(Player player, RewardGive reward) {
        try {
            var material = material(reward.item());
            int remaining = reward.amount();
            while (remaining > 0) {
                int amount = Math.min(remaining, material.getMaxStackSize());
                var leftovers = player.getInventory().addItem(new ItemStack(material, amount));
                for (var item : leftovers.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
                remaining -= amount;
            }
            pendingIds.remove(reward.requestId());
            remember(reward.requestId());
            success(reward);
        } catch (Exception error) {
            pendingIds.remove(reward.requestId());
            resultSender.accept(new CommandResult(
                serverId, reward.requestId(), "reward.give", false,
                error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage())
            ));
        }
    }

    private void success(RewardGive reward) {
        resultSender.accept(new CommandResult(serverId, reward.requestId(), "reward.give", true, null));
    }

    private void validate(RewardGive reward) {
        if (!serverId.equals(reward.serverId())) throw new IllegalArgumentException("serverId 不匹配");
        if (reward.requestId() == null || reward.requestId().isBlank()) throw new IllegalArgumentException("requestId 不能为空");
        UUID.fromString(reward.playerUuid());
        if (reward.amount() < 1 || reward.amount() > 2304) throw new IllegalArgumentException("奖励数量无效");
        material(reward.item());
    }

    private Material material(String item) {
        var material = Material.matchMaterial(item);
        if (material == null || material.isAir() || !material.isItem()) {
            throw new IllegalArgumentException("未知或不可发放的物品: " + item);
        }
        return material;
    }

    private void load() {
        var config = YamlConfiguration.loadConfiguration(stateFile);
        processed.addAll(config.getStringList("processed"));
    }

    private void remember(String requestId) throws IOException {
        processed.add(requestId);
        while (processed.size() > MAX_PROCESSED_IDS) {
            var iterator = processed.iterator();
            iterator.next();
            iterator.remove();
        }
        var config = new YamlConfiguration();
        config.set("processed", new ArrayList<>(processed));
        config.save(stateFile);
    }
}
