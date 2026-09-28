package com.mengsama.mod.mengsamanetmusic.hud;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.cache.MusicAssetCache;
import com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicPlayback;
import com.mengsama.mod.mengsamanetmusic.client.lyric.ClientLyricStore;
import com.mengsama.mod.mengsamanetmusic.config.MusicHudConfig;
import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerSkin;
import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout;
import com.mengsama.mod.mengsamanetmusic.util.SongAddress;
import com.mengsama.mod.mengsamanetmusic.util.PlaybackTime;
import com.mengsama.mod.mengsamanetmusic.api.ArtworkMetadata;
import com.mengsama.mod.mengsamanetmusic.client.artwork.ArtworkImages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

 
public final class MusicInfoHud {
    private static final ResourceLocation DEFAULT_TEXTURE = ResourceLocation.fromNamespaceAndPath(MengSamaNetMusic.MOD_ID, "textures/gui/default.png");
    private static final Map<String, SongInfo> DEVICE_INFO = new LinkedHashMap<>();
    private static final Map<String, ResourceLocation> ARTWORK_TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, ResourceLocation> URL_TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> ARTWORK_LOADING = new ConcurrentHashMap<>();
    private static final Map<String, Long> FAILED_URLS = new ConcurrentHashMap<>();
    private static final long FAILED_URL_RETRY_MS = 30_000L;
    private static final TwoLineLyricAnimator LYRIC_ANIMATOR = new TwoLineLyricAnimator();
    private static String targetId;
    private static SongInfo info;
    private static ResourceLocation icon = DEFAULT_TEXTURE;
    private static Long id;

    private MusicInfoHud() {}

    public static void render(@NotNull GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!isMusicHudEnabled() || mc.options.hideGui || info == null || targetId == null) return;
        int tick = ClientMusicPlayback.getTick(targetId);
        if (tick < 0) {
            onDeviceStopped(targetId);
            if (info == null || targetId == null || (tick = ClientMusicPlayback.getTick(targetId)) < 0) return;
        }
        SongInfo playingInfo = ClientMusicPlayback.getSongInfo(targetId);
        if (playingInfo != null && !playingInfo.identityKey().equals(info.identityKey())) {
            select(targetId, playingInfo);
        }
        renderContents(graphics, info, tick, false);
    }

    public static void renderPreview(@NotNull GuiGraphics graphics) {
        Preview preview = preview();
        renderContents(graphics, preview.song(), preview.tick(), true);
    }

     
    public static RosewoodHudLayout.Viewport previewBounds(int screenWidth, int screenHeight) {
        Preview preview = preview();
        MusicHudConfig.Data config = MusicHudConfig.get();
        Prepared prepared = prepare(Minecraft.getInstance().font, config, preview.song(), preview.tick(), true, screenWidth);
        return RosewoodHudLayout.place(prepared.layout(), config.x, config.y, config.scale, screenWidth, screenHeight);
    }

    public static RosewoodHudLayout.Viewport previewBounds() {
        Minecraft mc = Minecraft.getInstance();
        return previewBounds(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    private record Preview(SongInfo song, int tick) {}
    private static Preview preview() {
        SongInfo song = info;
        int tick = targetId == null ? 0 : Math.max(0, ClientMusicPlayback.getTick(targetId));
        if (song == null) {
            song = new SongInfo();
            song.songName = "正在播放的歌曲（实时预览）";
            song.songTime = 245;
            song.artists.add("歌手名称");
            song.source = "netease";
            tick = 73 * 20;
        }
        return new Preview(song, tick);
    }

    private record Prepared(String title, String artist, String time, TwoLineLyricAnimator.Frame lyrics,
                            RosewoodHudLayout.Layout layout) {}

    private static Prepared prepare(Font font, MusicHudConfig.Data config, SongInfo song, int tick,
                                    boolean preview, int screenWidth) {
        String title = song.transName == null || song.transName.isBlank() ? safe(song.songName)
                : safe(song.songName) + " (" + song.transName + ")";
        String artist = song.artists == null ? "" : String.join(" / ", song.artists);
        String time = PlaybackTime.clock(Math.max(0, tick / 20)) + "/"
                + PlaybackTime.clock(Math.max(0, song.songTime));
        TwoLineLyricAnimator.Frame lyrics = config.showLyrics
                ? (preview ? TwoLineLyricAnimator.Frame.preview() : lyricFrame(tick))
                : TwoLineLyricAnimator.Frame.empty();
        var elements = new RosewoodHudLayout.Elements(config.showCover, config.showTitle && !title.isBlank(),
                config.showArtist && !artist.isBlank(), config.showProgress && song.songTime > 0,
                config.showLyrics && lyrics.hasText());
        float scale = Float.isFinite(config.scale) ? Math.max(0.5F, Math.min(2F, config.scale)) : 1F;
        int availableWidth = Math.max(0, (int)Math.floor(screenWidth / scale));
        int lyricWidth = Math.max(font.width(lyrics.current()), Math.max(font.width(lyrics.next()), font.width(lyrics.outgoing())));
        var layout = RosewoodHudLayout.calculate(elements, font.lineHeight, font.width(title), font.width(artist),
                font.width(time), lyricWidth, availableWidth);
        return new Prepared(title, artist, time, lyrics, layout);
    }

    private static void renderContents(GuiGraphics graphics, SongInfo song, int tick, boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        MusicHudConfig.Data config = MusicHudConfig.get();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        Prepared prepared = prepare(font, config, song, tick, preview, screenWidth);
        var layout = prepared.layout();
        var viewport = RosewoodHudLayout.place(layout, config.x, config.y, config.scale, screenWidth, screenHeight);
        if (layout.empty() || viewport.width() <= 0 || viewport.height() <= 0) return;
        float opacity = Float.isFinite(config.opacity) ? Math.max(0.15F, Math.min(1F, config.opacity)) : 0.85F;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(viewport.x(), viewport.y(), 0);
        pose.scale(viewport.scale(), viewport.scale(), 1F);
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.x() + viewport.width(), viewport.y() + viewport.height());
        try (var state = new RosewoodHudSkin.RenderState(graphics)) {
            state.opacity(opacity * ((config.backgroundColor >>> 24) / 255F));
            RosewoodHudSkin.render(graphics, layout);
            state.opacity(opacity);
            var cover = layout.cover();
            if (!cover.empty()) graphics.blit(Objects.requireNonNullElse(icon, DEFAULT_TEXTURE),
                    cover.x(), cover.y(), cover.width(), cover.height(), 0F, 0F, 40, 40, 40, 40);
            var track = layout.track();
            if (!track.empty()) MusicPlayerSkin.renderProgress(graphics,
                    new RosewoodPlayerLayout.Rect(track.x(), track.y(), track.width(), track.height()),
                    Math.max(0F, Math.min(1F, tick / 20F / Math.max(1, song.songTime))));
            state.opacity(1F);
            int primary = withOpacity(config.textColor == 0xFF45302F ? MusicPlayerSkin.primary() : config.textColor, opacity);
            int secondary = withOpacity(config.secondaryTextColor == 0xFF795D54 ? MusicPlayerSkin.secondary() : config.secondaryTextColor, opacity);
            drawText(graphics, font, prepared.title(), layout.title(), primary);
            drawText(graphics, font, prepared.artist(), layout.artist(), secondary);
            drawText(graphics, font, prepared.time(), layout.time(), secondary);
            var clip = layout.lyricClip();
            if (!clip.empty()) {
                 
                graphics.enableScissor(viewport.x() + (int)Math.floor(clip.x() * viewport.scale()),
                        viewport.y() + (int)Math.floor(clip.y() * viewport.scale()),
                        viewport.x() + (int)Math.ceil(clip.right() * viewport.scale()),
                        viewport.y() + (int)Math.ceil(clip.bottom() * viewport.scale()));
                try {
                    for (TwoLineLyricAnimator.Row row : prepared.lyrics().rows()) {
                        int color = withOpacity(primary, row.opacity());
                         
                        if ((color >>> 24) < 4) continue;
                        pose.pushPose();
                        try {
                            pose.translate(0, row.offsetRows() * layout.lyricRowHeight(), 0);
                            graphics.drawString(font, trim(font, row.text(), clip.width()), clip.x(), clip.y(), color, false);
                        } finally { pose.popPose(); }
                    }
                } finally { graphics.disableScissor(); }
            }
        } finally {
            graphics.disableScissor();
            pose.popPose();
        }
    }

    private static void drawText(GuiGraphics graphics, Font font, String text, RosewoodHudLayout.Rect area, int color) {
        if (!area.empty() && (color >>> 24) >= 4) {
            graphics.drawString(font, trim(font, text, area.width()), area.x(), area.y(), color, false);
        }
    }

    private static TwoLineLyricAnimator.Frame lyricFrame(int tick) {
        if (targetId == null || info == null) return TwoLineLyricAnimator.Frame.empty();
        ClientLyricStore.Snapshot snapshot = ClientLyricStore.bind(targetId, info);
        Minecraft mc = Minecraft.getInstance();
        return LYRIC_ANIMATOR.frame(targetId, snapshot.identity(), snapshot.data(), Math.max(0, tick) * 50L,
                System.nanoTime(), mc.isPaused() || ClientMusicPlayback.isPaused(targetId));
    }

    private static String trim(Font font, String text, int maxWidth) {
        text = safe(text);
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        int ellipsisWidth = font.width("…");
        if (ellipsisWidth > maxWidth) return "";
        return font.plainSubstrByWidth(text, maxWidth - ellipsisWidth) + "…";
    }

    private static String safe(String value) { return value == null ? "" : value; }
    private static int withOpacity(int color, float opacity) {
        return (color & 0x00FFFFFF) | (Math.round(((color >>> 24) & 255) * opacity) << 24);
    }

     
    public static synchronized void onMaidDevicePlaying(String deviceId) {
        DEVICE_INFO.remove(deviceId);
         
         
        DEVICE_INFO.keySet().removeIf(com.mengsama.mod.mengsamanetmusic.compat.PlaybackTargetId::isMaidTarget);
        clearInfo();
    }

    public static synchronized void onDevicePlaying(String deviceId, SongInfo song) {
        if (com.mengsama.mod.mengsamanetmusic.compat.PlaybackTargetId.isMaidTarget(deviceId)) {
            onMaidDevicePlaying(deviceId);
            return;
        }
        DEVICE_INFO.remove(deviceId);
        DEVICE_INFO.put(deviceId, song.clone());
        select(deviceId, song);
    }

    public static synchronized void onDeviceStopped(String deviceId) {
        if (ClientMusicPlayback.isActive(deviceId)) return;
        DEVICE_INFO.remove(deviceId);
        if (!Objects.equals(targetId, deviceId)) return;
        String fallback = null;
        for (String candidate : DEVICE_INFO.keySet()) {
            if (!com.mengsama.mod.mengsamanetmusic.compat.PlaybackTargetId.isMaidTarget(candidate)
                    && ClientMusicPlayback.isActive(candidate)) fallback = candidate;
        }
        if (fallback == null) clearInfo(); else select(fallback, DEVICE_INFO.get(fallback));
    }

    private static void select(String deviceId, SongInfo song) {
        targetId = deviceId;
        info = song.clone();
        info.normalizeIdentity();
        loadAssets(info);
    }

    private static void loadAssets(SongInfo song) {
        icon = DEFAULT_TEXTURE;
        String identity = song.identityKey();
        ClientLyricStore.bind(targetId, song);
        if (!identity.isBlank()) icon = ARTWORK_TEXTURES.getOrDefault(identity, DEFAULT_TEXTURE);

        if ("qq".equals(song.source)) {
            id = null;
            String artworkUrl = !safe(song.coverUrl).isBlank() ? song.coverUrl : song.picUrl;
            if (!safe(song.albumMid).isBlank()) {
                loadProviderArtwork(identity, artworkUrl, song.albumMid);
            } else if (!safe(song.providerId).isBlank()) {
                 
                 
                com.mengsama.mod.mengsamanetmusic.api.QqMusicApi.fetchSongDetail(song.providerId)
                        .thenAccept(detail -> {
                            if (detail == null || safe(detail.albumMid).isBlank()) {
                                if (!safe(artworkUrl).isBlank()) loadProviderArtwork(identity, artworkUrl, "");
                                return;
                            }
                            synchronized (MusicInfoHud.class) {
                                if (info != null && identity.equals(info.identityKey())) {
                                    info.albumMid = detail.albumMid;
                                    info.coverUrl = detail.coverUrl;
                                    info.picUrl = detail.picUrl;
                                }
                            }
                            loadProviderArtwork(identity, detail.coverUrl, detail.albumMid);
                        }).exceptionally(failure -> {
                            if (!safe(artworkUrl).isBlank()) loadProviderArtwork(identity, artworkUrl, "");
                            return null;
                        });
            } else if (!safe(artworkUrl).isBlank()) {
                loadProviderArtwork(identity, artworkUrl, "");
            }
            return;
        }
        long songId = song.songId;
        if (songId == 0) try { songId = SongAddress.requireId(song.songUrl); } catch (Exception ignored) {}
        id = songId > 0 ? songId : null;
        if (id == null) {
            loadProviderArtwork(identity, song.picUrl);
            return;
        }
        String artworkKey = "netease:" + id;
        ResourceLocation ready = ARTWORK_TEXTURES.get(artworkKey);
        if (ready != null) { icon = ready; return; }
        final long requestedId = id;
        String knownCover = !safe(song.coverUrl).isBlank() ? song.coverUrl : song.picUrl;
        MusicAssetCache.instance().requestCover(requestedId, () -> {
            try { return ArtworkMetadata.firstAlbum(MengSamaNetMusic.NET_EASE_API.song(requestedId)).toString(); }
            catch (Exception failure) {
                if (!safe(knownCover).isBlank()) return knownCover;
                throw new java.util.concurrent.CompletionException(failure);
            }
        }).thenAcceptAsync(path -> registerTexture(artworkKey, path),
                com.mengsama.mod.mengsamanetmusic.util.AsyncIoExecutor.executor()).exceptionally(failure -> null);
    }

    private static void loadProviderArtwork(String identity, String artworkUrl) {
        loadProviderArtwork(identity, artworkUrl, "");
    }

    private static void loadProviderArtwork(String identity, String artworkUrl, String albumMid) {
        if (identity == null || identity.isBlank()) return;
        java.util.List<String> candidates = new java.util.ArrayList<>();
         
        if (albumMid != null && !albumMid.isBlank()) {
            candidates.addAll(com.mengsama.mod.mengsamanetmusic.api.QqMusicUtils.buildAlbumCoverUrls(albumMid));
        }
        if (artworkUrl != null && !artworkUrl.isBlank() && !candidates.contains(artworkUrl)) candidates.add(artworkUrl);
        if (candidates.isEmpty()) return;
        for (String candidate : candidates) {
            ResourceLocation cached = URL_TEXTURES.get(candidate);
            if (cached != null) {
                ARTWORK_TEXTURES.put(identity, cached);
                if (info != null && identity.equals(info.identityKey())) icon = cached;
                return;
            }
        }
        if (ARTWORK_LOADING.putIfAbsent(identity, Boolean.TRUE) != null) return;
        Thread thread = new Thread(() -> {
            try {
                DownloadedArtwork downloaded = downloadArtwork(candidates);
                Minecraft.getInstance().execute(() -> {
                    try {
                        ResourceLocation location = textureLocationForUrl(downloaded.url);
                        Minecraft.getInstance().getTextureManager().register(location, downloaded.texture);
                        URL_TEXTURES.put(downloaded.url, location);
                        FAILED_URLS.remove(downloaded.url);
                        ARTWORK_TEXTURES.put(identity, location);
                        if (info != null && identity.equals(info.identityKey())) icon = location;
                    } finally {
                        ARTWORK_LOADING.remove(identity);
                    }
                });
            } catch (Exception e) {
                ARTWORK_LOADING.remove(identity);
                MengSamaNetMusic.LOGGER.debug("Artwork download failed for {}: {}", identity, e.getMessage());
            }
        }, "MengSama-HUD-artwork");
        thread.setDaemon(true);
        thread.start();
    }

    private static DownloadedArtwork downloadArtwork(java.util.List<String> artworkUrls) throws Exception {
        Exception lastFailure = null;
        for (String artworkUrl : artworkUrls) {
            Long failedAt = FAILED_URLS.get(artworkUrl);
            if (failedAt != null && System.currentTimeMillis() - failedAt < FAILED_URL_RETRY_MS) continue;
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    return new DownloadedArtwork(artworkUrl,
                            ArtworkImages.download(new URL(artworkUrl), isQqArtwork(artworkUrl)));
                } catch (Exception e) {
                    lastFailure = e;
                    if (attempt < 2) try { Thread.sleep(250L); } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw interrupted;
                    }
                }
            }
             
            FAILED_URLS.put(artworkUrl, System.currentTimeMillis());
        }
        throw lastFailure == null ? new IllegalStateException("Artwork download failed") : lastFailure;
    }

    private record DownloadedArtwork(String url,
            net.minecraft.client.renderer.texture.AbstractTexture texture) {}

    private static boolean isQqArtwork(String url) {
        return url.contains("gtimg.cn") || url.contains("qq.com");
    }

    private static void registerTexture(String identity, java.nio.file.Path image) {
        try {
            var texture = ArtworkImages.file(image);
            Minecraft.getInstance().execute(() -> {
                ResourceLocation location = textureLocation(identity);
                Minecraft.getInstance().getTextureManager().register(location, texture);
                ARTWORK_TEXTURES.put(identity, location);
                if (info != null && identity.equals(info.identityKey())) icon = location;
            });
        } catch (Exception ignored) {}
    }

    private static ResourceLocation textureLocation(String identity) {
        UUID uuid = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        return ResourceLocation.fromNamespaceAndPath(MengSamaNetMusic.MOD_ID, "hud_cover/" + uuid.toString().replace("-", ""));
    }

    private static ResourceLocation textureLocationForUrl(String url) {
        UUID uuid = UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
        return ResourceLocation.fromNamespaceAndPath(MengSamaNetMusic.MOD_ID, "hud_cover_url/" + uuid.toString().replace("-", ""));
    }

    public static SongInfo getInfo() { return info; }
    public static String getTargetId() { return targetId; }
    public static void setPos(int x, int y) { MusicHudConfig.get().x = x; MusicHudConfig.get().y = y; }
    public static synchronized void clearTarget(String deviceId) {
        DEVICE_INFO.remove(deviceId);
        if (Objects.equals(targetId, deviceId)) clearInfo();
    }

    public static synchronized void clearInfo() {
        if (targetId != null) ClientLyricStore.clear(targetId);
        targetId = null; info = null; icon = DEFAULT_TEXTURE; id = null;
        LYRIC_ANIMATOR.reset();
    }

    public static void setInfoFromPacket(String deviceId, String songName, int seconds, String rawUrl, long songId) {
        SongInfo song = new SongInfo(rawUrl, songName, seconds);
        song.songId = songId;
        song.source = SongInfo.detectSource(rawUrl);
        onDevicePlaying(deviceId, song);
    }

    public static void setInfoFromPacket(String deviceId, SongInfo song) { onDevicePlaying(deviceId, song); }

    private static boolean isMusicHudEnabled() {
        return true;
    }
}
