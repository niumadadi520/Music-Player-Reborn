package com.mengsama.mod.mengsamanetmusic.api.qq;

import com.mengsama.mod.mengsamanetmusic.api.QqCredential;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class QqCredentialFileTest {
    @TempDir Path temporary;
    private QqCredential valid() { return new QqCredential("123", "key", 0, 0, "refresh", "token"); }

    @Test void readingLegacyCredentialDoesNotRewriteAndUpdatingPreservesUnknownFields() throws Exception {
        Path file = temporary.resolve("credential.json");
        String legacy = "{\"musicid\":\"123\",\"musickey\":\"old\",\"future\":{\"saved\":[1,2,3]}}";
        Files.writeString(file, legacy);
        var storage = new QqCredentialFile(file);
        assertEquals("old", storage.read().getMusicKey());
        assertEquals(legacy, Files.readString(file));
        storage.write(valid());
        assertEquals("key", storage.read().getMusicKey());
        assertEquals("[1,2,3]", QqFields.object(QqFields.parse(Files.readString(file)), "future").get("saved").toString());
        try (var entries = Files.list(temporary)) { assertEquals(1, entries.count()); }
    }

    @Test void invalidReplacementDoesNotEraseGoodCredential() throws Exception {
        Path file = temporary.resolve("credential.json");
        var storage = new QqCredentialFile(file);
        storage.write(valid()); byte[] before = Files.readAllBytes(file);
        assertThrows(IOException.class, () -> storage.write(new QqCredential()));
        assertThrows(IOException.class, () -> storage.write(null));
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test void unreadableContentIsPreservedAndExplicitLogoutWritesEmptyObject() throws Exception {
        Path file = temporary.resolve("credential.json");
        Files.writeString(file, "broken JSON");
        var storage = new QqCredentialFile(file);
        assertThrows(IOException.class, storage::read);
        assertEquals("broken JSON", Files.readString(file));
        storage.clear();
        assertEquals("{}", Files.readString(file));
        assertFalse(storage.read().isValid());
    }

    @Test void headerInjectionAndSecretDiagnosticsAreRejected() {
        for (String key : new String[]{"bad;other=cookie", "bad\r\nx", "bad\nx\ny", "bad\tx"}) {
            QqCredential credential = new QqCredential("123", key, 0, 0, "", "");
            assertFalse(credential.isValid());
            assertEquals("", credential.toCookieString());
        }
        assertFalse(valid().toString().contains("refresh"));
        assertFalse(valid().toString().contains("123"));
    }

    @Test void malformedFieldTypesCannotProduceValidCredentials() {
        assertFalse(QqCredentialFile.decode(QqFields.properties("musicid", QqFields.properties("x", 1)), 0).isValid());
        var decoded = QqCredentialFile.decode(QqFields.properties("musicId", "123", "musicKey", "key", "refreshKey", "refresh"), 100);
        assertEquals("123", decoded.getMusicId());
        assertEquals(100, decoded.getMusicKeyCreateTime());
    }
}
