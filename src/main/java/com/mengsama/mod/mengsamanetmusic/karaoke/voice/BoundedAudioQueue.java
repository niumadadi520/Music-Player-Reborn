package com.mengsama.mod.mengsamanetmusic.karaoke.voice;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

 
final class BoundedAudioQueue<T> {
    private final int maxStreams;
    private final int framesPerStream;
    private final LinkedHashMap<UUID, ArrayDeque<T>> streams = new LinkedHashMap<>();

    BoundedAudioQueue(int maxStreams, int framesPerStream) {
        if (maxStreams < 1 || framesPerStream < 1) throw new IllegalArgumentException("Invalid audio queue limit");
        this.maxStreams = maxStreams;
        this.framesPerStream = framesPerStream;
    }

    synchronized boolean offer(UUID stream, T frame) {
        ArrayDeque<T> queue = streams.get(stream);
        if (queue == null) {
            if (streams.size() >= maxStreams) return false;
            queue = new ArrayDeque<>();
            streams.put(stream, queue);
        }
        if (queue.size() == framesPerStream) queue.removeFirst();
        queue.addLast(frame);
        notifyAll();
        return true;
    }

    synchronized T poll() {
        var iterator = streams.entrySet().iterator();
        if (!iterator.hasNext()) return null;
        Map.Entry<UUID, ArrayDeque<T>> entry = iterator.next();
        UUID id = entry.getKey();
        ArrayDeque<T> queue = entry.getValue();
        iterator.remove();
        T frame = queue.removeFirst();
        if (!queue.isEmpty()) streams.put(id, queue);
        return frame;
    }

    synchronized T await(long millis) throws InterruptedException {
        if (streams.isEmpty()) wait(millis);
        return poll();
    }

    synchronized void remove(UUID stream) { streams.remove(stream); }
    synchronized void clear() { streams.clear(); notifyAll(); }
    synchronized int size() { return streams.values().stream().mapToInt(ArrayDeque::size).sum(); }
}
