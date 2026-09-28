package com.mengsama.mod.mengsamanetmusic.client.artwork;

import com.mengsama.mod.mengsamanetmusic.util.NetWorker;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

 
public final class ArtworkImages {
    private ArtworkImages() {}
    public static DynamicTexture download(URL source, boolean qq) throws IOException {
        return texture(fetch(source, qq));
    }
    public static DynamicTexture file(Path source) throws IOException {
        try (InputStream input = Files.newInputStream(source)) { return texture(bounded(input)); }
    }
    public static byte[] fetch(URL source, boolean qq) throws IOException {
        URI target;
        try { target = source.toURI(); } catch (URISyntaxException bad) { throw new IOException("Invalid artwork address", bad); }
        Set<URI> visited = new HashSet<>();
        for (int remaining = 6; remaining > 0; remaining--) {
            if (!valid(target) || !visited.add(target)) throw new IOException("Invalid or cyclic artwork redirect");
            HttpURLConnection request = (HttpURLConnection)target.toURL().openConnection(NetWorker.getProxyFromConfig());
            try {
                request.setInstanceFollowRedirects(false);
                request.setConnectTimeout(10000); request.setReadTimeout(15000);
                request.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36");
                request.setRequestProperty("Accept", "image/png,image/jpeg,image/gif,image/bmp,*/*;q=0.5");
                request.setRequestProperty("Accept-Encoding", "identity");
                if (qq) request.setRequestProperty("Referer", "https://y.qq.com/");
                int status = request.getResponseCode();
                if (Set.of(301, 302, 303, 307, 308).contains(status)) {
                    String next = request.getHeaderField("Location");
                    if (next == null || next.isBlank()) throw new IOException("Artwork redirect omitted Location");
                    URI resolved;
                    try { resolved = target.resolve(next); } catch (IllegalArgumentException malformed) { throw new IOException("Invalid artwork redirect", malformed); }
                    if ("https".equalsIgnoreCase(target.getScheme()) && !"https".equalsIgnoreCase(resolved.getScheme())) throw new IOException("Artwork TLS downgrade");
                    target = resolved; continue;
                }
                if (status / 100 != 2) throw new IOException("Artwork HTTP status " + status);
                if (request.getContentLengthLong() > 16L * 1024 * 1024) throw new IOException("Artwork byte limit");
                try (InputStream input = request.getInputStream()) { return bounded(input); }
            } finally { request.disconnect(); }
        }
        throw new IOException("Artwork redirect limit");
    }
    private static boolean valid(URI uri) {
        return uri.getHost() != null && uri.getUserInfo() == null &&
                ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
    }
    private static byte[] bounded(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(16 * 1024 * 1024 + 1);
        if (bytes.length > 16 * 1024 * 1024) throw new IOException("Artwork byte limit");
        return bytes;
    }
    private static DynamicTexture texture(byte[] bytes) throws IOException {
        var pixels = ArtworkPixels.decode(bytes);
        ByteArrayOutputStream normalized = new ByteArrayOutputStream();
        if (!ImageIO.write(pixels, "png", normalized)) throw new IOException("PNG encoder unavailable");
        return new DynamicTexture(NativeImage.read(new ByteArrayInputStream(normalized.toByteArray())));
    }
}
