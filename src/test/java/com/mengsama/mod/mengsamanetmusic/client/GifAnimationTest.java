package com.mengsama.mod.mengsamanetmusic.client;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GifAnimationTest {
    @Test void compositesTransparencyDisposalAndAdvancesByDelay() throws Exception {
        Path gif = Files.createTempFile("mengsama-animation", ".gif");
        try {
            writeGif(gif);
            GifAnimation animation = GifAnimation.read(gif);
            assertEquals(3, animation.frameCount());
            assertEquals(2, animation.width());
            assertEquals(1, animation.height());
            assertEquals(0, animation.loopCount());
            assertEquals(320, animation.durationMs());
            assertEquals(0, animation.frameAt(99));
            assertEquals(1, animation.frameAt(100));
            assertEquals(2, animation.frameAt(300));
            assertEquals(0, animation.frameAt(320));
            assertEquals(0xFFFF0000, animation.frame(0)[0]);
            assertEquals(0xFF0000FF, animation.frame(1)[1]);
             
            assertEquals(0x00000000, animation.frame(2)[1]);
        } finally { Files.deleteIfExists(gif); }
    }

    private static void writeGif(Path path) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(Files.newOutputStream(path))) {
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            writeFrame(writer, image(0xFFFF0000, 0), 10, "doNotDispose", true);
            writeFrame(writer, image(0, 0xFF0000FF), 20, "restoreToBackgroundColor", false);
            writeFrame(writer, image(0xFF00FF00, 0), 0, "doNotDispose", false);
            writer.endWriteSequence();
        } finally { writer.dispose(); }
    }

    private static BufferedImage image(int left, int right) {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, left); image.setRGB(1, 0, right); return image;
    }

    private static void writeFrame(ImageWriter writer, BufferedImage image, int delay, String disposal, boolean loop) throws Exception {
        IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), writer.getDefaultWriteParam());
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree("javax_imageio_gif_image_1.0");
        IIOMetadataNode control = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
        control.setAttribute("disposalMethod", disposal); control.setAttribute("userInputFlag", "FALSE");
        control.setAttribute("transparentColorFlag", "TRUE"); control.setAttribute("delayTime", Integer.toString(delay));
        control.setAttribute("transparentColorIndex", "0");
        if (loop) {
            IIOMetadataNode apps = new IIOMetadataNode("ApplicationExtensions");
            IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
            app.setAttribute("applicationID", "NETSCAPE"); app.setAttribute("authenticationCode", "2.0");
            app.setUserObject(new byte[]{1, 0, 0}); apps.appendChild(app); root.appendChild(apps);
        }
        metadata.setFromTree("javax_imageio_gif_image_1.0", root);
        writer.writeToSequence(new javax.imageio.IIOImage(image, null, metadata), writer.getDefaultWriteParam());
    }
}
