package com.mengsama.mod.mengsamanetmusic.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppleMusicApiTest {
    @Test void upgradesOnlyTrustedAppleArtworkPath() {
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Music/1/600x600bb.jpg",
                AppleMusicApi.highResolutionArtwork("https://is1-ssl.mzstatic.com/image/thumb/Music/1/100x100bb.jpg"));
        assertEquals("https://evil.example/100x100bb.jpg",
                AppleMusicApi.highResolutionArtwork("https://evil.example/100x100bb.jpg"));
        assertEquals("https://is1-ssl.mzstatic.com/not-a-size.jpg",
                AppleMusicApi.highResolutionArtwork("https://is1-ssl.mzstatic.com/not-a-size.jpg"));
    }

    @Test void acceptsOnlyHttpsApplePreviewCdn() {
        assertTrue(AppleMusicApi.isSafePreviewUrl("https://audio-ssl.itunes.apple.com/apple-assets-us-std-000001/file.m4a"));
        assertTrue(AppleMusicApi.isSafePreviewUrl("https://audio-ssl.itunes.apple.com/file.m4a"));
        assertTrue(AppleMusicApi.isSafePreviewUrl("https://audio-ssl.mzstatic.com/file.m4a"));
        assertFalse(AppleMusicApi.isSafePreviewUrl("http://audio-ssl.mzstatic.com/file.m4a"));
    }
}
