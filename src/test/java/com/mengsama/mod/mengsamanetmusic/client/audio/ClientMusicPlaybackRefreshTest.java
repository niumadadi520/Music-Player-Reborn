package com.mengsama.mod.mengsamanetmusic.client.audio;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientMusicPlaybackRefreshTest {
    @Test void physicalDropStopsOldSlotTargetsAndClearsPausedHudWithoutStoppingAnotherDevice() throws Exception {
        var device=java.util.UUID.randomUUID();
        String first="item:owner:0:"+device, moved="item:owner:17:"+device, other="item:owner:3:"+java.util.UUID.randomUUID();
        long generation=ClientMusicPlayback.beginSwitch(first);
        ClientMusicPlayback.register(first,new Object());ClientMusicPlayback.register(moved,new Object());ClientMusicPlayback.register(other,new Object());
        ClientMusicPlayback.setPaused(first,true);
        var hud=com.mengsama.mod.mengsamanetmusic.hud.MusicInfoHud.class;
        var target=hud.getDeclaredField("targetId");target.setAccessible(true);target.set(null,first);
        var info=hud.getDeclaredField("info");info.setAccessible(true);info.set(null,new SongInfo("https://example.invalid/song","saved song",60));
        try {
            ClientMusicPlayback.stopPhysicalDevice(device);
            assertFalse(ClientMusicPlayback.isActive(first));assertFalse(ClientMusicPlayback.isActive(moved));
            assertTrue(ClientMusicPlayback.isActive(other));assertFalse(ClientMusicPlayback.isPaused(first));
            assertFalse(ClientMusicPlayback.isCurrent(first,generation));
            assertNull(com.mengsama.mod.mengsamanetmusic.hud.MusicInfoHud.getTargetId());
            assertNull(com.mengsama.mod.mengsamanetmusic.hud.MusicInfoHud.getInfo());
        } finally {com.mengsama.mod.mengsamanetmusic.hud.MusicInfoHud.clearInfo();ClientMusicPlayback.resetSession();}
    }
    @Test
    void reconnectClearsOldServerGenerationAndInvalidatesOldAsyncCallbacks() {
        String target = "block:minecraft:overworld:123";
        assertTrue(ClientMusicPlayback.acceptServerGeneration(target, 9999L));
        long old = ClientMusicPlayback.beginSwitch(target);
        ClientMusicPlayback.resetSession();
        assertTrue(ClientMusicPlayback.acceptServerGeneration(target, 1L));
        long next = ClientMusicPlayback.beginSwitch(target);
        assertFalse(ClientMusicPlayback.isCurrent(target, old));
        assertTrue(ClientMusicPlayback.isCurrent(target, next));
    }

    @Test
    void recreatedBlockGetsNewGenerationWithoutChangingItsSongData() {
        String target = "block:minecraft:overworld:123";
        long first = com.mengsama.mod.mengsamanetmusic.network.PlaybackGenerations.next();
        assertTrue(ClientMusicPlayback.acceptServerGeneration(target, first));
        long recreated = com.mengsama.mod.mengsamanetmusic.network.PlaybackGenerations.next();
        assertTrue(recreated > first);
        assertTrue(ClientMusicPlayback.acceptServerGeneration(target, recreated));
        assertFalse(ClientMusicPlayback.acceptServerGeneration(target, first));
    }
    @Test void rapidSwitchRejectsEveryLateChannelExceptLastAndPreservesOtherDevice() {
        ClientMusicPlayback.resetSession();
        String target = "item:rapid", other = "block:other";
        Object independent = new Object();
        long otherGeneration = ClientMusicPlayback.beginSwitch(other);
        ClientMusicPlayback.register(other, independent);
        var generations = new java.util.ArrayList<Long>();
        var sounds = new java.util.ArrayList<Object>();
        for (int i = 0; i < 50; i++) {
            generations.add(ClientMusicPlayback.beginSwitch(target));
            Object sound = new Object(); sounds.add(sound);
            ClientMusicPlayback.register(target, sound);
        }
        for (int i = 0; i < 50; i++)
            assertEquals(i == 49, ClientMusicPlayback.isCurrentSound(target, generations.get(i), sounds.get(i)));
        assertTrue(ClientMusicPlayback.isCurrentSound(other, otherGeneration, independent));
        ClientMusicPlayback.stop(target);
        assertFalse(ClientMusicPlayback.isCurrentSound(target, generations.get(49), sounds.get(49)));
    }

    @Test void replacedSoundInSameGenerationCannotStartLateChannel() {
        long generation = ClientMusicPlayback.beginSwitch("same");
        Object old = new Object(), next = new Object();
        ClientMusicPlayback.register("same", old);
        ClientMusicPlayback.register("same", next);
        assertFalse(ClientMusicPlayback.isCurrentSound("same", generation, old));
        assertTrue(ClientMusicPlayback.isCurrentSound("same", generation, next));
    }

    @Test void movedDeviceInvalidatesOldSlotAndRejectsItsDelayedServerResponse() {
        String id = java.util.UUID.randomUUID().toString();
        String first = "item:owner:2:" + id, moved = "item:owner:18:" + id;
        assertTrue(ClientMusicPlayback.acceptServerGeneration(first, 10));
        long old = ClientMusicPlayback.beginSwitch(first);
        ClientMusicPlayback.register(first, new Object());
        assertTrue(ClientMusicPlayback.acceptServerGeneration(moved, 12));
        long next = ClientMusicPlayback.beginSwitch(moved);
        assertFalse(ClientMusicPlayback.isActive(first));
        assertFalse(ClientMusicPlayback.isCurrent(first, old));
        assertTrue(ClientMusicPlayback.isCurrent(moved, next));
        assertFalse(ClientMusicPlayback.acceptServerGeneration(first, 11));
        assertFalse(ClientMusicPlayback.acceptServerGeneration(first, 12));
        assertTrue(ClientMusicPlayback.acceptServerGeneration(first, 13));
    }

    @Test void backpackPlacementSharesPhysicalGenerationButAnotherWalkmanDoesNot() {
        String id = java.util.UUID.randomUUID().toString();
        String portable = "item:owner:-1:" + id, placed = "backpack:world:123:bag:" + id;
        String other = "item:owner:0:" + java.util.UUID.randomUUID();
        assertTrue(ClientMusicPlayback.acceptServerGeneration(portable, 200));
        long old = ClientMusicPlayback.beginSwitch(portable);
        assertTrue(ClientMusicPlayback.acceptServerGeneration(placed, 201));
        ClientMusicPlayback.beginSwitch(placed);
        assertFalse(ClientMusicPlayback.isCurrent(portable, old));
        assertFalse(ClientMusicPlayback.acceptServerGeneration(portable, 199));
        assertTrue(ClientMusicPlayback.acceptServerGeneration(other, 1));
    }

    @BeforeEach
    void reset() {
        ClientMusicPlayback.resetForTest();
    }

    @Test
    void blockAndPlayerTargetsBothIssueExactlyOneRefreshForUnexpectedPayload() {
        SongInfo song = new SongInfo("https://music.163.com/song/media/outer/url?id=123.mp3", "song", 1);
        song.source = "netease";
        song.providerId = "123";
        song.songId = 123L;
        song.rawUrl = song.songUrl;
        var failure = new NetMusicAudioStream.UnexpectedAudioPayloadException(
                NetMusicAudioStream.AudioContainer.HTML, "text/html");

        for (String target : new String[]{"block:0,64,0", "player:device-id"}) {
            assertTrue(ClientMusicPlayback.acceptServerGeneration(target, 7L));
            assertTrue(NetMusicAudioStream.shouldRefreshProvider(song, failure));
            long nonce = ClientMusicPlayback.beginRefresh(target, 7L);
            assertNotEquals(0L, nonce);
            assertEquals(0L, ClientMusicPlayback.beginRefresh(target, 7L));
            assertTrue(ClientMusicPlayback.acceptServerGeneration(target, 8L, nonce));
        }
    }

    @Test
    void matchingSeekResponseIsConsumedButOlderResponseIsRejected() throws Exception {
        String target = "block:seek";
        SongInfo song = new SongInfo("https://example.test/song.mp3", "song", 120);
        ClientMusicPlayback.register(target, new Object());
        java.lang.reflect.Field pending = ClientMusicPlayback.class.getDeclaredField("PENDING_SEEKS");
        pending.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> seeks = (java.util.Map<String, Object>) pending.get(null);
        var constructor = Class.forName(ClientMusicPlayback.class.getName() + "$PendingSeek")
                .getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        seeks.put(target, constructor.newInstance(80, song.identityKey(), System.nanoTime()));

        assertTrue(ClientMusicPlayback.consumeSeekResponse(target, 81, song.identityKey()),
                "an older authoritative response must not replace the newer local seek");
        assertTrue(ClientMusicPlayback.consumeSeekResponse(target, 80, song.identityKey()),
                "the matching response must be consumed without replacing the stream");
        assertFalse(ClientMusicPlayback.consumeSeekResponse(target, 80, song.identityKey()));
    }

    @Test
    void pauseStateIsTargetScopedAndStopClearsIt() throws Exception {
        String target = "item:player:slot:device";
        java.lang.reflect.Field paused = ClientMusicPlayback.class.getDeclaredField("PAUSED");
        paused.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Boolean> states = (java.util.Map<String, Boolean>) paused.get(null);

        states.put(target, true);
        assertTrue(ClientMusicPlayback.isPaused(target));
        assertFalse(ClientMusicPlayback.isPaused("other"));
        ClientMusicPlayback.stop(target);
        assertFalse(ClientMusicPlayback.isPaused(target));
    }

    @Test
    void pauseCommandsAreOrderedWithinOnePlaybackGeneration() {
        String target = "block:test:1";
        assertTrue(ClientMusicPlayback.acceptServerGeneration(target, 12L));
        assertTrue(ClientMusicPlayback.acceptPauseCommand(target, 12L, 100L));
        assertFalse(ClientMusicPlayback.acceptPauseCommand(target, 12L, 99L),
                "delayed pause must not override a newer resume");
        assertTrue(ClientMusicPlayback.acceptPauseCommand(target, 12L, 101L));
        assertFalse(ClientMusicPlayback.acceptPauseCommand(target, 11L, 102L),
                "control for replaced playback must be rejected");
    }

    @Test
    void earlyPauseSurvivesUntilSoundAndChannelRegistration() {
        String target = "item:player:2:device";
        assertTrue(ClientMusicPlayback.setPaused(target, true, 8L, 1L));
        assertTrue(ClientMusicPlayback.isPaused(target));
        assertTrue(ClientMusicPlayback.acceptServerGeneration(target, 8L));
        ClientMusicPlayback.register(target, new Object());
        assertTrue(ClientMusicPlayback.isPaused(target));
    }

    @Test
    void pauseForNextPlaybackGenerationIsAcceptedBeforePlayPacketArrives() {
        String target = "item:player:2:device";
        assertTrue(ClientMusicPlayback.acceptServerGeneration(target, 7L));
        assertTrue(ClientMusicPlayback.setPaused(target, true, 8L, 10L),
                "pause from the server's next playback must survive packet reordering");
        assertTrue(ClientMusicPlayback.isPaused(target));
    }

    @Test
    void localPreviewCannotResumeAnAuthoritativePauseWithOlderSequence() {
        String target = "block:test:paused";
        assertTrue(ClientMusicPlayback.acceptServerGeneration(target, 21L));
        assertTrue(ClientMusicPlayback.setPaused(target, true, 21L, 500L));
        assertTrue(ClientMusicPlayback.isPaused(target));

        assertFalse(ClientMusicPlayback.setPaused(target, false, 21L, 499L));
        assertTrue(ClientMusicPlayback.isPaused(target),
                "screen removal or a delayed local preview must not resume authoritative pause");
        ClientMusicPlayback.setPaused(target, false, 21L, 501L);
        assertFalse(ClientMusicPlayback.isPaused(target));
    }

    @Test
    void handheldTargetFollowsPhysicalInstanceAfterInventorySlotChanges() {
        String instance = java.util.UUID.randomUUID().toString();
        String playingTarget = "item:player:2:" + instance;
        String movedTarget = "item:player:7:" + instance;
        Object sound = new Object();
        ClientMusicPlayback.register(playingTarget, sound);

        assertEquals(playingTarget, ClientMusicPlayback.authoritativeTarget(movedTarget));
        assertEquals("unrelated", ClientMusicPlayback.authoritativeTarget("unrelated"));
    }

    @Test
    void refreshFailureCanRetryOnlyAfterANewExplicitPlaybackRequest() {
        assertTrue(ClientMusicPlayback.acceptServerGeneration("target", 3L));
        long nonce = ClientMusicPlayback.beginRefresh("target", 3L);
        assertNotEquals(0L, nonce);
        assertEquals(0L, ClientMusicPlayback.beginRefresh("target", 3L));
        assertFalse(ClientMusicPlayback.acceptServerGeneration("target", 4L, nonce + 1));
        assertTrue(ClientMusicPlayback.acceptServerGeneration("target", 4L, nonce));

        assertEquals(0L, ClientMusicPlayback.beginRefresh("target", 4L),
                "a failed refreshed stream must not start another refresh/play loop");
        assertTrue(ClientMusicPlayback.acceptServerGeneration("target", 5L));
        assertNotEquals(0L, ClientMusicPlayback.beginRefresh("target", 5L));
    }
}
