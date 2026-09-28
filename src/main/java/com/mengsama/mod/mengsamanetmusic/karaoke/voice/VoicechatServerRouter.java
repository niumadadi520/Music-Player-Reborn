package com.mengsama.mod.mengsamanetmusic.karaoke.voice;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

 
final class VoicechatServerRouter implements KaraokeVoiceBridge.Backend {
     
    interface Destination {
        UUID channelId();
        int volume();
        boolean loaded();
        LocationalAudioChannel create(VoicechatServerApi api, UUID id);
        boolean sameLocation(Destination other);
    }

    private record WorldDestination(KaraokeVoiceBridge.Target target) implements Destination {
        public UUID channelId() { return target.channelId(); }
        public int volume() { return target.volume(); }
        public boolean loaded() { return target.level().hasChunkAt(target.pos()); }
        public LocationalAudioChannel create(VoicechatServerApi api, UUID id) {
            return api.createLocationalAudioChannel(id, api.fromServerLevel(target.level()),
                    api.createPosition(target.pos().getX() + 0.5, target.pos().getY() + 0.6, target.pos().getZ() + 0.5));
        }
        public boolean sameLocation(Destination other) {
            return other instanceof WorldDestination world && target.level() == world.target.level()
                    && target.pos().equals(world.target.pos());
        }
    }
    static final int MAX_PERFORMERS = 64;
    static final int MAX_TARGETS = 64;
    static final String CATEGORY = "mengsama_karaoke";
    private final VoicechatServerApi api;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final BoundedAudioQueue<Input> incoming = new BoundedAudioQueue<>(MAX_PERFORMERS, 5);
    private final BoundedAudioQueue<Output> outgoing = new BoundedAudioQueue<>(MAX_PERFORMERS, 5);
     
    private final Map<Session, Codec> codecs = new HashMap<>();
    private final Map<Session, Map<UUID, Channel>> channels = new HashMap<>();
    private final Thread worker;
    private volatile boolean running = true;
    private boolean codecFailureReported;
    private long channelGeneration;

    VoicechatServerRouter(VoicechatServerApi api) {
        this.api = api;
        api.registerVolumeCategory(api.volumeCategoryBuilder().setId(CATEGORY).setName("K歌音响")
                .setDescription("音乐机模组麦克风通过已连接音响播放的歌声").build());
        worker = new Thread(this::process, "MengSama-Karaoke-Codec");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public boolean playerConnected(UUID player) {
        VoicechatConnection connection = api.getConnectionOf(player);
         
        return connection != null && connection.isInstalled() && connection.isConnected();
    }

    @Override
    public void route(UUID performer, UUID nonce, byte[] opus, List<KaraokeVoiceBridge.Target> targets) {
        routeDestinations(performer, nonce, opus, targets.stream().map(WorldDestination::new).map(Destination.class::cast).toList());
    }

    void routeDestinations(UUID performer, UUID nonce, byte[] opus, List<Destination> targets) {
        if (!running) return;
        LinkedHashMap<UUID, Destination> destinations = new LinkedHashMap<>();
        for (Destination target : targets) {
            if (target.volume() > 0 && target.loaded()) destinations.put(target.channelId(), target);
            if (destinations.size() == MAX_TARGETS) break;
        }
        Session session = sessions.get(performer);
        if (destinations.isEmpty()) {
            if (session != null && session.nonce.equals(nonce)) endSession(performer, nonce);
            return;
        }
        if (session != null && !session.nonce.equals(nonce)) {
            endSession(performer, session.nonce);
            session = null;
        }
        if (session == null) {
            if (sessions.size() >= MAX_PERFORMERS) return;
            session = new Session(performer, nonce);
            sessions.put(performer, session);
        }
        session.targets = Map.copyOf(destinations);
        session.lastInput = System.nanoTime();
        reconcileChannels(session);
        incoming.offer(performer, new Input(session, opus.clone(), new ArrayList<>(destinations.values()), session.lastInput));
    }

    @Override
    public void endSession(UUID performer, UUID nonce) {
        Session session = sessions.get(performer);
        if (session == null || !session.nonce.equals(nonce)) return;
        sessions.remove(performer, session);
        incoming.remove(performer);
        outgoing.remove(performer);
        Map<UUID, Channel> removed = channels.remove(session);
        if (removed != null) removed.values().forEach(Channel::flush);
    }

    @Override
    public void tick() {
        if (!running) return;
        long now = System.nanoTime();
        for (Session session : List.copyOf(sessions.values())) {
            if (now - session.lastInput > 2_000_000_000L) endSession(session.performer, session.nonce);
        }
        for (int i = 0; i < MAX_PERFORMERS * 5; i++) {
            Output frame = outgoing.poll();
            if (frame == null) break;
            if (!current(frame.session) || now - frame.created > VoiceAudioMath.MAX_AGE_NANOS) continue;
            Map<UUID, Channel> activeChannels = channels.computeIfAbsent(frame.session, ignored -> new HashMap<>());
            for (Destination target : frame.targets) {
                if (!target.equals(frame.session.targets.get(target.channelId())) || !target.loaded()) continue;
                byte[] encoded = frame.byVolume.get(target.volume());
                if (encoded == null) continue;
                Channel channel = activeChannels.get(target.channelId());
                if (channel == null || channel.audio.isClosed()) {
                     
                    UUID id = UUID.nameUUIDFromBytes((frame.session.nonce + ":" + target.channelId() + ":" + ++channelGeneration)
                            .getBytes(StandardCharsets.UTF_8));
                    LocationalAudioChannel audio = target.create(api, id);
                    if (audio == null) continue;
                    audio.setDistance(32);
                    audio.setCategory(CATEGORY);
                     
                    UUID performerId = frame.session.performer;
                    audio.setFilter(player -> !performerId.equals(player.getUuid()));
                    channel = new Channel(target, audio);
                    activeChannels.put(target.channelId(), channel);
                }
                channel.audio.send(encoded);
            }
        }
    }

    private void reconcileChannels(Session session) {
        Map<UUID, Channel> activeChannels = channels.get(session);
        if (activeChannels == null) return;
        var iterator = activeChannels.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!entry.getValue().sameLocation(session.targets.get(entry.getKey()))) {
                entry.getValue().flush();
                iterator.remove();
            }
        }
    }

    private boolean current(Session session) { return running && sessions.get(session.performer) == session; }

    private void process() {
        try {
            while (running) {
                pruneCodecs();
                Input frame = incoming.await(20);
                if (frame == null || !current(frame.session) || System.nanoTime() - frame.created > VoiceAudioMath.MAX_AGE_NANOS) continue;
                try {
                    Codec codec = codecs.computeIfAbsent(frame.session, ignored -> new Codec(api.createDecoder()));
                    short[] samples = codec.decoder.decode(frame.opus);
                    if (samples == null || samples.length != VoiceAudioMath.FRAME_SAMPLES) continue;
                    Map<Integer, byte[]> scaled = new HashMap<>();
                    Set<Integer> volumes = new HashSet<>();
                    for (Destination target : frame.targets) volumes.add(target.volume());
                    codec.retain(volumes);
                    for (int volume : volumes) {
                        if (volume == 100) scaled.put(volume, frame.opus);
                        else {
                            OpusEncoder encoder = codec.encoders.computeIfAbsent(volume, ignored -> api.createEncoder(OpusEncoderMode.AUDIO));
                            byte[] encoded = encoder.encode(VoiceAudioMath.withVolume(samples, volume));
                            if (VoiceAudioMath.validPacket(encoded)) scaled.put(volume, encoded);
                        }
                    }
                    if (current(frame.session)) outgoing.offer(frame.session.performer,
                            new Output(frame.session, frame.targets, scaled, frame.created));
                } catch (Exception | LinkageError failure) {
                    Codec removed = codecs.remove(frame.session);
                    if (removed != null) removed.close();
                     
                    if (!codecFailureReported) {
                        codecFailureReported = true;
                        MengSamaNetMusic.LOGGER.warn("Karaoke rejected a voice frame or could not initialize the Voice Chat codec ({})",
                                failure.getClass().getSimpleName());
                    }
                }
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        } finally {
            codecs.values().forEach(Codec::close);
            codecs.clear();
        }
    }

    private void pruneCodecs() {
        Iterator<Map.Entry<Session, Codec>> iterator = codecs.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Session, Codec> entry = iterator.next();
            if (!current(entry.getKey())) {
                entry.getValue().close();
                iterator.remove();
            }
        }
    }

    @Override
    public void close() {
        running = false;
        sessions.clear();
        incoming.clear();
        outgoing.clear();
        worker.interrupt();
         
    }

    private static final class Session {
        final UUID performer;
        final UUID nonce;
        volatile Map<UUID, Destination> targets = Map.of();
        volatile long lastInput;
        Session(UUID performer, UUID nonce) { this.performer = performer; this.nonce = nonce; }
    }

    private record Input(Session session, byte[] opus, List<Destination> targets, long created) {}
    private record Output(Session session, List<Destination> targets, Map<Integer, byte[]> byVolume, long created) {}
    private record Channel(Destination target, LocationalAudioChannel audio) {
        boolean sameLocation(Destination current) {
            return current != null && target.sameLocation(current);
        }
        void flush() {
            try { audio.flush(); } catch (Exception | LinkageError ignored) { }
        }
    }

    private static final class Codec {
        final OpusDecoder decoder;
        final Map<Integer, OpusEncoder> encoders = new HashMap<>();
        Codec(OpusDecoder decoder) { this.decoder = decoder; }
        void retain(Set<Integer> used) {
            var iterator = encoders.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (!used.contains(entry.getKey())) {
                    closeEncoder(entry.getValue());
                    iterator.remove();
                }
            }
        }
        void close() {
            try { decoder.close(); } catch (Exception | LinkageError ignored) { }
            encoders.values().forEach(Codec::closeEncoder);
            encoders.clear();
        }
        static void closeEncoder(OpusEncoder encoder) {
            try { encoder.close(); } catch (Exception | LinkageError ignored) { }
        }
    }
}
