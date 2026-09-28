package com.mengsama.mod.mengsamanetmusic.karaoke.voice;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

 
class VoicechatServerRouterTest {
    @Test void routesIndependentVolumesAcrossDestinationsWhileOrdinaryVoiceIsDisabled() throws Exception {
        Harness api = new Harness();
        VoicechatServerRouter router = new VoicechatServerRouter(api.api());
        UUID singer = UUID.randomUUID(), nonce = UUID.randomUUID();
        var first = api.destination("overworld:0,64,0", 50);
        var second = api.destination("nether:900000,70,-800000", 25);
        try {
            assertTrue(router.playerConnected(singer), "Ordinary disabled state must not block the K song connection");
            deliver(router, api, singer, nonce, List.of(first, second), 2);
            assertEquals(2, api.channels.size());
            assertNotEquals(api.channels.get(0).id, api.channels.get(1).id);
            assertTrue(api.channels.stream().anyMatch(c -> c.samples.contains(50)));
            assertTrue(api.channels.stream().anyMatch(c -> c.samples.contains(25)));
            for (MemoryChannel channel : api.channels) {
                assertEquals(32f, channel.distance);
                assertEquals(VoicechatServerRouter.CATEGORY, channel.category);
                assertFalse(channel.filter.test(player(singer)), "The singer must not receive their own feedback");
                assertTrue(channel.filter.test(player(UUID.randomUUID())));
            }
            assertNotEquals(api.mainThread, api.codecThread, "Codecs must not run on the Minecraft publication thread");
            assertEquals(api.mainThread, api.publicationThread);
        } finally { router.close(); }
        await(() -> api.closedDecoders.get() > 0);
    }

    @Test void endingSessionWhileDecodeIsInFlightCannotPublishOrReviveItsChannel() throws Exception {
        Harness api = new Harness(); api.blockFirstDecode.set(true);
        VoicechatServerRouter router = new VoicechatServerRouter(api.api());
        UUID singer = UUID.randomUUID(), nonce = UUID.randomUUID();
        try {
            router.routeDestinations(singer, nonce, new byte[]{100}, List.of(api.destination("overworld:0,64,0", 50)));
            assertTrue(api.decodeEntered.await(2, TimeUnit.SECONDS));
            router.endSession(singer, nonce);
            api.releaseDecode.countDown();
            await(() -> api.closedDecoders.get() > 0);
            router.tick();
            assertEquals(0, api.packetCount());
            assertTrue(api.channels.isEmpty());
        } finally { api.releaseDecode.countDown(); router.close(); }
    }

    @Test void replacingSessionDropsInFlightAudioFromTheOldNonce() throws Exception {
        Harness api = new Harness(); api.blockFirstDecode.set(true);
        VoicechatServerRouter router = new VoicechatServerRouter(api.api());
        UUID singer = UUID.randomUUID(), oldNonce = UUID.randomUUID(), newNonce = UUID.randomUUID();
        var oldTarget = api.destination("overworld:1,64,0", 25);
        var newTarget = api.destination("overworld:2,64,0", 50);
        try {
            router.routeDestinations(singer, oldNonce, new byte[]{100}, List.of(oldTarget));
            assertTrue(api.decodeEntered.await(2, TimeUnit.SECONDS));
            router.routeDestinations(singer, newNonce, new byte[]{100}, List.of(newTarget));
            api.releaseDecode.countDown();
            deliver(router, api, singer, newNonce, List.of(newTarget), 1);
            assertTrue(api.channels.stream().allMatch(c -> c.location.equals(newTarget.location)));
            assertTrue(api.channels.stream().flatMap(c -> c.samples.stream()).allMatch(sample -> sample == 50));
        } finally { api.releaseDecode.countDown(); router.close(); }
    }

    @Test void volumeAdjustmentKeepsSequenceChannelButRelinkingGetsNewIdentity() throws Exception {
        Harness api = new Harness();
        VoicechatServerRouter router = new VoicechatServerRouter(api.api());
        UUID singer = UUID.randomUUID(), nonce = UUID.randomUUID();
        var first = api.destination("overworld:0,64,0", 50);
        try {
            deliver(router, api, singer, nonce, List.of(first), 1);
            UUID initial = api.channels.get(0).id;
            int packets = api.packetCount();
            var quieter = first.withVolume(25);
            deliver(router, api, singer, nonce, List.of(quieter), packets + 1);
            assertEquals(1, api.channels.size(), "Volume-only updates must not reset Voice Chat's sequence numbers");
            assertEquals(initial, api.channels.get(0).id);
            assertTrue(api.channels.get(0).samples.contains(25));
            router.routeDestinations(singer, nonce, new byte[]{100}, List.of());
            assertTrue(api.channels.get(0).flushes.get() > 0);
            packets = api.packetCount();
            deliver(router, api, singer, nonce, List.of(quieter), packets + 1);
            assertEquals(2, api.channels.size());
            assertNotEquals(initial, api.channels.get(1).id, "A relinked channel must get a fresh sequence identity");
        } finally { router.close(); }
    }

    @Test void unloadedOrZeroVolumeSpeakersNeverPublishAudio() throws Exception {
        Harness api = new Harness(); api.blockFirstDecode.set(true);
        VoicechatServerRouter router = new VoicechatServerRouter(api.api());
        UUID singer = UUID.randomUUID(), nonce = UUID.randomUUID();
        var target = api.destination("overworld:0,64,0", 50);
        try {
            router.routeDestinations(singer, nonce, new byte[]{100}, List.of(target));
            assertTrue(api.decodeEntered.await(2, TimeUnit.SECONDS));
            target.available.set(false);
            api.releaseDecode.countDown();
            await(() -> api.decodedFrames.get() > 0);
            for (int i = 0; i < 10; i++) { router.tick(); Thread.sleep(2); }
            assertTrue(api.channels.isEmpty());
            router.routeDestinations(singer, nonce, new byte[]{100}, List.of(target.withVolume(0)));
            await(() -> api.closedDecoders.get() > 0);
            router.tick();
            assertEquals(0, api.packetCount());
        } finally { api.releaseDecode.countDown(); router.close(); }
    }

    private static void deliver(VoicechatServerRouter router, Harness api, UUID singer, UUID nonce,
                                List<VoicechatServerRouter.Destination> targets, int wanted) throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L, next = 0;
        while (api.packetCount() < wanted && System.nanoTime() < deadline) {
            if (System.nanoTime() >= next) {
                router.routeDestinations(singer, nonce, new byte[]{100}, targets);
                next = System.nanoTime() + 50_000_000L;
            }
            router.tick();
            Thread.sleep(1);
        }
        assertTrue(api.packetCount() >= wanted, "Timed out waiting for production router output");
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(1);
        assertTrue(condition.getAsBoolean(), "Timed out waiting for codec resource cleanup");
    }

    private static de.maxhenkel.voicechat.api.ServerPlayer player(UUID uuid) {
        return proxy(de.maxhenkel.voicechat.api.ServerPlayer.class, (object, method, args) -> method.getName().equals("getUuid") ? uuid : defaultValue(method));
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, handler));
    }

    private static Object defaultValue(Method method) {
        if (method.getReturnType() == boolean.class) return false;
        if (method.getReturnType() == int.class) return 0;
        if (method.getReturnType() == long.class) return 0L;
        if (method.getReturnType() == float.class) return 0f;
        if (method.getReturnType() == double.class) return 0d;
        return null;
    }

    private static final class Harness {
        final long mainThread = Thread.currentThread().getId();
        volatile long codecThread, publicationThread;
        final CopyOnWriteArrayList<MemoryChannel> channels = new CopyOnWriteArrayList<>();
        final AtomicBoolean blockFirstDecode = new AtomicBoolean();
        final CountDownLatch decodeEntered = new CountDownLatch(1), releaseDecode = new CountDownLatch(1);
        final AtomicInteger closedDecoders = new AtomicInteger(), decodedFrames = new AtomicInteger();

        VoicechatServerApi api() {
            return proxy(VoicechatServerApi.class, (object, method, args) -> switch (method.getName()) {
                case "volumeCategoryBuilder" -> proxy(VolumeCategory.Builder.class, (builder, call, parameters) ->
                        call.getName().equals("build") ? proxy(VolumeCategory.class, (a, b, c) -> defaultValue(b)) : builder);
                case "getConnectionOf" -> proxy(VoicechatConnection.class, (connection, call, parameters) -> switch (call.getName()) {
                    case "isInstalled", "isConnected" -> true;
                    case "isDisabled" -> throw new AssertionError("Router must not inspect the singer's ordinary disabled switch");
                    default -> defaultValue(call);
                });
                case "createDecoder" -> decoder();
                case "createEncoder" -> encoder();
                default -> defaultValue(method);
            });
        }

        MemoryDestination destination(String location, int volume) {
            return new MemoryDestination(UUID.randomUUID(), location, volume, this, new AtomicBoolean(true));
        }

        int packetCount() { return channels.stream().mapToInt(c -> c.samples.size()).sum(); }

        OpusDecoder decoder() {
            AtomicBoolean closed = new AtomicBoolean();
            return proxy(OpusDecoder.class, (object, method, args) -> switch (method.getName()) {
                case "decode" -> {
                    codecThread = Thread.currentThread().getId();
                    if (blockFirstDecode.compareAndSet(true, false)) {
                        decodeEntered.countDown();
                        if (!releaseDecode.await(2, TimeUnit.SECONDS)) throw new AssertionError("Decode latch timed out");
                    }
                    short[] frame = new short[960]; Arrays.fill(frame, (short) 10000);
                    decodedFrames.incrementAndGet();
                    yield frame;
                }
                case "close" -> { if (closed.compareAndSet(false, true)) closedDecoders.incrementAndGet(); yield null; }
                case "isClosed" -> closed.get();
                default -> defaultValue(method);
            });
        }

        OpusEncoder encoder() {
            AtomicBoolean closed = new AtomicBoolean();
            return proxy(OpusEncoder.class, (object, method, args) -> switch (method.getName()) {
                case "encode" -> { codecThread = Thread.currentThread().getId(); yield new byte[]{(byte) (((short[]) args[0])[0] / 100)}; }
                case "close" -> { closed.set(true); yield null; }
                case "isClosed" -> closed.get();
                default -> defaultValue(method);
            });
        }
    }

    private record MemoryDestination(UUID channelId, String location, int volume, Harness harness,
                                     AtomicBoolean available) implements VoicechatServerRouter.Destination {
        public boolean loaded() { return available.get(); }
        public boolean sameLocation(VoicechatServerRouter.Destination other) {
            return other instanceof MemoryDestination memory && location.equals(memory.location);
        }
        public LocationalAudioChannel create(VoicechatServerApi api, UUID id) {
            MemoryChannel channel = new MemoryChannel(id, location, harness);
            harness.channels.add(channel);
            return channel.api();
        }
        MemoryDestination withVolume(int changed) { return new MemoryDestination(channelId, location, changed, harness, available); }
    }

    private static final class MemoryChannel {
        final UUID id;
        final String location;
        final Harness harness;
        final CopyOnWriteArrayList<Integer> samples = new CopyOnWriteArrayList<>();
        final AtomicInteger flushes = new AtomicInteger();
        Predicate<de.maxhenkel.voicechat.api.ServerPlayer> filter;
        float distance;
        String category;
        MemoryChannel(UUID id, String location, Harness harness) { this.id = id; this.location = location; this.harness = harness; }
        @SuppressWarnings("unchecked")
        LocationalAudioChannel api() {
            return proxy(LocationalAudioChannel.class, (object, method, args) -> switch (method.getName()) {
                case "send" -> { harness.publicationThread = Thread.currentThread().getId(); samples.add((int) ((byte[]) args[0])[0]); yield null; }
                case "flush" -> { flushes.incrementAndGet(); yield null; }
                case "setDistance" -> { distance = (float) args[0]; yield null; }
                case "setCategory" -> { category = (String) args[0]; yield null; }
                case "setFilter" -> { filter = (Predicate<de.maxhenkel.voicechat.api.ServerPlayer>) args[0]; yield null; }
                case "getId" -> id;
                default -> defaultValue(method);
            });
        }
    }
}
