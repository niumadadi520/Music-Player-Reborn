package com.mengsama.mod.mengsamanetmusic.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import com.google.gson.JsonParser;
import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.api.LrcParser;
import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.config.ModConfig;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import com.mengsama.mod.mengsamanetmusic.util.AsyncIoExecutor;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.Map;
import java.util.NavigableMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import com.mengsama.mod.mengsamanetmusic.api.LyricPayload;

 


public final class MaidLyricSynchronizer {

    private static final Map<UUID, Session> SESSIONS = new java.util.concurrent.ConcurrentHashMap<>();

    private static final LyricSessionGate GATE = new LyricSessionGate();

    private static final int RECOVERY_INTERVAL_TICKS = 100;

    private MaidLyricSynchronizer() {
    }

     


    public static void start(EntityMaid maid, String targetId, SongInfo song) {
        UUID maidId = maid.getUUID();
        long generation = GATE.next(maidId);
        Session previous = SESSIONS.remove(maidId);
        if (previous != null)
            previous.removeBubble(previous.generation);
        var device = EntityMusicDevice.heldPlayer(maid);
        int totalTicks = Math.max(0, song == null ? 0 : song.songTime * 20 + 64);
        int startElapsedTicks = playbackElapsedTicks(totalTicks, MusicPlayerItem.getCurrentTime(device), 0);
        if (!ModConfig.ENABLE_MAID_LYRICS.get() || song == null || "apple".equals(song.source))
            return;
        if (!("netease".equals(song.source) && song.songId > 0) && !("qq".equals(song.source) && song.providerId != null && !song.providerId.isBlank()))
            return;
        SongInfo stableIdentity = song.clone();
        stableIdentity.songUrl = "";
        stableIdentity.normalizeIdentity();
        Session session = new Session(generation, targetId, totalTicks, startElapsedTicks, stableIdentity, maid, maid.level() instanceof ServerLevel serverLevel ? serverLevel : null);
        SESSIONS.put(maidId, session);
        MengSamaNetMusic.LOGGER.info("[歌词阶段] session-start generation={} provider={} song={}", generation, stableIdentity.source, safeSongId(stableIdentity));
        requestLyrics(maidId, session, 0);
    }

    public static void stop(UUID maidId) {
        long generation = GATE.invalidate(maidId);
        Session old = SESSIONS.remove(maidId);
        if (old != null)
            old.removeBubble(old.generation);
        MengSamaNetMusic.LOGGER.info("[歌词阶段] session-stop generation={}", generation);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 2 != 0)
            return;
        for (var entry : SESSIONS.entrySet()) {
            UUID maidId = entry.getKey();
            Session session = entry.getValue();
            if (!current(maidId, session))
                continue;
            EntityMaid maid = session.maid;
            if (maid == null || maid.isRemoved() || !maid.getUUID().equals(maidId) || maid.level() == null) {
                if (event.getServer().getTickCount() - session.lastRecoveryTick < RECOVERY_INTERVAL_TICKS)
                    continue;
                session.lastRecoveryTick = event.getServer().getTickCount();
                maid = findMaid(session.maidLevel, event.getServer(), maidId);
                session.maid = maid;
                session.maidLevel = maid != null && maid.level() instanceof ServerLevel serverLevel ? serverLevel : null;
            }
            if (!current(maidId, session))
                continue;
            if (maid == null || !isPlaybackCurrent(maid, session)) {
                endIfCurrent(maidId, session, "playback-ended");
                continue;
            }
            session.maid = maid;
            var device = EntityMusicDevice.heldPlayer(maid);
            NavigableMap<Long, String> lines = session.lines;
            if (lines == null || lines.isEmpty())
                continue;
            long playbackMs = playbackMillis(session, maid);
            String line = session.timeline.update(lines, playbackMs, MusicPlayerItem.isPlay(device) && !MusicPlayerItem.isPaused(device));
            if (line == null || !current(maidId, session))
                continue;
            session.replaceBubble(line, bubbleDurationTicks(lines, playbackMs), session.generation);
        }
    }

    private static boolean isPlaybackCurrent(EntityMaid maid, Session session) {
        var device = EntityMusicDevice.heldPlayer(maid);
        return !device.isEmpty() && MusicPlayerItem.isPlay(device) && session.targetId.equals(EntityMusicDevice.targetId(maid, device));
    }

    private static boolean current(UUID maidId, Session session) {
        return session != null && GATE.isCurrent(maidId, session.generation) && SESSIONS.get(maidId) == session;
    }

    private static void endIfCurrent(UUID maidId, Session session, String phase) {
        if (!current(maidId, session) || !SESSIONS.remove(maidId, session))
            return;
        GATE.invalidate(maidId);
        session.removeBubble(session.generation);
        MengSamaNetMusic.LOGGER.info("[歌词阶段] session-end generation={} phase={}", session.generation, phase);
    }

    private static EntityMaid findMaid(ServerLevel cachedLevel, net.minecraft.server.MinecraftServer server, UUID maidId) {
        if (cachedLevel != null && cachedLevel.getEntity(maidId) instanceof EntityMaid found)
            return found;
        for (var level : server.getAllLevels()) {
            if (level != cachedLevel && level.getEntity(maidId) instanceof EntityMaid found)
                return found;
        }
        return null;
    }

    static int playbackElapsedTicks(int totalTicks, int currentTime, int startElapsedTicks) {
        return Math.max(0, totalTicks - Math.max(0, currentTime) - startElapsedTicks);
    }

    private static long playbackMillis(Session session, EntityMaid maid) {
        int currentTime = MusicPlayerItem.getCurrentTime(EntityMusicDevice.heldPlayer(maid));
        return playbackElapsedTicks(session.totalTicks, currentTime, session.startElapsedTicks) * 50L;
    }

    private static void requestLyrics(UUID maidId, Session session, int attempt) {
        if (!current(maidId, session))
            return;
        long delaySeconds = attempt == 0 ? 0 : Math.min(30, 1L << Math.min(5, attempt - 1));
        CompletableFuture.runAsync(() -> {
        }, CompletableFuture.delayedExecutor(delaySeconds, TimeUnit.SECONDS)).thenCompose(ignored -> AsyncIoExecutor.supplyAsync(() -> loadLrc(session.identity))).whenComplete((lines, error) -> {
            if (!current(maidId, session))
                return;
            NavigableMap<Long, String> loaded = error == null && lines != null ? lines : new java.util.TreeMap<>();
            if (!loaded.isEmpty()) {
                session.lines = loaded;
                runOnServerThread(session, () -> primeCurrentLine(maidId, session));
                MengSamaNetMusic.LOGGER.info("[歌词阶段] loaded generation={} provider={} song={} lines={} attempt={}", session.generation, session.identity.source, safeSongId(session.identity), loaded.size(), attempt + 1);
            } else if (attempt < 4) {
                MengSamaNetMusic.LOGGER.info("[歌词阶段] retry generation={} attempt={}", session.generation, attempt + 2);
                requestLyrics(maidId, session, attempt + 1);
            } else {
                 
                MengSamaNetMusic.LOGGER.warn("[歌词阶段] unavailable generation={} provider={} song={}", session.generation, session.identity.source, safeSongId(session.identity));
            }
        });
    }

     


    private static void runOnServerThread(Session session, Runnable action) {
        ServerLevel level = session.maidLevel;
        if (level == null || level.getServer() == null)
            return;
        level.getServer().execute(action);
    }

     


    private static void primeCurrentLine(UUID maidId, Session session) {
        if (!current(maidId, session) || session.maid == null)
            return;
        EntityMaid maid = session.maid;
        long playbackMs = playbackMillis(session, maid);
        var device = EntityMusicDevice.heldPlayer(maid);
        String line = session.timeline.update(session.lines, playbackMs, MusicPlayerItem.isPlay(device) && !MusicPlayerItem.isPaused(device));
        if (line == null || !current(maidId, session))
            return;
        session.replaceBubble(line, bubbleDurationTicks(session.lines, playbackMs), session.generation);
    }

     


    static int bubbleDurationTicks(NavigableMap<Long, String> lines, long playbackMs) {
        if (lines == null || lines.isEmpty())
            return 1;
        Long next = lines.higherKey(playbackMs);
        if (next == null)
            return IChatBubbleData.DEFAULT_EXIST_TICK;
        long remainingMs = Math.max(50L, next - playbackMs);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, (remainingMs + 49L) / 50L + 4L));
    }

    static NavigableMap<Long, String> loadLrc(SongInfo song) {
        try {
            if (song == null)
                return new java.util.TreeMap<>();
            if ("netease".equals(song.source) && song.songId > 0) {
                JsonObject root = JsonParser.parseString(MengSamaNetMusic.NET_EASE_API.lyric(song.songId)).getAsJsonObject();
                String originalText = LyricPayload.text(root, "lrc", "lyric", "lrcText", "originalLyric");
                String translatedText = LyricPayload.text(root, "tlyric", "translatedLyric", "translation", "transLyric");
                NavigableMap<Long, String> original = LrcParser.parseMillis(originalText);
                NavigableMap<Long, String> translated = LrcParser.parseMillis(translatedText);
                return translated.isEmpty() ? original : LrcParser.mergeTranslation(original, translated);
            }
            if ("qq".equals(song.source) && song.providerId != null && !song.providerId.isBlank())
                return LrcParser.parseMillis(com.mengsama.mod.mengsamanetmusic.api.QqMusicUtils.getLyric(song.providerId));
        } catch (Exception e) {
            MengSamaNetMusic.LOGGER.warn("[歌词阶段] request-failed provider={} song={} type={}", song == null ? "unknown" : song.source, safeSongId(song), e.getClass().getSimpleName());
        }
        return new java.util.TreeMap<>();
    }

    private static String safeSongId(SongInfo song) {
        if (song == null)
            return "unknown";
        if ("netease".equals(song.source))
            return Long.toString(song.songId);
        String id = song.providerId;
        if (id == null || id.isBlank())
            return "unknown";
        return Integer.toHexString(id.hashCode());
    }

    private static final class Session {

        final long generation;

        final String targetId;

        final int totalTicks;

        final int startElapsedTicks;

        final SongInfo identity;

        final MaidLyricTimeline timeline = new MaidLyricTimeline();

        volatile NavigableMap<Long, String> lines;

        volatile long bubbleId = -1;

        volatile EntityMaid maid;

        volatile ServerLevel maidLevel;

        volatile int lastRecoveryTick = 0;

        Session(long generation, String targetId, int totalTicks, int startElapsedTicks, SongInfo identity, EntityMaid maid, ServerLevel maidLevel) {
            this.generation = generation;
            this.targetId = targetId;
            this.totalTicks = totalTicks;
            this.startElapsedTicks = startElapsedTicks;
            this.identity = identity;
            this.maid = maid;
            this.maidLevel = maidLevel;
        }

        void replaceBubble(String line, int durationTicks, long callbackGeneration) {
            if (callbackGeneration != generation)
                return;
            EntityMaid currentMaid = maid;
            if (currentMaid == null)
                return;
            removeBubble(callbackGeneration);
            if (line == null || line.isBlank())
                return;
            TextChatBubbleData bubble = TextChatBubbleData.create(Math.max(1, durationTicks), Component.literal(line), IChatBubbleData.TYPE_2, 0);
            bubbleId = currentMaid.getChatBubbleManager().addChatBubble(bubble);
        }

        void removeBubble(long callbackGeneration) {
            if (callbackGeneration != generation)
                return;
            EntityMaid currentMaid = maid;
            long currentBubble = bubbleId;
            bubbleId = -1;
            if (currentMaid != null && currentBubble >= 0)
                currentMaid.getChatBubbleManager().removeChatBubble(currentBubble);
        }
    }
}
