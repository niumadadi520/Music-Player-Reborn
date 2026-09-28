package com.mengsama.mod.mengsamanetmusic.client;

import com.mengsama.mod.mengsamanetmusic.api.CoverUrlUtil;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

 
public final class CoverImageDownloader {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private CoverImageDownloader() {}

    public static byte[] download(String rawUrl) throws IOException {
        String url = CoverUrlUtil.forDisplay(rawUrl);
        if (!CoverUrlUtil.isSupportedRasterUrl(url)) throw new IOException("Unsupported cover URL");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 MengSamaNetMusic/1.0");
            connection.setRequestProperty("Referer", "https://music.163.com/");
            connection.setRequestProperty("Accept", "image/png,image/jpeg,image/gif,image/bmp;q=0.8,*/*;q=0.5");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("HTTP " + status);
            String contentType = connection.getContentType();
            String mediaType = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!mediaType.startsWith("image/") || mediaType.contains("webp") || mediaType.contains("avif")) {
                throw new IOException("Unexpected Content-Type: " + contentType);
            }
            byte[] bytes;
            try (InputStream input = connection.getInputStream()) {
                bytes = readLimited(input);
            }
            if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) throw new IOException("ImageIO rejected cover bytes");
            return bytes;
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        for (int read; (read = input.read(buffer)) >= 0;) {
            total += read;
            if (total > MAX_BYTES) throw new IOException("Cover exceeds " + MAX_BYTES + " bytes");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
