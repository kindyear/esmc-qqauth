package cn.kindyear.esmc.qqauth.listener;

import cn.kindyear.esmc.qqauth.service.ChallengeService;
import cn.kindyear.esmc.qqauth.service.VerificationSessionService;
import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class WhitelistVerifyListener implements Listener {
    private final ChallengeService challenges;
    private final VerificationSessionService sessions;

    public WhitelistVerifyListener(
        ChallengeService challenges, VerificationSessionService sessions
    ) {
        this.challenges = challenges;
        this.sessions = sessions;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWhitelistVerify(ProfileWhitelistVerifyEvent event) {
        if (!event.isWhitelistEnabled() || event.isWhitelisted()) return;
        var uuid = event.getPlayerProfile().getId();
        if (uuid == null) return;
        var challenge = challenges.find(uuid);
        if (challenge == null) return;
        sessions.begin(uuid, challenge.verificationId());
        event.setWhitelisted(true);
        event.kickMessage(Component.text("QQ 白名单认证会话已失效，请回到 QQ 重试。"));
    }
}
