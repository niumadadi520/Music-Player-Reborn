package com.mengsama.mod.mengsamanetmusic.client.audio;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;

 
final class PcmQueue {
    private final ArrayDeque<ByteBuffer> chunks = new ArrayDeque<>();
    synchronized void offer(ByteBuffer data) { if (data.hasRemaining()) chunks.addLast(data.asReadOnlyBuffer()); }
    synchronized int size() { return chunks.size(); }
    synchronized boolean isEmpty() { return chunks.isEmpty(); }
    synchronized void clear() { chunks.clear(); }
    synchronized ByteBuffer drain(int budget) {
        if (budget < 1 || chunks.isEmpty()) return null;
        ByteBuffer result = ByteBuffer.allocateDirect(budget);
        while (result.hasRemaining() && !chunks.isEmpty()) {
            ByteBuffer cursor = chunks.getFirst();
            int count = Math.min(result.remaining(), cursor.remaining());
            result.put(cursor.slice(cursor.position(), count));
            cursor.position(cursor.position() + count);
            if (!cursor.hasRemaining()) chunks.removeFirst();
        }
        return result.flip();
    }
}
