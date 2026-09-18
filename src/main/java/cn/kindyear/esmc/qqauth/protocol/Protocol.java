package cn.kindyear.esmc.qqauth.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.UUID;

public final class Protocol {
    public static final int VERSION = 1;
    private static final Gson GSON = new Gson();

    private Protocol() {}

    public record Envelope(int v, String id, String type, String timestamp, JsonObject payload) {}
    public record ClientReady(String serverId, String pluginVersion) {}
    public record VerificationCreate(
        String serverId, String verificationId, String playerUuid,
        String playerName, String code, String expiresAt, String qqUserId, String qqDisplayName
    ) {}
    public record VerificationCancel(String serverId, String verificationId) {}
    public record VerificationSuccess(
        String serverId, String verificationId, String playerUuid,
        String playerName, String verifiedAt
    ) {}
    public record VerificationExpired(
        String serverId, String verificationId, String playerUuid, String expiredAt, String reason
    ) {}
    public record WhitelistChange(
        String serverId, String requestId, String playerUuid, String playerName, String reason
    ) {}
    public record PlayerKick(
        String serverId, String requestId, String playerUuid,
        String playerName, String reason
    ) {}
    public record RewardGive(
        String serverId, String requestId, String playerUuid, String playerName,
        String item, int amount, String reason
    ) {}
    public record CommandResult(
        String serverId, String requestId, String operation, boolean success, String error
    ) {}

    public static Envelope parse(String json) {
        var envelope = GSON.fromJson(json, Envelope.class);
        if (envelope == null || envelope.v() != VERSION || envelope.id() == null
            || envelope.type() == null || envelope.timestamp() == null || envelope.payload() == null) {
            throw new IllegalArgumentException("无效的协议消息");
        }
        return envelope;
    }

    public static <T> T payload(Envelope envelope, Class<T> type) {
        return GSON.fromJson(envelope.payload(), type);
    }

    public static String message(String type, Object payload) {
        var jsonPayload = GSON.toJsonTree(payload).getAsJsonObject();
        return GSON.toJson(new Envelope(
            VERSION, UUID.randomUUID().toString(), type, Instant.now().toString(), jsonPayload
        ));
    }
}
