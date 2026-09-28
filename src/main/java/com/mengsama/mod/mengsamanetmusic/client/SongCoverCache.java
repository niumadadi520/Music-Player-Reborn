package com.mengsama.mod.mengsamanetmusic.client;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

 
public final class SongCoverCache {
    private static final int MAX_READY_ENTRIES = 256;
     
    private static final Map<String, ResourceLocation> READY =
            java.util.Collections.synchronizedMap(new LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ResourceLocation> eldest) {
                    if (size() <= MAX_READY_ENTRIES) return false;
                    ResourceLocation id = eldest.getValue();
                    if (id != null) {
                        try {
                            Minecraft.getInstance().getTextureManager().release(id);
                        } catch (Throwable ignored) {}
                    }
                    return true;
                }
            });
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> RETRY_AFTER = new ConcurrentHashMap<>();
    private static final long FAILURE_RETRY_MS = 15_000L;

    private SongCoverCache() {}

    public static ResourceLocation getOrRequest(String rawUrl) {
        String url = com.mengsama.mod.mengsamanetmusic.api.CoverUrlUtil.forDisplay(rawUrl);
        if (url.isEmpty()) return null;
        ResourceLocation ready = READY.get(url);
        if (ready != null) return ready;
        Long retryAt = RETRY_AFTER.get(url);
        if (retryAt != null && System.currentTimeMillis() < retryAt) return null;
        RETRY_AFTER.remove(url);
        if (LOADING.add(url)) {
            java.util.concurrent.CompletableFuture.runAsync(() -> download(url), Util.backgroundExecutor());
        }
        return null;
    }

    private static void download(String url) {
        try {
            byte[] bytes = CoverImageDownloader.download(url);
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            Minecraft.getInstance().execute(() -> {
                try {
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MengSamaNetMusic.MOD_ID,
                            "cover/" + Integer.toUnsignedString(url.hashCode(), 36));
                    Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
                    READY.put(url, id);
                    RETRY_AFTER.remove(url);
                } catch (Throwable error) {
                    image.close();
                    RETRY_AFTER.put(url, System.currentTimeMillis() + FAILURE_RETRY_MS);
                    MengSamaNetMusic.LOGGER.debug("Cover registration failed: {}", url, error);
                } finally { LOADING.remove(url); }
            });
        } catch (Throwable error) {
            LOADING.remove(url);
            RETRY_AFTER.put(url, System.currentTimeMillis() + FAILURE_RETRY_MS);
            MengSamaNetMusic.LOGGER.debug("Cover download failed: {}", url, error);
        }
    }
}
