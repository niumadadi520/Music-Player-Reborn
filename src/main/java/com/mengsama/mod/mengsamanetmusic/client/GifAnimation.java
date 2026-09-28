package com.mengsama.mod.mengsamanetmusic.client;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

 
public final class GifAnimation {
    public static final int MAX_WIDTH = 1920;
    public static final int MAX_HEIGHT = 1080;
    public static final int MAX_FRAMES = 256;
    public static final long MAX_DECODED_BYTES = 256L * 1024 * 1024;
    private static final int MIN_DELAY_MS = 20;

    private final int width;
    private final int height;
    private final List<int[]> frames;
    private final long[] frameEndsMs;
    private final long durationMs;
    private final int loopCount;

    private GifAnimation(int width, int height, List<int[]> frames, long[] frameEndsMs, int loopCount) {
        this.width = width;
        this.height = height;
        this.frames = List.copyOf(frames);
        this.frameEndsMs = frameEndsMs;
        this.durationMs = frameEndsMs[frameEndsMs.length - 1];
        this.loopCount = loopCount;
    }

    public static GifAnimation read(Path path) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) throw new IOException("No ImageIO GIF reader installed");
        ImageReader reader = readers.next();
        try (ImageInputStream input = ImageIO.createImageInputStream(Files.newInputStream(path))) {
            if (input == null) throw new IOException("Cannot open GIF stream");
            reader.setInput(input, false, false);
            int count = reader.getNumImages(true);
            if (count < 1 || count > MAX_FRAMES) throw new IOException("GIF frame count out of range: " + count);
            int[] logical = logicalSize(reader.getStreamMetadata());
            int width = logical[0] > 0 ? logical[0] : reader.getWidth(0);
            int height = logical[1] > 0 ? logical[1] : reader.getHeight(0);
            checkLimits(width, height, count);

            BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            List<int[]> frames = new ArrayList<>(count);
            long[] ends = new long[count];
            long elapsed = 0;
            int loopCount = loopCount(reader.getStreamMetadata());
            for (int i = 0; i < count; i++) {
                IIOMetadata metadata = reader.getImageMetadata(i);
                if (i == 0 && loopCount == 1) loopCount = loopCount(metadata);
                FrameInfo info = frameInfo(metadata);
                BufferedImage before = "restoreToPrevious".equals(info.disposal)
                        ? copy(canvas) : null;
                BufferedImage image = reader.read(i);
                Graphics2D graphics = canvas.createGraphics();
                graphics.setComposite(AlphaComposite.SrcOver);
                 
                 
                 
                int drawX = image.getWidth() == width && image.getHeight() == height ? 0 : info.left;
                int drawY = image.getWidth() == width && image.getHeight() == height ? 0 : info.top;
                graphics.drawImage(image, drawX, drawY, null);
                graphics.dispose();
                frames.add(canvas.getRGB(0, 0, width, height, null, 0, width));
                elapsed += Math.max(MIN_DELAY_MS, info.delayHundredths * 10L);
                ends[i] = elapsed;

                if ("restoreToBackgroundColor".equals(info.disposal)) {
                    graphics = canvas.createGraphics();
                    graphics.setComposite(AlphaComposite.Clear);
                    graphics.fillRect(info.left, info.top, info.width, info.height);
                    graphics.dispose();
                } else if (before != null) {
                    canvas = before;
                }
            }
            return new GifAnimation(width, height, frames, ends, loopCount);
        } finally {
            reader.dispose();
        }
    }

    private static void checkLimits(int width, int height, int count) throws IOException {
        long bytes = (long) width * height * count * 4L;
        if (width < 1 || height < 1 || width > MAX_WIDTH || height > MAX_HEIGHT)
            throw new IOException("GIF dimensions out of range: " + width + "x" + height);
        if (bytes > MAX_DECODED_BYTES) throw new IOException("GIF decoded memory limit exceeded: " + bytes);
    }

    public int frameAt(long elapsedMs) {
        if (frames.size() == 1 || durationMs <= 0) return 0;
        long time;
        if (loopCount == 0) {
            time = Math.floorMod(elapsedMs, durationMs);
        } else {
            long total = durationMs * (long) loopCount;
            if (elapsedMs >= total) return frames.size() - 1;
            time = Math.max(0, elapsedMs) % durationMs;
        }
        int low = 0, high = frameEndsMs.length - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (time < frameEndsMs[mid]) high = mid; else low = mid + 1;
        }
        return low;
    }

    public int width() { return width; }
    public int height() { return height; }
    public int frameCount() { return frames.size(); }
    public int loopCount() { return loopCount; }
    public long durationMs() { return durationMs; }
    public int[] frame(int index) { return frames.get(index); }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return result;
    }

    private static int[] logicalSize(IIOMetadata metadata) {
        Node root = tree(metadata, "javax_imageio_gif_stream_1.0");
        Node descriptor = child(root, "LogicalScreenDescriptor");
        return descriptor == null ? new int[]{0, 0} : new int[]{integer(descriptor, "logicalScreenWidth", 0), integer(descriptor, "logicalScreenHeight", 0)};
    }

    private static FrameInfo frameInfo(IIOMetadata metadata) {
        Node root = tree(metadata, "javax_imageio_gif_image_1.0");
        Node descriptor = child(root, "ImageDescriptor");
        Node control = child(root, "GraphicControlExtension");
        return new FrameInfo(integer(descriptor, "imageLeftPosition", 0), integer(descriptor, "imageTopPosition", 0),
                integer(descriptor, "imageWidth", 0), integer(descriptor, "imageHeight", 0),
                integer(control, "delayTime", 10), attribute(control, "disposalMethod", "none"));
    }

    private static int loopCount(IIOMetadata metadata) {
        Node root = tree(metadata, "javax_imageio_gif_stream_1.0");
        if (root == null) root = tree(metadata, "javax_imageio_gif_image_1.0");
        Node extensions = child(root, "ApplicationExtensions");
        if (extensions == null) return 1;
        for (Node node = extensions.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!"ApplicationExtension".equals(node.getNodeName())) continue;
            String id = attribute(node, "applicationID", "");
            Object data = node instanceof javax.imageio.metadata.IIOMetadataNode metadataNode
                    ? metadataNode.getUserObject() : null;
            if (("NETSCAPE".equals(id) || "ANIMEXTS".equals(id)) && data instanceof byte[] bytes && bytes.length >= 3)
                return (bytes[1] & 255) | ((bytes[2] & 255) << 8);
        }
        return 1;
    }

    private static Node tree(IIOMetadata metadata, String name) {
        try { return metadata == null ? null : metadata.getAsTree(name); }
        catch (RuntimeException ignored) { return null; }
    }
    private static Node child(Node parent, String name) {
        if (parent == null) return null;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
            if (name.equals(node.getNodeName())) return node;
        return null;
    }
    private static String attribute(Node node, String name, String fallback) {
        NamedNodeMap attributes = node == null ? null : node.getAttributes();
        Node value = attributes == null ? null : attributes.getNamedItem(name);
        return value == null ? fallback : value.getNodeValue();
    }
    private static int integer(Node node, String name, int fallback) {
        try { return Integer.parseInt(attribute(node, name, Integer.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private record FrameInfo(int left, int top, int width, int height, int delayHundredths, String disposal) {}
}
