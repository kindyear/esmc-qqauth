package cn.kindyear.esmc.qqauth.service;

import cn.kindyear.esmc.qqauth.protocol.Protocol.VerificationCreate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChallengeService {
    public record Challenge(
        String verificationId, UUID playerUuid, String playerName,
        String code, Instant expiresAt, int failedAttempts
    ) {}

    public enum VerifyResult { SUCCESS, NOT_FOUND, EXPIRED, INVALID_CODE, ATTEMPTS_EXHAUSTED }

    private final Map<UUID, Challenge> byPlayer = new ConcurrentHashMap<>();
    private final Map<String, UUID> byId = new ConcurrentHashMap<>();
    private final int maxAttempts;

    public ChallengeService() {
        this(3);
    }

    public ChallengeService(int maxAttempts) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts 必须大于 0");
        this.maxAttempts = maxAttempts;
    }

    public synchronized void put(VerificationCreate message) {
        require(message.serverId(), "serverId");
        require(message.verificationId(), "verificationId");
        require(message.playerUuid(), "playerUuid");
        require(message.code(), "code");
        var uuid = UUID.fromString(message.playerUuid());
        var expiresAt = Instant.parse(message.expiresAt());
        if (!expiresAt.isAfter(Instant.now())) throw new IllegalArgumentException("challenge 已过期");
        if (!message.code().matches("^[0-9]{4,12}$")) {
            throw new IllegalArgumentException("challenge code 格式无效");
        }
        var challenge = new Challenge(
            message.verificationId(), uuid, message.playerName(), message.code(), expiresAt, 0
        );
        var previous = byPlayer.put(uuid, challenge);
        if (previous != null) byId.remove(previous.verificationId());
        var previousPlayer = byId.put(challenge.verificationId(), uuid);
        if (previousPlayer != null && !previousPlayer.equals(uuid)) byPlayer.remove(previousPlayer);
    }

    public Challenge find(UUID playerUuid) {
        var challenge = byPlayer.get(playerUuid);
        if (challenge != null && !challenge.expiresAt().isAfter(Instant.now())) {
            remove(challenge.verificationId());
            return null;
        }
        return challenge;
    }

    public synchronized VerifyResult verify(UUID playerUuid, String code) {
        var challenge = byPlayer.get(playerUuid);
        if (challenge == null) return VerifyResult.NOT_FOUND;
        if (!challenge.expiresAt().isAfter(Instant.now())) {
            remove(challenge.verificationId());
            return VerifyResult.EXPIRED;
        }
        boolean matches = MessageDigest.isEqual(
            challenge.code().getBytes(StandardCharsets.UTF_8),
            code.getBytes(StandardCharsets.UTF_8)
        );
        if (matches) return VerifyResult.SUCCESS;
        int failedAttempts = challenge.failedAttempts() + 1;
        if (failedAttempts >= maxAttempts) {
            byPlayer.remove(playerUuid, challenge);
            byId.remove(challenge.verificationId(), playerUuid);
            return VerifyResult.ATTEMPTS_EXHAUSTED;
        }
        byPlayer.put(playerUuid, new Challenge(
            challenge.verificationId(), challenge.playerUuid(), challenge.playerName(),
            challenge.code(), challenge.expiresAt(), failedAttempts
        ));
        return VerifyResult.INVALID_CODE;
    }

    public int attemptsRemaining(UUID playerUuid) {
        var challenge = find(playerUuid);
        return challenge == null ? 0 : Math.max(0, maxAttempts - challenge.failedAttempts());
    }

    public synchronized Challenge remove(String verificationId) {
        var uuid = byId.remove(verificationId);
        if (uuid == null) return null;
        var challenge = byPlayer.get(uuid);
        if (challenge != null && challenge.verificationId().equals(verificationId)) {
            byPlayer.remove(uuid, challenge);
            return challenge;
        }
        return null;
    }

    public List<Challenge> removeExpired() {
        var expired = new ArrayList<Challenge>();
        var now = Instant.now();
        for (var challenge : byPlayer.values()) {
            if (!challenge.expiresAt().isAfter(now) && remove(challenge.verificationId()) != null) {
                expired.add(challenge);
            }
        }
        return expired;
    }

    public void clear() {
        byPlayer.clear();
        byId.clear();
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
    }
}
