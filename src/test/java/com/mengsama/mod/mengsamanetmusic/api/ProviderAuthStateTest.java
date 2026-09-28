package com.mengsama.mod.mengsamanetmusic.api;

import com.mengsama.mod.mengsamanetmusic.gui.ProviderAuthControls;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ProviderAuthStateTest {
    @Test
    void allThreeContextsUseOneSharedControllerContract() {
        assertArrayEquals(new ProviderAuthControls.Context[]{
                ProviderAuthControls.Context.HELD_PLAYER,
                ProviderAuthControls.Context.PORTABLE_BLOCK,
                ProviderAuthControls.Context.JUKEBOX
        }, ProviderAuthControls.Context.values());
    }

    @Test
    void qqLogoutPublishesRevisionToEveryContext() {
        QqCredentialManager.save(new QqCredential("123", "secret", 3600,
                Instant.now().getEpochSecond(), "", ""));
        long loggedInRevision = QqCredentialManager.revision();
        assertTrue(QqCredentialManager.hasValidCredential());
        QqCredentialManager.clear();
        assertFalse(QqCredentialManager.hasValidCredential());
        assertTrue(QqCredentialManager.revision() > loggedInRevision);
    }

    @Test
    void appleRequiresStructurallyValidUnexpiredDeveloperJwt() {
        assertFalse(AppleMusicKitAuthorization.isDeveloperTokenUsable(""));
        assertFalse(AppleMusicKitAuthorization.isDeveloperTokenUsable("not-a-jwt"));
        assertTrue(AppleMusicKitAuthorization.isDeveloperTokenUsable(jwt(Instant.now().plusSeconds(300).getEpochSecond())));
        assertFalse(AppleMusicKitAuthorization.isDeveloperTokenUsable(jwt(Instant.now().minusSeconds(1).getEpochSecond())));
    }

    private static String jwt(long exp) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(("{\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8)) + ".signature";
    }
}
