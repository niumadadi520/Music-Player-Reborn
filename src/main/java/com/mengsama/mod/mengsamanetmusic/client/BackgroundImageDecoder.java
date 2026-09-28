package com.mengsama.mod.mengsamanetmusic.client;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

 
public final class BackgroundImageDecoder {
    private BackgroundImageDecoder() {}

    public static BufferedImage read(Path path) throws IOException {
        BufferedImage source = ImageIO.read(path.toFile());
        if (source == null) throw new IOException("图片数据损坏或没有可用解码器");
        BufferedImage oriented = applyOrientation(source, jpegOrientation(Files.readAllBytes(path)));
        BufferedImage argb = new BufferedImage(oriented.getWidth(), oriented.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = argb.createGraphics();
        try { graphics.drawImage(oriented, 0, 0, null); } finally { graphics.dispose(); }
        return argb;
    }

    static int jpegOrientation(byte[] data) {
        try {
            if (data.length < 4 || (data[0] & 255) != 0xFF || (data[1] & 255) != 0xD8) return 1;
            int p = 2;
            while (p + 4 <= data.length) {
                if ((data[p] & 255) != 0xFF) break;
                int marker = data[p + 1] & 255;
                int length = ((data[p + 2] & 255) << 8) | (data[p + 3] & 255);
                if (length < 2 || p + 2 + length > data.length) break;
                if (marker == 0xE1 && length >= 14 && data[p + 4] == 'E' && data[p + 5] == 'x') {
                    int tiff = p + 10;
                    ByteOrder order = data[tiff] == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
                    ByteBuffer b = ByteBuffer.wrap(data).order(order);
                    int ifd = tiff + b.getInt(tiff + 4);
                    int count = b.getShort(ifd) & 0xFFFF;
                    for (int i = 0; i < count; i++) {
                        int entry = ifd + 2 + i * 12;
                        if ((b.getShort(entry) & 0xFFFF) == 0x0112) return b.getShort(entry + 8) & 0xFFFF;
                    }
                }
                p += 2 + length;
            }
        } catch (RuntimeException ignored) {}
        return 1;
    }

    static BufferedImage applyOrientation(BufferedImage source, int orientation) {
        if (orientation < 2 || orientation > 8) return source;
        int width = source.getWidth();
        int height = source.getHeight();
        boolean swap = orientation >= 5;
        BufferedImage target = new BufferedImage(swap ? height : width, swap ? width : height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
             
            java.awt.geom.AffineTransform transform = new java.awt.geom.AffineTransform();
            switch (orientation) {
                case 2 -> { transform.scale(-1.0, 1.0); transform.translate(-width, 0); }
                case 3 -> { transform.translate(width, height); transform.rotate(Math.PI); }
                case 4 -> { transform.scale(1.0, -1.0); transform.translate(0, -height); }
                case 5 -> { transform.rotate(-Math.PI / 2); transform.scale(-1.0, 1.0); }
                case 6 -> { transform.translate(height, 0); transform.rotate(Math.PI / 2); }
                case 7 -> { transform.scale(-1.0, 1.0); transform.translate(-height, 0); transform.translate(0, width); transform.rotate(3 * Math.PI / 2); }
                case 8 -> { transform.translate(0, width); transform.rotate(3 * Math.PI / 2); }
                default -> { return source; }
            }
            graphics.drawImage(source, transform, null);
        } finally { graphics.dispose(); }
        return target;
    }
}
