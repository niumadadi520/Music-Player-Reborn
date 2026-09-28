package com.mengsama.mod.mengsamanetmusic.cache;

import java.io.*;
import java.net.*;
import java.nio.file.*;

 
final class CacheTransfer {
    static void fetch(String address, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        if (Files.isRegularFile(destination)) return;
        URL url = new URL(address);
        if (!java.util.Set.of("https", "http").contains(url.getProtocol())) throw new IOException("Unsupported cache source");
        var connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        Path staging = null;
        try {
            int response = connection.getResponseCode();
            if (response / 100 != 2) throw new IOException("Cache HTTP status " + response);
            long expected = connection.getContentLengthLong();
            long maximum = 16L << 20;
            if (expected > maximum) throw new IOException("Cache size limit");
            staging = Files.createTempFile(destination.getParent(), "transfer-", ".tmp");
            long count;
            try (var input = connection.getInputStream(); var output = Files.newOutputStream(staging)) {
                count = copy(input, output, maximum);
            }
            if (count == 0 || expected >= 0 && expected != count) throw new IOException("Incomplete cache response");
            validateImage(staging);
            try { Files.move(staging, destination); }
            catch (FileAlreadyExistsException anotherWriterWon) {   }
        } finally {
            connection.disconnect();
            if (staging != null) Files.deleteIfExists(staging);
        }
    }
    static void validateImage(Path file) throws IOException {
        try (var input = javax.imageio.ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) throw new IOException("Unreadable cover");
            var readers = javax.imageio.ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Cache response is not an image");
            var reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 16_777_216L)
                    throw new IOException("Cover dimensions exceed limit");
            } finally { reader.dispose(); }
        }
    }
    static long copy(InputStream input, OutputStream output, long maximum) throws IOException {
        byte[] block = new byte[16 * 1024];
        long consumed = 0;
        for (;;) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cache transfer cancelled");
            int bytes = input.read(block);
            if (bytes < 0) return consumed;
            if (bytes == 0) continue;
            consumed += bytes;
            if (consumed > maximum) throw new IOException("Cache size limit");
            output.write(block, 0, bytes);
        }
    }
}
