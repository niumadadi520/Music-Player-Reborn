package com.mengsama.mod.mengsamanetmusic.client.artwork;

import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

 
public final class ArtworkPixels {
    private ArtworkPixels() {}
    public static BufferedImage decode(byte[] encoded) throws IOException {
        if (encoded == null || encoded.length == 0 || encoded.length > 16 * 1024 * 1024) throw new IOException("Artwork byte limit");
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(encoded))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported artwork format");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long)width * height > 16_777_216L) throw new IOException("Artwork dimension limit");
                ImageReadParam options = reader.getDefaultReadParam();
                int stride = Math.max(1, (Math.max(width, height) + 511) / 512);
                options.setSourceSubsampling(stride, stride, 0, 0);
                return reader.read(0, options);
            } finally { reader.dispose(); }
        } catch (RuntimeException malformed) { throw new IOException("Corrupt artwork image", malformed); }
    }
}
