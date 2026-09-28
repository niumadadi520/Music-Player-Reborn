package com.mengsama.mod.mengsamanetmusic.client.audio;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.function.BooleanSupplier;

public class NetMusicAudioStream implements AudioStream {
    private final PcmPrefetchStream pcm;

    public NetMusicAudioStream(URL url) throws IOException, UnsupportedAudioFileException {
        this(url, java.util.Collections.emptyMap());
    }

    public NetMusicAudioStream(URL url, Map<String, String> headers) throws IOException, UnsupportedAudioFileException {
        this(url, headers, 0);
    }

    public NetMusicAudioStream(URL url, Map<String, String> headers, int startSecond) throws IOException, UnsupportedAudioFileException {
        this(getAudioInputStream(url, headers), startSecond, () -> false);
    }

    public NetMusicAudioStream(URL url, Map<String, String> headers, int startSecond, BooleanSupplier cancelled)
            throws IOException, UnsupportedAudioFileException {
        this(getAudioInputStream(url, headers), startSecond, cancelled);
    }

    public NetMusicAudioStream(AudioInputStream audioInputStream) throws IOException, UnsupportedAudioFileException {
        this(audioInputStream, 0);
    }

    public NetMusicAudioStream(AudioInputStream audioInputStream, int startSecond) throws IOException, UnsupportedAudioFileException {
        this(audioInputStream, startSecond, () -> false);
    }

    NetMusicAudioStream(AudioInputStream audioInputStream, int startSecond, BooleanSupplier cancelled)
            throws IOException, UnsupportedAudioFileException {
        BooleanSupplier obsolete = cancelled == null ? () -> false : cancelled;
        AudioInputStream decodedStream = null;
        AudioInputStream pcmStream = null;
        try {
            PcmFormatPlan plan = PcmFormatPlan.forSource(audioInputStream.getFormat());
            final AudioInputStream pcmFinal = withAudioSpiClassLoader(
                    () -> AudioSystem.getAudioInputStream(plan.decoded(), audioInputStream));
            pcmStream = pcmFinal;
            decodedStream = withAudioSpiClassLoader(
                    () -> AudioSystem.getAudioInputStream(plan.playback(), pcmFinal));
            if (startSecond > 0) {
                byte[] seekBuffer = new byte[Math.max(16384, decodedStream.getFormat().getFrameSize())];
                skipPcmSeconds(decodedStream, startSecond, seekBuffer, obsolete);
            }
            try {
                pcm = new PcmPrefetchStream(decodedStream, calculateBufferSize(decodedStream.getFormat(), 0.25f), obsolete);
            } catch (IOException invalidPcm) {
                throw unsupported("Decoder failed while producing the first PCM buffer", invalidPcm);
            }
        } catch (IOException | UnsupportedAudioFileException | RuntimeException e) {
            if (decodedStream != null) {
                closeQuietly(decodedStream);
            } else if (pcmStream != null) {
                closeQuietly(pcmStream);
            } else {
                closeQuietly(audioInputStream);
            }
            throw e;
        }
    }

    private static AudioInputStream getAudioInputStream(URL url, Map<String, String> headers) throws IOException, UnsupportedAudioFileException {
        try {
            return openHttpAudioStream(url, headers);
        } catch (IOException | UnsupportedAudioFileException first) {
            MengSamaNetMusic.LOGGER.warn("Direct audio open failed for {}, trying provider fallback", url, first);
            final AudioInputStream metingStream = tryMetingFallback(url);
            if (metingStream != null) return metingStream;
            throw first;
        }
    }

    @FunctionalInterface
    private interface AudioOperation<T> {
        T run() throws UnsupportedAudioFileException;
    }

    private static <T> T withAudioSpiClassLoader(AudioOperation<T> operation)
            throws UnsupportedAudioFileException {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        ClassLoader spiLoader = NetMusicAudioStream.class.getClassLoader();
        try {
            thread.setContextClassLoader(spiLoader);
            return operation.run();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static AudioInputStream probeAudioStream(InputStream input)
            throws IOException, UnsupportedAudioFileException {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(NetMusicAudioStream.class.getClassLoader());
            return AudioSystem.getAudioInputStream(input);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static UnsupportedAudioFileException unsupported(String message, Throwable cause) {
        UnsupportedAudioFileException failure = new UnsupportedAudioFileException(message + ": " + cause);
        failure.initCause(cause);
        return failure;
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }

     
    private static AudioInputStream openHttpAudioStream(URL original, Map<String, String> playbackHeaders) throws IOException, UnsupportedAudioFileException {
        URL current = original;
        Map<String, String> initialHeaders = requestHeaders(original, playbackHeaders);
        for (int redirect = 0; redirect <= 8; redirect++) {
            if (!"http".equalsIgnoreCase(current.getProtocol()) && !"https".equalsIgnoreCase(current.getProtocol()))
                throw new IOException("Unsupported audio protocol");
            Map<String, String> headers = com.mengsama.mod.mengsamanetmusic.util.AudioRequestHeaders.sanitize(original, current, initialHeaders);
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            headers.forEach(connection::setRequestProperty);
            connection.setRequestProperty("User-Agent", headers.getOrDefault("User-Agent", "Mozilla/5.0"));
            connection.setRequestProperty("Referer", headers.getOrDefault("Referer", refererFor(original)));
            connection.setRequestProperty("Accept", "audio/mpeg,audio/aac,audio/mp4,audio/*;q=0.9,*/*;q=0.5");
             
            connection.setRequestProperty("Accept-Encoding", "identity");
            int code = connection.getResponseCode();
            String contentType = connection.getContentType();
            MengSamaNetMusic.LOGGER.info("[音频链路] response={} contentType={} url={}", code, contentType, current);
            if (code >= 300 && code < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.isBlank()) throw new IOException("Audio redirect has no Location");
                URL next;
                try {
                    next = current.toURI().resolve(location).toURL();
                } catch (Exception invalidRedirect) {
                    throw new IOException("Invalid audio redirect from " + current + " to " + location, invalidRedirect);
                }
                MengSamaNetMusic.LOGGER.info("[音频链路] redirect {} -> {}", current, next);
                current = next;
                continue;
            }
            if (code < 200 || code >= 300) {
                String expirationHint = expirationHint(connection);
                connection.disconnect();
                throw new IOException("Audio HTTP " + code + " from " + current + expirationHint);
            }
            InputStream responseBody = connection.getInputStream();
            String contentEncoding = connection.getContentEncoding();
            if (contentEncoding != null && !contentEncoding.isBlank()
                    && !"identity".equalsIgnoreCase(contentEncoding)) {
                String normalized = contentEncoding.toLowerCase(java.util.Locale.ROOT);
                if (normalized.contains("gzip")) {
                    responseBody = new java.util.zip.GZIPInputStream(responseBody);
                } else if (normalized.contains("deflate")) {
                    responseBody = new java.util.zip.InflaterInputStream(responseBody);
                } else {
                    responseBody.close();
                    connection.disconnect();
                    throw new IOException("Unsupported audio Content-Encoding " + contentEncoding);
                }
            }
            InputStream decodedBody = responseBody;
            InputStream body = new java.io.FilterInputStream(decodedBody) {
                @Override public void close() throws IOException { try { super.close(); } finally { connection.disconnect(); } }
            };
             
             
            byte[] prefix = body.readNBytes(64);
            InputStream replayed = new SequenceInputStream(new ByteArrayInputStream(prefix), body);
            BufferedInputStream sniffed = new BufferedInputStream(replayed, 64 * 1024);
            AudioContainer container = AudioContainer.detect(contentType, prefix);
            MengSamaNetMusic.LOGGER.info("[音频链路] finalUrl={} declaredType={} detected={} bytes={}",
                    current, contentType, container, hexPrefix(prefix));
            if (container == AudioContainer.HTML || container == AudioContainer.JSON) {
                sniffed.close();
                throw new UnexpectedAudioPayloadException(container, contentType);
            }
            try {
                return probeAudioStream(sniffed);
            } catch (IOException | UnsupportedAudioFileException | RuntimeException e) {
                MengSamaNetMusic.LOGGER.error("[音频链路] JavaSound probe failed finalUrl={} declaredType={} detected={} prefix={}",
                        current, contentType, container, hexPrefix(prefix), e);
                sniffed.close();
                throw e;
            }
        }
        throw new IOException("Too many audio redirects for " + original);
    }

    private static String expirationHint(HttpURLConnection connection) {
        StringBuilder hint = new StringBuilder();
        for (String name : new String[]{"X-Amz-Error-Code", "X-Error-Code", "X-Cos-Error-Code"}) {
            String value = connection.getHeaderField(name);
            if (value != null) hint.append(' ').append(value);
        }
        try (InputStream error = connection.getErrorStream()) {
            if (error != null) hint.append(' ').append(new String(error.readNBytes(2048), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException ignored) { }
        String normalized = hint.toString().toLowerCase(java.util.Locale.ROOT);
        return normalized.matches("(?s).*(expiredtoken|request has expired|signature.*expired|url.*expired|token.*expired).*")
                ? " signed-url-expired" : "";
    }

    static Map<String, String> requestHeaders(URL url, Map<String, String> playbackHeaders) {
        Map<String, String> headers = new java.util.HashMap<>();
        String host = url.getHost().toLowerCase(java.util.Locale.ROOT);
        if (com.mengsama.mod.mengsamanetmusic.util.AudioRequestHeaders.isNetEaseApi(url)) {
            headers.putAll(MengSamaNetMusic.NET_EASE_API.getRequestPropertyData());
        }
        if (playbackHeaders != null) headers.putAll(playbackHeaders);
        headers.putIfAbsent("User-Agent", "Mozilla/5.0");
        headers.putIfAbsent("Referer", refererFor(url));
        return com.mengsama.mod.mengsamanetmusic.util.AudioRequestHeaders.sanitize(url, url, headers);
    }

    private static String refererFor(URL url) {
        String host = url.getHost().toLowerCase(java.util.Locale.ROOT);
        if (host.contains("qqmusic") || host.endsWith("qq.com")) return "https://y.qq.com/";
        if (host.contains("163.com") || host.endsWith("music.126.net") || host.contains("netease")) return "https://music.163.com/";
        if (host.endsWith("mzstatic.com") || host.endsWith("apple.com")) return "https://music.apple.com/";
        return url.getProtocol() + "://" + url.getHost() + "/";
    }

    static String hexPrefix(byte[] bytes) {
        final char[] digits = "0123456789abcdef".toCharArray();
        int length = Math.min(bytes.length, 12);
        char[] out = new char[length * 2];
        for (int i = 0; i < length; i++) {
            int value = bytes[i] & 0xff;
            out[i * 2] = digits[value >>> 4];
            out[i * 2 + 1] = digits[value & 0x0f];
        }
        return new String(out);
    }

    static final class UnexpectedAudioPayloadException extends UnsupportedAudioFileException {
        UnexpectedAudioPayloadException(AudioContainer container, String contentType) {
            super("Unexpected audio payload " + container + " Content-Type=" + contentType);
        }
    }

    enum AudioContainer {
        MPEG, AAC_ADTS, MP4_AAC, FLAC, OGG, WAV, HTML, JSON, UNKNOWN;

        static AudioContainer detect(String contentType, byte[] bytes) {
            String type = contentType == null ? "" : contentType.toLowerCase(java.util.Locale.ROOT);
            int offset = textPayloadOffset(bytes);
            if (matches(bytes, offset, "<html") || matches(bytes, offset, "<!doc")
                    || matches(bytes, offset, "<?xml") || matches(bytes, offset, "<head")
                    || matches(bytes, offset, "<body")) return HTML;
            if (offset < bytes.length && (bytes[offset] == '{' || bytes[offset] == '[')) return JSON;
            if (matches(bytes, 0, "ID3") || (bytes.length >= 2 && (bytes[0] & 0xff) == 0xff
                    && ((bytes[1] & 0xe0) == 0xe0))) {
                return bytes.length >= 2 && (bytes[1] & 0xf6) == 0xf0 ? AAC_ADTS : MPEG;
            }
            if (bytes.length >= 12 && matches(bytes, 4, "ftyp")) return MP4_AAC;
            if (matches(bytes, 0, "fLaC")) return FLAC;
            if (matches(bytes, 0, "OggS")) return OGG;
            if (matches(bytes, 0, "RIFF") && bytes.length >= 12 && matches(bytes, 8, "WAVE")) return WAV;
            if (type.contains("mpeg")) return MPEG;
            if (type.contains("aac")) return AAC_ADTS;
            if (type.contains("mp4") || type.contains("m4a")) return MP4_AAC;
            if (type.contains("flac")) return FLAC;
            if (type.contains("ogg")) return OGG;
            if (type.contains("wav")) return WAV;
            if (type.contains("html")) return HTML;
            if (type.contains("json")) return JSON;
            return UNKNOWN;
        }

        private static int textPayloadOffset(byte[] bytes) {
            int offset = bytes.length >= 3 && (bytes[0] & 0xff) == 0xef
                    && (bytes[1] & 0xff) == 0xbb && (bytes[2] & 0xff) == 0xbf ? 3 : 0;
            while (offset < bytes.length && isAsciiWhitespace(bytes[offset])) offset++;
            return offset;
        }

        private static boolean isAsciiWhitespace(byte value) {
            int unsigned = value & 0xff;
            return unsigned == 0x20 || unsigned == 0x09 || unsigned == 0x0a
                    || unsigned == 0x0b || unsigned == 0x0c || unsigned == 0x0d;
        }

        private static boolean matches(byte[] bytes, int offset, String ascii) {
            if (offset < 0 || bytes.length - offset < ascii.length()) return false;
            for (int i = 0; i < ascii.length(); i++) {
                if (Character.toLowerCase((char) bytes[offset + i]) != Character.toLowerCase(ascii.charAt(i))) return false;
            }
            return true;
        }
    }

    private static AudioInputStream getDirectAudioStream(URL url) throws IOException, UnsupportedAudioFileException {
        return openHttpAudioStream(url, java.util.Collections.emptyMap());
    }

    private static AudioInputStream tryMetingFallback(URL originalUrl) {
        try {
            String urlStr = originalUrl.toString();
            if (!urlStr.contains("music.163.com")) return null;

            int idIdx = urlStr.indexOf("id=");
            if (idIdx < 0) return null;
            String sub = urlStr.substring(idIdx + 3);
            int dot = sub.indexOf(".mp3");
            if (dot > 0) sub = sub.substring(0, dot);
            long songId = Long.parseLong(sub);

            MengSamaNetMusic.LOGGER.info("Client Meting fallback: trying for id {}", songId);
            String metingUrl = com.mengsama.mod.mengsamanetmusic.api.MetingApi.getSongUrl(songId);
            if (metingUrl == null || metingUrl.isEmpty()) {
                MengSamaNetMusic.LOGGER.warn("Client Meting fallback: getSongUrl returned null for id {}", songId);
                return null;
            }

            MengSamaNetMusic.LOGGER.info("Client Meting fallback: got URL {}", metingUrl);
            URL metingUrlObj = new URI(metingUrl).toURL();
            return getDirectAudioStream(metingUrlObj);
        } catch (Exception e) {
            MengSamaNetMusic.LOGGER.warn("Client Meting fallback failed: {}", e.getMessage());
            return null;
        }
    }

    private static URL resolveRedirectUrl(URL url) throws IOException {
        if (!java.util.Set.of("http", "https").contains(url.getProtocol())) return url;
        return new URL(com.mengsama.mod.mengsamanetmusic.util.NetWorker.resolveRedirect(url.toExternalForm(), 5,
                MengSamaNetMusic.NET_EASE_API.getRequestPropertyData()));
    }
    private static int calculateBufferSize(AudioFormat format, float seconds) {
        long frames = Math.max(1L, (long)(format.getFrameRate() * seconds));
        long bytes = frames * format.getFrameSize();
        if (bytes > Integer.MAX_VALUE) throw new IllegalArgumentException("PCM buffer size overflow");
        return (int)bytes;
    }

    static long skipPcmSeconds(AudioInputStream stream, int startSecond, byte[] frame) throws IOException {
        return skipPcmSeconds(stream, startSecond, frame, () -> false);
    }

    static long skipPcmSeconds(AudioInputStream stream, int startSecond, byte[] frame,
                               BooleanSupplier cancelled) throws IOException {
        AudioFormat format = stream.getFormat();
        int pcmFrameSize = Math.max(1, format.getFrameSize());
        double frameRate = format.getFrameRate();
        if (!(frameRate > 0.0) || Double.isInfinite(frameRate)) return 0L;
        long framesToSkip = Math.max(0L, Math.round(Math.max(0, startSecond) * frameRate));
        long bytesRemaining;
        try {
            bytesRemaining = Math.multiplyExact(framesToSkip, (long) pcmFrameSize);
        } catch (ArithmeticException overflow) {
            bytesRemaining = Long.MAX_VALUE - Long.MAX_VALUE % pcmFrameSize;
        }
        long skippedBytes = 0L;
        int scratchSize = Math.max(8192, pcmFrameSize);
        scratchSize -= scratchSize % pcmFrameSize;
        byte[] scratch = frame.length >= scratchSize && frame.length % pcmFrameSize == 0
                ? frame : new byte[scratchSize];
        while (bytesRemaining > 0L) {
            if (cancelled != null && cancelled.getAsBoolean()) throw new IOException("Playback request was superseded");
            int wanted = (int) Math.min((long) scratch.length, bytesRemaining);
            wanted -= wanted % pcmFrameSize;
            if (wanted <= 0) wanted = pcmFrameSize;

             
             
             
             
            int read = stream.read(scratch, 0, wanted);
            if (read < 0) break;
             
            if (read == 0) break;
            skippedBytes += read;
            bytesRemaining -= Math.min(bytesRemaining, read);
        }
        return skippedBytes;
    }

    public enum FailureCategory { NOT_LOGGED_IN, EXPIRED, NO_COPYRIGHT, DECODE_FAILED, TIMEOUT, RESOLUTION_FAILED }

    public static FailureCategory classifyFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof UnexpectedAudioPayloadException) return FailureCategory.EXPIRED;
            String message = String.valueOf(current.getMessage()).toLowerCase(java.util.Locale.ROOT);
            if (message.contains("requires cookie") || message.contains("not logged") || message.contains("unauthorized")
                    || message.contains("login required")) return FailureCategory.NOT_LOGGED_IN;
            if (message.contains("copyright") || message.contains("no playable url") || message.contains("no privilege")
                    || message.contains("unavailable in your country")) return FailureCategory.NO_COPYRIGHT;
            if (message.contains("http 403") || message.contains("http 404") || message.contains("http 401")
                    || message.contains("signed-url-expired") || message.contains("expiredtoken")
                    || message.contains("request has expired")) {
                return FailureCategory.EXPIRED;
            }
            if (message.contains("content-encoding") || message.contains("gzip") || message.contains("deflate")
                    || current instanceof UnsupportedAudioFileException || message.contains("decoder")
                    || message.contains("could not get audio input stream")) return FailureCategory.DECODE_FAILED;
            if (current instanceof java.net.SocketTimeoutException) return FailureCategory.TIMEOUT;
            current = current.getCause();
        }
        return FailureCategory.RESOLUTION_FAILED;
    }

    public static boolean shouldRefreshProvider(SongInfo songInfo, Throwable error) {
        return songInfo != null && songInfo.canRefreshProvider()
                && classifyFailure(error) == FailureCategory.EXPIRED;
    }

    public static String userFailureMessage(Throwable error) { return userFailureMessage(classifyFailure(error)); }

    public static String userFailureMessage(FailureCategory category) {
        return switch (category) {
            case NOT_LOGGED_IN -> "音乐播放失败：音源未登录或未授权";
            case EXPIRED -> "音乐播放失败：临时音源已失效，请重试";
            case NO_COPYRIGHT -> "音乐播放失败：歌曲无版权或当前地区不可用";
            case DECODE_FAILED -> "音乐播放失败：音频数据或编码无法解码";
            case TIMEOUT -> "音乐播放失败：连接音源超时，请稍后重试";
            case RESOLUTION_FAILED -> "音乐播放失败：音源解析失败";
        };
    }

    @Override public AudioFormat getFormat() { return pcm.getFormat(); }
    @Override public ByteBuffer read(int size) { return pcm.read(size); }
    @Override public void close() throws IOException { pcm.close(); }
}
