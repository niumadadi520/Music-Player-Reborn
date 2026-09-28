package com.mengsama.mod.mengsamanetmusic.client;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.config.MusicPlayerUiConfig;
import com.mengsama.mod.mengsamanetmusic.util.AsyncIoExecutor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.zip.CRC32;

 
@OnlyIn(Dist.CLIENT)
public final class MusicPlayerBackground {
    public static final Path DIRECTORY = java.util.Optional.ofNullable(FMLPaths.CONFIGDIR.get())
            .orElse(java.nio.file.Paths.get("config"))
            .resolve("mengsamanetmusic").resolve("ui").resolve("backgrounds");
    private static final String[] CANDIDATES = {
            "background.png", "background.jpg", "background.jpeg", "background.bmp", "background.gif"
    };
    private static final ResourceLocation LOCATION =
            new ResourceLocation(MengSamaNetMusic.MOD_ID, "local_music_player_background");
    private static final AtomicLong REQUEST = new AtomicLong();

    private static DynamicTexture texture;
    private static GifAnimation animation;
    private static int uploadedFrame;
    private static int width;
    private static int height;
    private static long animationStartNanos;
    private static long generation;
    private static long pixelChecksum;
    private static Path loadedPath;
    private static final long FAILURE_RETRY_NANOS = java.util.concurrent.TimeUnit.MINUTES.toNanos(1);
    private static volatile boolean loading;
    private static volatile long retryAfterNanos;
    private static volatile String status = "等待加载";
    private static Crop crop;

    private MusicPlayerBackground() {}

     
    public static void reload() {
        long request = REQUEST.incrementAndGet();
        loading = true;
        retryAfterNanos = 0;
        status = "正在解码";
        AsyncIoExecutor.supplyAsync(() -> decode(request))
                .whenComplete((decoded, error) -> Minecraft.getInstance().execute(() -> {
                    if (request != REQUEST.get()) return;
                    if (error != null) status = "后台任务繁忙，稍后重试";
                    install(request, decoded);
                }));
    }

    private static Decoded decode(long request) {
        try {
            Files.createDirectories(DIRECTORY);
            Path selected = findBackground();
            if (selected == null) return new Decoded(null, null, null, 0);
            if (selected.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gif")) {
                GifAnimation gif = GifAnimation.read(selected);
                return new Decoded(selected, gif, null, checksum(gif.frame(0)));
            }
            BufferedImage image = BackgroundImageDecoder.read(selected);
            if (image.getWidth() < 1 || image.getHeight() < 1
                    || image.getWidth() > GifAnimation.MAX_WIDTH || image.getHeight() > GifAnimation.MAX_HEIGHT)
                throw new IllegalArgumentException("Background dimensions out of range: " + image.getWidth() + "x" + image.getHeight());
            int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            return new Decoded(selected, null, new StaticImage(image.getWidth(), image.getHeight(), pixels), checksum(pixels));
        } catch (Exception ex) {
            status = "解码失败：" + conciseReason(ex);
            MengSamaNetMusic.LOGGER.error("[背景阶段] decode failed type={} reason={}",
                    ex.getClass().getSimpleName(), conciseReason(ex));
            return null;
        }
    }

    private static void install(long request, Decoded decoded) {
        if (request != REQUEST.get()) return;
        loading = false;
        if (decoded == null) {
            retryAfterNanos = retryDeadline(System.nanoTime());
            return;  
        }
        closeTexture();
        if (decoded.path == null) {
            status = "未配置图片，使用背景色";
             
            retryAfterNanos = retryDeadline(System.nanoTime());
            MengSamaNetMusic.LOGGER.info("[背景阶段] no configured image; using fallback color");
            return;
        }
        retryAfterNanos = 0;
        animation = decoded.animation;
        width = animation == null ? decoded.image.width : animation.width();
        height = animation == null ? decoded.image.height : animation.height();
        int[] first = animation == null ? decoded.image.pixels : animation.frame(0);
        NativeImage nativeImage = toNativeImage(width, height, first);
        texture = new DynamicTexture(nativeImage);
        Minecraft.getInstance().getTextureManager().register(LOCATION, texture);
        uploadedFrame = 0;
        animationStartNanos = System.nanoTime();
        loadedPath = decoded.path;
        pixelChecksum = decoded.checksum;
        generation++;
        status = "已应用 " + loadedPath.getFileName() + " · " + width + "×" + height;
        MengSamaNetMusic.LOGGER.info("[背景阶段] applied file={} size={}x{} frames={} generation={} crc32={}",
                loadedPath.getFileName(), width, height, animation == null ? 1 : animation.frameCount(),
                generation, Long.toHexString(pixelChecksum));
    }

    private static Path findBackground() throws Exception {
        String configured = MusicPlayerUiConfig.get().backgroundFile;
        if (configured != null && !configured.isBlank()) {
            Path selected = DIRECTORY.resolve(configured).normalize();
            if (selected.getParent().equals(DIRECTORY.normalize()) && Files.isRegularFile(selected) && isSupportedImage(selected)) return selected;
            throw new IllegalArgumentException("配置的背景文件不存在或格式不支持: " + configured);
        }
        for (String name : CANDIDATES) {
            Path candidate = DIRECTORY.resolve(name);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        try (Stream<Path> files = Files.list(DIRECTORY)) {
            return files.filter(Files::isRegularFile).filter(MusicPlayerBackground::isSupportedImage)
                    .sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
                    .findFirst().orElse(null);
        }
    }

    private static boolean isSupportedImage(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".bmp") || name.endsWith(".gif");
    }

    private static NativeImage toNativeImage(int width, int height, int[] pixels) {
        NativeImage image = new NativeImage(width, height, true);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int argb = pixels[y * width + x];
            int abgr = (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >>> 16) | ((argb & 0x000000FF) << 16);
            image.setPixelRGBA(x, y, abgr);
        }
        return image;
    }

    private static void uploadFrame(int index) {
        if (texture == null || animation == null || index == uploadedFrame) return;
        NativeImage pixels = texture.getPixels();
        if (pixels == null) return;
        int[] frame = animation.frame(index);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int argb = frame[y * width + x];
            pixels.setPixelRGBA(x, y, (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >>> 16) | ((argb & 255) << 16));
        }
        texture.upload();
        uploadedFrame = index;
    }

    private static long checksum(int[] pixels) {
        CRC32 crc = new CRC32();
        for (int pixel : pixels) {
            crc.update(pixel); crc.update(pixel >>> 8); crc.update(pixel >>> 16); crc.update(pixel >>> 24);
        }
        return crc.getValue();
    }

     
    public static void renderCover(GuiGraphics graphics, int x, int y, int targetWidth, int targetHeight, int fallbackColor) {
        if (texture == null) {
            graphics.fill(x, y, x + targetWidth, y + targetHeight, fallbackColor);
            if (!loading && retryAllowed(System.nanoTime(), retryAfterNanos)) reload();
            return;
        }
        if (animation != null) {
            long elapsedMs = Math.max(0, (System.nanoTime() - animationStartNanos) / 1_000_000L);
            uploadFrame(animation.frameAt(elapsedMs));
        }
        Crop currentCrop = crop;
        if (currentCrop == null || !currentCrop.matches(width, height, targetWidth, targetHeight)) {
            currentCrop = calculateCrop(width, height, targetWidth, targetHeight);
            crop = currentCrop;
        }
        graphics.blit(LOCATION, x, y, targetWidth, targetHeight, (float) currentCrop.x, (float) currentCrop.y,
                currentCrop.width, currentCrop.height, width, height);
    }

     
    public static void close() {
        REQUEST.incrementAndGet();
        loading = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) minecraft.execute(MusicPlayerBackground::closeTexture); else closeTexture();
    }

    private static void closeTexture() {
        if (texture != null) Minecraft.getInstance().getTextureManager().release(LOCATION);
        texture = null; animation = null; width = 0; height = 0; loadedPath = null; pixelChecksum = 0;
        uploadedFrame = 0; animationStartNanos = 0; crop = null;
    }

    static long retryDeadline(long nowNanos) { return nowNanos + FAILURE_RETRY_NANOS; }
    static boolean retryAllowed(long nowNanos, long retryAfter) { return retryAfter <= nowNanos; }

    static Crop calculateCrop(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        double sourceAspect = (double) sourceWidth / sourceHeight;
        double targetAspect = (double) targetWidth / targetHeight;
        int cropX = 0, cropY = 0, cropWidth = sourceWidth, cropHeight = sourceHeight;
        if (sourceAspect > targetAspect) {
            cropWidth = Math.max(1, (int) Math.round(sourceHeight * targetAspect)); cropX = (sourceWidth - cropWidth) / 2;
        } else if (sourceAspect < targetAspect) {
            cropHeight = Math.max(1, (int) Math.round(sourceWidth / targetAspect)); cropY = (sourceHeight - cropHeight) / 2;
        }
        return new Crop(sourceWidth, sourceHeight, targetWidth, targetHeight, cropX, cropY, cropWidth, cropHeight);
    }

    public static String status() { return status; }
    public static boolean hasTexture() { return texture != null; }
    static long generation() { return generation; }
    static long pixelChecksum() { return pixelChecksum; }
    static Path loadedPath() { return loadedPath; }
    private static String conciseReason(Throwable error) {
        String message = error == null ? "未知错误" : error.getMessage();
        if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
        return message.length() > 160 ? message.substring(0, 160) : message;
    }
    record Crop(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight,
                        int x, int y, int width, int height) {
        boolean matches(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
            return this.sourceWidth == sourceWidth && this.sourceHeight == sourceHeight
                    && this.targetWidth == targetWidth && this.targetHeight == targetHeight;
        }
    }
    private record StaticImage(int width, int height, int[] pixels) {}
    private record Decoded(Path path, GifAnimation animation, StaticImage image, long checksum) {}
}
