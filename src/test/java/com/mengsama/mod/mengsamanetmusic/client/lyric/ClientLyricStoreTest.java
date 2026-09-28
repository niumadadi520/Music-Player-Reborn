package com.mengsama.mod.mengsamanetmusic.client.lyric;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ClientLyricStoreTest {
    private Queue<Runnable> work;
    private AtomicInteger loads;
    private LyricRepository repository;

    @BeforeEach void reset() {
        ClientLyricStore.resetForTest();
        work = new ArrayDeque<>();
        loads = new AtomicInteger();
        repository = new LyricRepository(song -> {
            loads.incrementAndGet();
            TreeMap<Long, String> lines = new TreeMap<>();
            lines.put(0L, song.songName + "-0");
            lines.put(5_000L, song.songName + "-5");
            lines.put(10_000L, song.songName + "-10");
            return new LyricRepository.LyricData(lines, false);
        }, work::add);
    }

    @Test void targetsAreIsolatedAndSameIdentitySharesOneRequest() {
        SongInfo same = song("netease", "11", "same");
        ClientLyricStore.bind("block:a", same, repository);
        ClientLyricStore.bind("block:b", same, repository);
        assertEquals(1, work.size(), "identity cache must deduplicate provider request");
        work.remove().run();
        assertEquals(1, loads.get());
        assertEquals("same-0", ClientLyricStore.snapshot("block:a").data().lineAt(0));
        assertEquals("same-0", ClientLyricStore.snapshot("block:b").data().lineAt(0));

        ClientLyricStore.clear("block:a");
        assertEquals(ClientLyricStore.State.IDLE, ClientLyricStore.snapshot("block:a").state());
        assertEquals(ClientLyricStore.State.READY, ClientLyricStore.snapshot("block:b").state());
    }

    @Test void staleCallbackCannotReplaceSwitchedIdentity() {
        SongInfo oldSong = song("netease", "1", "old");
        SongInfo newSong = song("netease", "2", "new");
        ClientLyricStore.bind("target", oldSong, repository);
        Runnable oldRequest = work.remove();
        ClientLyricStore.bind("target", newSong, repository);
        Runnable newRequest = work.remove();

        oldRequest.run();
        assertEquals("netease:2", ClientLyricStore.snapshot("target").identity());
        assertEquals(ClientLyricStore.State.LOADING, ClientLyricStore.snapshot("target").state());
        newRequest.run();
        assertEquals("new-0", ClientLyricStore.snapshot("target").data().lineAt(0));
    }

    @Test void switchSeekPauseAndStopHaveDeterministicCursor() {
        SongInfo first = song("netease", "1", "first");
        ClientLyricStore.bind("target", first, repository);
        work.remove().run();
        var data = ClientLyricStore.snapshot("target").data();
        assertEquals(0, data.lineIndexAt(4_999));
        assertEquals(1, data.lineIndexAt(5_000));
        assertEquals(2, data.lineIndexAt(11_000));
        assertEquals(1, data.lineIndexAt(5_000), "pause keeps the same tick/current line");
        assertEquals(0, data.lineIndexAt(1_000), "backward seek relocates by timestamp");

        ClientLyricStore.bind("target", song("netease", "2", "second"), repository);
        assertTrue(ClientLyricStore.snapshot("target").data().lines().isEmpty(), "switch clears old lyrics synchronously");
        ClientLyricStore.clear("target");
        assertEquals(ClientLyricStore.State.IDLE, ClientLyricStore.snapshot("target").state());
        assertEquals(-1, ClientLyricStore.snapshot("target").data().lineIndexAt(5_000), "stop has no highlighted line");
    }

    @Test void heldAndPlacedGuiUseTheSameTickClockAndPausedTickKeepsHighlight() {
        TreeMap<Long, String> lines = new TreeMap<>();
        lines.put(0L, "zero");
        lines.put(5_000L, "five");
        lines.put(10_000L, "ten");
        var data = new LyricRepository.LyricData(lines, false);

        assertEquals(4_950L, ClientLyricStore.playbackMillis(99));
        assertEquals(0, ClientLyricStore.lineIndexAtTick(data, 99));
        assertEquals(1, ClientLyricStore.lineIndexAtTick(data, 100));
        assertEquals(1, ClientLyricStore.lineIndexAtTick(data, 100),
                "pause must retain the exact sound tick and highlighted line");
        assertEquals(2, ClientLyricStore.lineIndexAtTick(data, 200));
        assertEquals(-1, ClientLyricStore.lineIndexAtTick(data, -1));
    }

    @Test void loadingTransitionsToReadyWithoutChangingGeneration() {
        ClientLyricStore.Snapshot loading = ClientLyricStore.bind("target", song("netease", "1", "normal"), repository);
        assertEquals(ClientLyricStore.State.LOADING, loading.state());

        work.remove().run();
        ClientLyricStore.Snapshot ready = ClientLyricStore.snapshot("target");
        assertEquals(ClientLyricStore.State.READY, ready.state());
        assertEquals(loading.generation(), ready.generation(), "GUI must observe state changes within one generation");
        assertEquals(3, ready.data().lines().size());
    }

    @Test void emptyAndFailedStatesRemainVisibleOnRepeatedRefresh() {
        LyricRepository emptyRepository = new LyricRepository(song -> LyricRepository.LyricData.empty(true), work::add);
        ClientLyricStore.bind("empty-player", song("apple", "empty", "empty"), emptyRepository);
        work.remove().run();
        assertEquals(ClientLyricStore.State.EMPTY, ClientLyricStore.snapshot("empty-player").state());
        assertTrue(ClientLyricStore.snapshot("empty-player").data().explicitlyUnavailable());

        LyricRepository failedRepository = new LyricRepository(song -> { throw new IllegalStateException("provider down"); }, work::add);
        SongInfo failedSong = song("qq", "failed", "failed");
        ClientLyricStore.bind("failed-player", failedSong, failedRepository);
        work.remove().run();
        ClientLyricStore.Snapshot failed = ClientLyricStore.snapshot("failed-player");
        assertEquals(ClientLyricStore.State.FAILED, failed.state());

        ClientLyricStore.Snapshot refreshed = ClientLyricStore.bind("failed-player", failedSong, failedRepository);
        assertEquals(ClientLyricStore.State.FAILED, refreshed.state(), "refresh must not hide the failure behind a new loading state");
        assertTrue(work.isEmpty(), "same failed binding must not start an unbounded retry loop");
    }

    @Test void providerRoutingUsesNeteaseSongIdQqProviderIdAndAppleExplicitEmpty() {
        SongInfo netease = song("netease", "123", "n"); netease.songId = 123;
        SongInfo qq = song("qq", "MID123", "q");
        SongInfo apple = song("apple", "apple-id", "a");
        assertEquals(LyricRepository.ProviderRoute.NETEASE, LyricRepository.route(netease));
        assertEquals(LyricRepository.ProviderRoute.QQ, LyricRepository.route(qq));
        assertEquals(LyricRepository.ProviderRoute.APPLE, LyricRepository.route(apple));
        assertTrue(LyricRepository.LyricData.empty(true).explicitlyUnavailable());
    }

    @Test void guiUsesMenuSongUntilSoundRegistrationAndNeverOverridesActiveIdentity() {
        SongInfo menuSong = song("netease", "123", "menu");
        SongInfo activeSong = song("netease", "456", "active");
        assertSame(menuSong, ClientLyricStore.selectGuiSong(null, true, menuSong),
                "opening the GUI before sound registration must still bind lyrics");
        assertSame(activeSong, ClientLyricStore.selectGuiSong(activeSong, true, menuSong),
                "the active sound remains authoritative after registration");
        assertNull(ClientLyricStore.selectGuiSong(null, false, menuSong),
                "a stopped menu must not retain stale lyrics");
    }

    private static SongInfo song(String source, String providerId, String name) {
        SongInfo song = new SongInfo("https://example.invalid/" + providerId, name, 180);
        song.source = source;
        song.providerId = providerId;
        if ("netease".equals(source)) song.songId = Long.parseLong(providerId);
        return song;
    }
}
