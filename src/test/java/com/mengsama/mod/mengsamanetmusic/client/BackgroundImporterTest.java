package com.mengsama.mod.mengsamanetmusic.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BackgroundImporterTest {
    @TempDir Path temp;

    @Test void importsUppercaseRealJpegFromChinesePathWithSpacesAndDecodesPixels() throws Exception {
        Path sourceDir = Files.createDirectories(temp.resolve("中文 路径"));
        Path source = sourceDir.resolve("背景 图片.JPEG");
        BufferedImage image = new BufferedImage(9, 5, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < 5; y++) for (int x = 0; x < 9; x++) image.setRGB(x, y, Color.ORANGE.getRGB());
        assertTrue(ImageIO.write(image, "jpeg", source.toFile()));

        Path target = BackgroundImporter.importAtomically(source, temp.resolve("config backgrounds"));
        assertEquals("background.jpeg", target.getFileName().toString());
        BufferedImage decoded = BackgroundImageDecoder.read(target);
        assertEquals(9, decoded.getWidth());
        assertEquals(5, decoded.getHeight());
        assertNotEquals(0, decoded.getRGB(2, 2));
    }

    @Test void rejectsCorruptJpegWithConcreteReason() throws Exception {
        Path corrupt = temp.resolve("BROKEN.JPG");
        Files.writeString(corrupt, "not-a-jpeg");
        Path target = BackgroundImporter.importAtomically(corrupt, temp.resolve("backgrounds"));
        IOException error = assertThrows(IOException.class, () -> BackgroundImageDecoder.read(target));
        assertTrue(error.getMessage().contains("损坏") || error.getMessage().contains("解码器"));
    }

    @Test void replacementIsImmediateAndChangesDecodedPixels() throws Exception {
        Path one = temp.resolve("one.PNG"), two = temp.resolve("two.png"), out = temp.resolve("out");
        writePng(one, Color.RED); writePng(two, Color.BLUE);
        Path target = BackgroundImporter.importAtomically(one, out);
        int before = BackgroundImageDecoder.read(target).getRGB(0, 0);
        BackgroundImporter.importAtomically(two, out);
        int after = BackgroundImageDecoder.read(target).getRGB(0, 0);
        assertNotEquals(before, after);
    }

    private static void writePng(Path path, Color color) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) image.setRGB(x, y, color.getRGB());
        ImageIO.write(image, "png", path.toFile());
    }
}
