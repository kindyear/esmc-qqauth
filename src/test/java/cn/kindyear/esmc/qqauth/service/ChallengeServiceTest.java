package cn.kindyear.esmc.qqauth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import cn.kindyear.esmc.qqauth.protocol.Protocol.VerificationCreate;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChallengeServiceTest {
    @Test
    void verifiesOnlyTheMatchingPlayersCode() {
        var service = new ChallengeService();
        var player = UUID.randomUUID();
        service.put(new VerificationCreate(
            "server", "verification", player.toString(), "Tester", "482731",
            Instant.now().plusSeconds(60).toString(), "qq-user", "Tester QQ"
        ));

        assertEquals(ChallengeService.VerifyResult.INVALID_CODE, service.verify(player, "111111"));
        assertEquals(2, service.attemptsRemaining(player));
        assertEquals(ChallengeService.VerifyResult.SUCCESS, service.verify(player, "482731"));
        service.remove("verification");
        assertNull(service.find(player));
    }

    @Test
    void invalidatesChallengeAfterThirdWrongCode() {
        var service = new ChallengeService(3);
        var player = UUID.randomUUID();
        service.put(new VerificationCreate(
            "server", "verification", player.toString(), "Tester", "482731",
            Instant.now().plusSeconds(60).toString(), "qq-user", "Tester QQ"
        ));

        assertEquals(ChallengeService.VerifyResult.INVALID_CODE, service.verify(player, "111111"));
        assertEquals(ChallengeService.VerifyResult.INVALID_CODE, service.verify(player, "222222"));
        assertEquals(ChallengeService.VerifyResult.ATTEMPTS_EXHAUSTED, service.verify(player, "333333"));
        assertNull(service.find(player));
        assertEquals(ChallengeService.VerifyResult.NOT_FOUND, service.verify(player, "482731"));
    }
}
