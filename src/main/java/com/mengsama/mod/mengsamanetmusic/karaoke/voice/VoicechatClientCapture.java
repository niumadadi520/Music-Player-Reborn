package com.mengsama.mod.mengsamanetmusic.karaoke.voice;

import com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeNetwork;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

 
final class VoicechatClientCapture implements KaraokeVoiceClient.CaptureBackend {
    private static final AudioFormat FORMAT = new AudioFormat(48_000, 16, 1, true, false);
    private final VoicechatClientApi api;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 5, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), runnable -> {
                Thread thread = new Thread(runnable, "MengSama-Karaoke-Capture");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardOldestPolicy());
    private volatile Capture current;
    private volatile String status = "K歌麦克风已关闭";

    VoicechatClientCapture(VoicechatClientApi api) {
        this.api = api;
        worker.allowCoreThreadTimeOut(true);
    }

    @Override
    public void start(UUID nonce) {
        stop();
        Capture capture = new Capture(nonce);
        current = capture;
        status = "正在打开K歌麦克风";
        worker.execute(() -> capture(capture));
    }

    @Override
    public void stop() {
        Capture previous = current;
        current = null;
        worker.getQueue().clear();
        if (previous != null) previous.stop();
        status = "K歌麦克风已关闭";
    }

    @Override
    public String status() { return status; }

    @Override
    public void tick() {
        Capture capture = current;
        if (capture == null || !active(capture)) return;
        for (int i = 0; i < 5; i++) {
            Encoded frame = capture.frames.poll();
            if (frame == null) break;
            if (active(capture) && System.nanoTime() - frame.created < VoiceAudioMath.MAX_AGE_NANOS) {
                KaraokeNetwork.sendVoice(capture.nonce, frame.opus);
            }
        }
    }

    private boolean active(Capture capture) {
        return !capture.stopped && current == capture && KaraokeVoiceClient.isCurrent(capture.nonce);
    }

    private void capture(Capture capture) {
        OpusEncoder encoder = null;
        try {
            if (!active(capture)) return;
            String preferred = api.getClientConfig().getString("microphone", "");
            SelectedDevice selected = selectDevice(preferred);
            TargetDataLine line = selected.line;
            capture.line = line;
            if (!active(capture)) return;
            line.open(FORMAT, VoiceAudioMath.FRAME_BYTES * 5);
            if (!active(capture)) return;
            encoder = api.createEncoder(OpusEncoderMode.AUDIO);
            line.flush();
            line.start();
            status = selected.usedDefault ? "K歌采集中 · 系统默认输入" : "K歌采集中 · Voice Chat所选输入";
            byte[] raw = new byte[VoiceAudioMath.FRAME_BYTES];
            while (active(capture)) {
                if (line.available() > VoiceAudioMath.FRAME_BYTES * 5) {
                    line.flush();
                    capture.frames.clear();
                    encoder.resetState();
                }
                if (line.available() < raw.length) {
                    Thread.sleep(5);
                    continue;
                }
                int read = 0;
                while (read < raw.length && active(capture)) {
                    int amount = line.read(raw, read, raw.length - read);
                    if (amount <= 0) break;
                    read += amount;
                }
                if (read != raw.length || !active(capture)) continue;
                byte[] encoded = encoder.encode(VoiceAudioMath.fromLittleEndian(raw));
                if (!VoiceAudioMath.validPacket(encoded) || !active(capture)) continue;
                Encoded frame = new Encoded(encoded, System.nanoTime());
                if (!capture.frames.offer(frame)) {
                    capture.frames.poll();
                    capture.frames.offer(frame);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception | LinkageError failure) {
            if (active(capture)) KaraokeVoiceClient.failed(capture.nonce,
                    "K歌麦克风打不开：请检查系统输入设备、麦克风权限及是否被独占");
        } finally {
            capture.stop();
            if (encoder != null) {
                try { encoder.close(); } catch (Exception | LinkageError ignored) { }
            }
        }
    }

    private static SelectedDevice selectDevice(String preferred) throws LineUnavailableException {
        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (preferred != null && !preferred.isBlank()) {
            String normalized = normalize(preferred);
            for (Mixer.Info info : AudioSystem.getMixerInfo()) {
                Mixer mixer = AudioSystem.getMixer(info);
                if (mixer.isLineSupported(lineInfo) && normalize(info.getName()).equals(normalized)) {
                    return new SelectedDevice((TargetDataLine) mixer.getLine(lineInfo), false);
                }
            }
        }
        return new SelectedDevice((TargetDataLine) AudioSystem.getLine(lineInfo), true);
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace("openal soft on ", "").strip();
    }

    private record SelectedDevice(TargetDataLine line, boolean usedDefault) {}
    private record Encoded(byte[] opus, long created) {}

    private static final class Capture {
        final UUID nonce;
        final ArrayBlockingQueue<Encoded> frames = new ArrayBlockingQueue<>(5);
        volatile boolean stopped;
        volatile TargetDataLine line;

        Capture(UUID nonce) { this.nonce = nonce; }

        void stop() {
            stopped = true;
            frames.clear();
            TargetDataLine currentLine = line;
            if (currentLine != null) {
                try { currentLine.stop(); } catch (Exception ignored) { }
                try { currentLine.flush(); } catch (Exception ignored) { }
                try { currentLine.close(); } catch (Exception ignored) { }
            }
        }
    }
}
