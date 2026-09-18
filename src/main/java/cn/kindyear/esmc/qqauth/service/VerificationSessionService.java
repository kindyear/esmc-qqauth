package cn.kindyear.esmc.qqauth.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VerificationSessionService {
    public enum State { VERIFYING, VERIFIED_PENDING_WHITELIST }
    public record Session(String verificationId, State state) {}

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public void begin(UUID playerUuid, String verificationId) {
        sessions.putIfAbsent(playerUuid, new Session(verificationId, State.VERIFYING));
    }

    public Session get(UUID playerUuid) {
        return sessions.get(playerUuid);
    }

    public boolean isRestricted(UUID playerUuid) {
        return sessions.containsKey(playerUuid);
    }

    public void markVerified(UUID playerUuid, String verificationId) {
        sessions.compute(playerUuid, (_uuid, current) -> {
            if (current == null || !current.verificationId().equals(verificationId)) {
                throw new IllegalStateException("验证会话已失效");
            }
            return new Session(verificationId, State.VERIFIED_PENDING_WHITELIST);
        });
    }

    public void clear(UUID playerUuid) {
        sessions.remove(playerUuid);
    }

    public void clearAll() {
        sessions.clear();
    }
}
