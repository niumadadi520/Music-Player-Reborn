package com.mengsama.mod.mengsamanetmusic.karaoke.voice;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class KaraokeVoiceIsolationTest {
    @Test void commonAndClientFacadesHaveNoOptionalVoiceChatReferences() throws Exception {
        for (String simple : new String[]{"KaraokeVoiceBridge", "KaraokeVoiceBridge$Backend", "KaraokeVoiceBridge$Target",
                "KaraokeVoiceClient", "KaraokeVoiceClient$CaptureBackend", "VoiceAudioMath", "BoundedAudioQueue", "MicrophoneStartGrant"}) {
            byte[] bytes = bytes(simple);
            new ClassReader(bytes);  
            assertFalse(new String(bytes, StandardCharsets.ISO_8859_1).contains("de/maxhenkel/voicechat"), simple);
        }
    }

    @Test void integrationUsesPublicApiAndDoesNotModifyOrdinaryVoiceChat() throws Exception {
        for (String simple : new String[]{"KaraokeVoicePlugin", "KaraokeVoicePlugin$ClientEvents",
                "VoicechatServerRouter", "VoicechatClientCapture"}) {
            String constantPool = new String(bytes(simple), StandardCharsets.ISO_8859_1);
            assertFalse(constantPool.contains("de/maxhenkel/voicechat/voice/"), simple);
            assertFalse(constantPool.contains("de/maxhenkel/voicechat/plugins/"), simple);
            assertFalse(constantPool.contains("setMuted"), simple);
            assertFalse(constantPool.contains("setDisabled"), simple);
            assertFalse(constantPool.contains("setMicrophoneLocked"), simple);
            assertFalse(constantPool.contains("sendAudioPacket"), simple);
        }
    }

    @Test void captureStartsOnlyWithNonceAndStopsBeforeSessionReplacement() {
        FakeCapture capture = new FakeCapture();
        KaraokeVoiceClient.install(capture);
        try {
            assertEquals(0, capture.starts);
            assertNull(KaraokeVoiceClient.session());
            UUID one = UUID.randomUUID(), two = UUID.randomUUID();
            KaraokeVoiceClient.authorizeStart();
            KaraokeVoiceClient.setSession(one);
            assertEquals(1, capture.starts);
            assertEquals(one, capture.nonce);
            KaraokeVoiceClient.setSession(one);
            assertEquals(1, capture.starts, "Repeated acknowledgement must not open another microphone");
            int stopsBefore = capture.stops;
            KaraokeVoiceClient.authorizeStart();
            KaraokeVoiceClient.setSession(two);
            assertEquals(stopsBefore + 1, capture.stops);
            assertEquals(two, capture.nonce);
            KaraokeVoiceClient.reset();
            assertNull(KaraokeVoiceClient.session());
            assertNull(capture.nonce);
        } finally { KaraokeVoiceClient.install(null); }
    }

    @Test void oldServerStopPreservesNewClickButLocalResetRevokesIt() {
        FakeCapture capture = new FakeCapture();
        KaraokeVoiceClient.install(capture);
        try {
            UUID one = UUID.randomUUID(), two = UUID.randomUUID();
            KaraokeVoiceClient.authorizeStart(); KaraokeVoiceClient.setSession(one);
            KaraokeVoiceClient.authorizeStart();
            KaraokeVoiceClient.stopFromServer();
            assertNull(KaraokeVoiceClient.session());
            assertNull(capture.nonce);
            KaraokeVoiceClient.setSession(two);
            assertEquals(two, capture.nonce);
            assertEquals(2, capture.starts);
            KaraokeVoiceClient.authorizeStart();
            KaraokeVoiceClient.reset();
            assertNull(KaraokeVoiceClient.session());
            assertFalse(KaraokeVoiceClient.consumeStartGrant(), "A local stop/death/disconnect must revoke even a pending new click");
        } finally { KaraokeVoiceClient.install(null); }
    }

    private static byte[] bytes(String simple) throws Exception {
        try (InputStream input = KaraokeVoiceIsolationTest.class.getResourceAsStream(simple + ".class")) {
            assertNotNull(input, simple);
            return input.readAllBytes();
        }
    }

    private static final class FakeCapture implements KaraokeVoiceClient.CaptureBackend {
        int starts, stops;
        UUID nonce;
        public void start(UUID nonce) { starts++; this.nonce = nonce; }
        public void stop() { stops++; nonce = null; }
        public void tick() {}
        public String status() { return "test"; }
    }
}
