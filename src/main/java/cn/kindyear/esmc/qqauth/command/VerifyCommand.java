package cn.kindyear.esmc.qqauth.command;

import cn.kindyear.esmc.qqauth.config.PluginSettings;
import cn.kindyear.esmc.qqauth.network.BackendClient;
import cn.kindyear.esmc.qqauth.protocol.Protocol.VerificationSuccess;
import cn.kindyear.esmc.qqauth.protocol.Protocol.VerificationExpired;
import cn.kindyear.esmc.qqauth.service.ChallengeService;
import cn.kindyear.esmc.qqauth.service.VerificationDisplayService;
import cn.kindyear.esmc.qqauth.service.VerificationSessionService;
import java.time.Instant;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class VerifyCommand implements CommandExecutor {
    private final ChallengeService challenges;
    private final VerificationSessionService sessions;
    private final BackendClient backend;
    private final PluginSettings settings;
    private final VerificationDisplayService display;

    public VerifyCommand(
        ChallengeService challenges,
        VerificationSessionService sessions,
        BackendClient backend,
        PluginSettings settings,
        VerificationDisplayService display
    ) {
        this.challenges = challenges;
        this.sessions = sessions;
        this.backend = backend;
        this.settings = settings;
        this.display = display;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该命令只能由玩家执行。");
            return true;
        }
        if (args.length != 1) return false;
        var challenge = challenges.find(player.getUniqueId());
        var session = sessions.get(player.getUniqueId());
        if (challenge == null || session == null) {
            player.sendMessage(Component.text(settings.invalidCodeMessage()));
            return true;
        }
        if (session.state() == VerificationSessionService.State.VERIFIED_PENDING_WHITELIST) {
            player.sendMessage(Component.text(settings.successMessage()));
            return true;
        }
        var result = challenges.verify(player.getUniqueId(), args[0]);
        if (result == ChallengeService.VerifyResult.ATTEMPTS_EXHAUSTED) {
            sessions.clear(player.getUniqueId());
            display.clear(player.getUniqueId());
            backend.send("verification.expired", new VerificationExpired(
                settings.serverId(), challenge.verificationId(), player.getUniqueId().toString(),
                Instant.now().toString(), "attempts_exhausted"
            ));
            player.sendMessage(Component.text(settings.attemptsExhaustedMessage()));
            player.kick(Component.text(settings.attemptsExhaustedMessage()));
            return true;
        }
        if (result != ChallengeService.VerifyResult.SUCCESS) {
            player.sendMessage(Component.text(settings.invalidCodeMessage().replace(
                "{remaining}", String.valueOf(challenges.attemptsRemaining(player.getUniqueId()))
            )));
            return true;
        }
        sessions.markVerified(player.getUniqueId(), challenge.verificationId());
        backend.send("verification.success", new VerificationSuccess(
            settings.serverId(), challenge.verificationId(), player.getUniqueId().toString(),
            player.getName(), Instant.now().toString()
        ));
        player.sendMessage(Component.text(settings.successMessage()));
        return true;
    }
}
