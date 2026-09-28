package com.mengsama.mod.mengsamanetmusic.client.audio;

import net.minecraft.client.sounds.AudioStream;
import javax.sound.sampled.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

 
final class PcmPrefetchStream implements AudioStream {
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(4,4,30,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), job -> {
                Thread worker = Executors.defaultThreadFactory().newThread(job);
                worker.setName("MengSama-PCM-" + worker.getId());
                worker.setDaemon(true);
                return worker;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final AudioInputStream input;
    private final AudioFormat format;
    private final BooleanSupplier obsolete;
    private final int blockBytes;
    private final byte[] scratch;
    private final PcmQueue ready = new PcmQueue();
    private final Object gate = new Object();
    private volatile boolean closed;
    private boolean pending, eof;
    private IOException failure;

    PcmPrefetchStream(AudioInputStream input, int blockBytes, BooleanSupplier obsolete) throws IOException {
        this.input = input;
        this.format = input.getFormat();
        this.obsolete = obsolete;
        int frame = Math.max(1,format.getFrameSize());
        this.blockBytes = Math.max(frame,blockBytes / frame * frame);
        this.scratch = new byte[Math.max(frame,16384 / frame * frame)];
        refill();  
        if (failure != null) throw failure;
    }
    private boolean stopped() { return closed || obsolete.getAsBoolean(); }

    private void schedule() {
        synchronized (gate) {
            if (stopped() || pending || eof || failure != null || ready.size() >= 3) return;
            pending = true;
            try { WORKERS.execute(this::refill); }
            catch (RejectedExecutionException overloaded) {
                pending = false;
                failure = new IOException("PCM refill capacity exhausted",overloaded);
                gate.notifyAll();
                try { input.close(); } catch (IOException ignored) { }
            }
        }
    }

    private void refill() {
        try {
            if (stopped()) return;
            ByteBuffer output = ByteBuffer.allocateDirect(blockBytes);
            boolean finished = false;
            while (output.hasRemaining() && !stopped()) {
                int count = input.read(scratch,0,Math.min(scratch.length,output.remaining()));
                if (count < 0) { finished = true; break; }
                if (count == 0) throw new IOException("PCM decoder made no progress");
                output.put(scratch,0,count);
            }
            synchronized (gate) {
                if (!stopped()) { ready.offer(output.flip()); eof |= finished; }
            }
        } catch (IOException | RuntimeException problem) {
            synchronized (gate) {
                if (!stopped()) failure = problem instanceof IOException io ? io : new IOException("PCM decoder failed",problem);
            }
            try { input.close(); } catch (IOException ignored) { }
        } finally {
            synchronized (gate) { pending = false; gate.notifyAll(); }
        }
    }

    @Override public AudioFormat getFormat() { return format; }
    @Override public ByteBuffer read(int requested) {
        if (requested < 1 || stopped()) return null;
        schedule();
        ByteBuffer output;
        synchronized (gate) {
            while (ready.isEmpty() && !eof && failure == null && !stopped()) {
                schedule();
                try { gate.wait(100L); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return null; }
            }
            if (stopped()) return null;
            output = ready.drain(requested);
        }
        schedule();
        return stopped() ? null : output;
    }
    @Override public void close() throws IOException {
        synchronized (gate) {
            if (closed) return;
            closed = true;
            ready.clear();
            gate.notifyAll();
        }
        input.close();
    }
}
