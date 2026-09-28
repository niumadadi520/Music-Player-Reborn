package com.mengsama.mod.mengsamanetmusic.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientFavoriteStoreTest {
    private static final Gson GSON = new Gson();
    @TempDir Path temporary;

    @Test
    void openingAndReadingAnEmptyStoreDoesNotCreateAFileOrDirectory() {
        Path path = temporary.resolve("new-config/favorites.json");
        ClientFavoriteStore.Store store = new ClientFavoriteStore.Store(path);
        assertTrue(store.status().success());
        assertTrue(store.snapshot().isEmpty());
        assertFalse(store.contains(song(1)));
        assertFalse(Files.exists(path.getParent()));
    }

    @Test
    void togglePersistsAcrossReloadAndRemovesByStableIdentity() throws IOException {
        Path path = temporary.resolve("favorites.json");
        ClientFavoriteStore.Store store = new ClientFavoriteStore.Store(path);
        SongInfo song = song(12);
        assertEquals(new ClientFavoriteStore.Result(true, true, "已加入收藏"), store.toggle(song));
        ClientFavoriteStore.Store reloaded = new ClientFavoriteStore.Store(path);
        assertTrue(reloaded.contains(song));
        assertEquals("Song 12", reloaded.snapshot().get(0).songName);
        SongInfo differentMetadata = song(12);
        differentMetadata.songName = "Renamed";
        assertEquals(new ClientFavoriteStore.Result(true, false, "已取消收藏"), reloaded.toggle(differentMetadata));
        assertTrue(new ClientFavoriteStore.Store(path).snapshot().isEmpty());
        try (var files = Files.list(temporary)) {
            assertEquals(List.of("favorites.json"), files.map(file -> file.getFileName().toString()).toList());
        }
    }

    @Test
    void inputAndReturnedMetadataCannotMutateStoredFavorites() {
        ClientFavoriteStore.Store store = new ClientFavoriteStore.Store(temporary.resolve("favorites.json"));
        SongInfo input = song(23);
        input.source = "unknown";
        input.providerId = "";
        String originalSource = input.source;
        assertTrue(store.toggle(input).success());
        assertEquals(originalSource, input.source, "Identity normalization must not mutate caller metadata");
        assertEquals("", input.providerId);
        input.songName = "Changed by caller";
        input.artists.clear();
        List<SongInfo> first = store.snapshot();
        assertThrows(UnsupportedOperationException.class, first::clear);
        first.get(0).songName = "Changed by view";
        first.get(0).artists.clear();
        first.get(0).playbackHeaders.put("Cookie", "view-secret");
        SongInfo stored = store.snapshot().get(0);
        assertEquals("Song 23", stored.songName);
        assertEquals(List.of("Artist"), stored.artists);
        assertTrue(stored.playbackHeaders.isEmpty());
    }

    @Test
    void zeroDurationSearchMetadataIsAcceptedWithoutCredentialsOrMediaUrlPersistence() throws IOException {
        Path path = temporary.resolve("favorites.json");
        ClientFavoriteStore.Store store = new ClientFavoriteStore.Store(path);
        SongInfo search = song(34);
        search.songTime = 0;
        search.resolvedMediaUrl = "https://example.invalid/audio?token=runtime-secret";
        search.playbackHeaders.put("Cookie", "account-secret");
        assertTrue(store.toggle(search).success());
        assertEquals(0, store.snapshot().get(0).songTime);
        String json = Files.readString(path);
        assertFalse(json.contains("runtime-secret"));
        assertFalse(json.contains("account-secret"));
        assertFalse(json.contains("playbackHeaders"));
        assertFalse(json.contains("resolvedMediaUrl"));
        assertEquals("account-secret", search.playbackHeaders.get("Cookie"));
    }

    @Test
    void invalidNewFavoriteDoesNotModifyTheFileOrExistingEntries() throws IOException {
        Path path = temporary.resolve("favorites.json");
        ClientFavoriteStore.Store store = new ClientFavoriteStore.Store(path);
        assertTrue(store.toggle(song(1)).success());
        byte[] before = Files.readAllBytes(path);
        SongInfo unnamed = song(2);
        unnamed.songName = " ";
        SongInfo unidentified = new SongInfo("https://example.invalid/audio", "No identity", 1);
        for (SongInfo invalid : new SongInfo[] {null, unnamed, unidentified}) {
            ClientFavoriteStore.Result result = store.toggle(invalid);
            assertFalse(result.success());
            assertFalse(result.favorite());
            assertFalse(result.message().isBlank());
            assertArrayEquals(before, Files.readAllBytes(path));
            assertEquals(1, store.snapshot().size());
        }
    }

    @Test
    void malformedJsonIsPreservedAndEveryToggleFailsExplicitly() throws IOException {
        Path path = temporary.resolve("favorites.json");
        byte[] corrupted = "{\"version\":1,\"songs\":[{\"songName\":\"keep me\"}".getBytes(StandardCharsets.UTF_8);
        Files.write(path, corrupted);
        ClientFavoriteStore.Store store = new ClientFavoriteStore.Store(path);
        assertFalse(store.status().success());
        assertTrue(store.snapshot().isEmpty());
        for (int attempt = 0; attempt < 2; attempt++) {
            assertFalse(store.toggle(song(1)).success());
            assertArrayEquals(corrupted, Files.readAllBytes(path));
        }
    }

    @Test
    void unsupportedVersionInvalidEntriesAndInvalidUtf8CannotBeOverwritten() throws IOException {
        Path path = temporary.resolve("favorites.json");
        for (byte[] unsupported : List.of(
                "{\"version\":2,\"songs\":[]}".getBytes(StandardCharsets.UTF_8),
                "{\"version\":1,\"songs\":[null]}".getBytes(StandardCharsets.UTF_8),
                "{\"version\":1,\"songs\":{}}".getBytes(StandardCharsets.UTF_8),
                new byte[] {(byte)0xc3, (byte)0x28})) {
            Files.write(path, unsupported);
            ClientFavoriteStore.Store store = new ClientFavoriteStore.Store(path);
            assertFalse(store.status().success());
            assertFalse(store.toggle(song(1)).success());
            assertArrayEquals(unsupported, Files.readAllBytes(path));
        }
    }

    @Test
    void duplicateIdentitiesLoadOnceWhileUnknownExtensionFieldsSurviveLaterWrites() throws IOException {
        Path path = temporary.resolve("favorites.json");
        JsonObject document = document(song(1), song(1), song(2));
        document.addProperty("extension", "root-content");
        document.getAsJsonArray("songs").get(0).getAsJsonObject().addProperty("custom-note", "song-content");
        Files.writeString(path, GSON.toJson(document));
        byte[] original = Files.readAllBytes(path);
        ClientFavoriteStore.Store store = new ClientFavoriteStore.Store(path);
        assertEquals(2, store.snapshot().size());
        assertArrayEquals(original, Files.readAllBytes(path), "Loading must not silently normalize the file");
        assertTrue(store.toggle(song(3)).success());
        JsonObject saved = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals("root-content", saved.get("extension").getAsString());
        assertEquals(3, saved.getAsJsonArray("songs").size());
        assertEquals("song-content", saved.getAsJsonArray("songs").get(0).getAsJsonObject().get("custom-note").getAsString());
    }

    @Test
    void externalFileChangesAreNotLostWhenTogglingAStaleInMemoryStore() throws IOException {
        Path path = temporary.resolve("favorites.json");
        ClientFavoriteStore.Store store = new ClientFavoriteStore.Store(path);
        assertTrue(store.toggle(song(1)).success());
        String external = GSON.toJson(document(song(2)));
        Files.writeString(path, external);
        ClientFavoriteStore.Result result = store.toggle(song(1));
        assertFalse(result.success());
        assertTrue(result.favorite(), "Failed removal must still report the retained in-memory favorite");
        assertTrue(store.contains(song(1)));
        assertEquals(external, Files.readString(path));
    }

    @Test
    void writeFailureLeavesMemoryAndThePreviousFileIntactAndCanBeRetried() throws IOException {
        Path folder = temporary.resolve("config");
        Path path = folder.resolve("favorites.json");
        ClientFavoriteStore.Store store = new ClientFavoriteStore.Store(path);
        assertTrue(store.toggle(song(1)).success());
        byte[] original = Files.readAllBytes(path);
        Path preserved = temporary.resolve("preserved-config");
        Files.move(folder, preserved);
        Files.writeString(folder, "A regular file blocks the directory");
        assertFalse(store.toggle(song(2)).success());
        assertTrue(store.contains(song(1)));
        assertFalse(store.contains(song(2)));
        assertArrayEquals(original, Files.readAllBytes(preserved.resolve("favorites.json")));
        Files.delete(folder);
        Files.move(preserved, folder);
        assertTrue(store.toggle(song(2)).success());
        assertEquals(2, new ClientFavoriteStore.Store(path).snapshot().size());
    }

    @Test
    void fileAndSongCountLimitsRejectOversizedContentWithoutTruncatingIt() throws IOException {
        Path path = temporary.resolve("favorites.json");
        byte[] huge = new byte[ClientFavoriteStore.MAX_FILE_BYTES + 1];
        Files.write(path, huge);
        ClientFavoriteStore.Store oversized = new ClientFavoriteStore.Store(path);
        assertFalse(oversized.status().success());
        assertFalse(oversized.toggle(song(1)).success());
        assertEquals(huge.length, Files.size(path));

        JsonObject document = document();
        JsonArray songs = document.getAsJsonArray("songs");
        for (int i = 0; i <= ClientFavoriteStore.MAX_FAVORITES; i++) songs.add(GSON.toJsonTree(song(i + 1)));
        Files.writeString(path, GSON.toJson(document));
        byte[] tooMany = Files.readAllBytes(path);
        ClientFavoriteStore.Store overCount = new ClientFavoriteStore.Store(path);
        assertFalse(overCount.status().success());
        assertFalse(overCount.toggle(song(1)).success());
        assertArrayEquals(tooMany, Files.readAllBytes(path));
    }

    @Test
    void atTheCountLimitRemovalStillWorksAndFailedAdditionDoesNotChangeTheFile() throws IOException {
        Path path = temporary.resolve("favorites.json");
        JsonObject document = document();
        for (int i = 1; i <= ClientFavoriteStore.MAX_FAVORITES; i++) {
            document.getAsJsonArray("songs").add(GSON.toJsonTree(song(i)));
        }
        Files.writeString(path, GSON.toJson(document));
        ClientFavoriteStore.Store store = new ClientFavoriteStore.Store(path);
        byte[] before = Files.readAllBytes(path);
        assertTrue(store.status().success());
        assertFalse(store.toggle(song(ClientFavoriteStore.MAX_FAVORITES + 1L)).success());
        assertArrayEquals(before, Files.readAllBytes(path));
        assertTrue(store.toggle(song(1)).success());
        assertEquals(ClientFavoriteStore.MAX_FAVORITES - 1, store.snapshot().size());
    }

    private static JsonObject document(SongInfo... songs) {
        JsonObject json = new JsonObject();
        json.addProperty("version", 1);
        JsonArray items = new JsonArray();
        for (SongInfo song : songs) items.add(GSON.toJsonTree(song));
        json.add("songs", items);
        return json;
    }

    private static SongInfo song(long id) {
        SongInfo song = new SongInfo("https://music.163.com/song?id=" + id, "Song " + id, 180);
        song.source = "netease";
        song.providerId = Long.toString(id);
        song.songId = id;
        song.artists.add("Artist");
        return song;
    }
}
