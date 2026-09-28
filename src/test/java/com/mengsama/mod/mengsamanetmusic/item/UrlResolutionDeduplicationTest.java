package com.mengsama.mod.mengsamanetmusic.item;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UrlResolutionDeduplicationTest {
    @Test void blockEntitiesOnlyDelegateToMusicPlayerItem() throws Exception {
        Path root = Path.of("src/main/java/com/mengsama/mod/mengsamanetmusic/block");
        for (String file : new String[]{"MusicPlayerBlockEntity.java", "PortableMusicPlayerBlockEntity.java"}) {
            String source = Files.readString(root.resolve(file));
            assertFalse(source.contains("private static CompletableFuture<SongInfo> resolveUrlAsync"));
            assertFalse(source.contains("extractSongId("));
            assertFalse(source.contains("resolveRedirectUrl("));
            assertEquals(0, count(source, "MusicPlayerItem.resolveUrlAsync"));
            assertTrue(source.contains("extends MusicDeviceEntity"));
        }
        assertEquals(1, count(Files.readString(root.resolve("MusicDeviceEntity.java")), "MusicPlayerItem.resolveUrlAsync"));
    }

    private static int count(String source, String token) {
        int result = 0;
        for (int index = source.indexOf(token); index >= 0; index = source.indexOf(token, index + token.length())) result++;
        return result;
    }
}
