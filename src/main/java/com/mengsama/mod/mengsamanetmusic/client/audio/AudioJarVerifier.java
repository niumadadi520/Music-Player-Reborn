package com.mengsama.mod.mengsamanetmusic.client.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.spi.AudioFileReader;
import javax.sound.sampled.spi.FormatConversionProvider;
import java.io.BufferedInputStream;
import java.net.URL;
import java.util.ServiceLoader;

 
public final class AudioJarVerifier {
    private AudioJarVerifier() {}

    public static void main(String[] args) throws Exception {
        ClassLoader loader = AudioJarVerifier.class.getClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        int readers = printServices("AudioFileReader", AudioFileReader.class, loader);
        int converters = printServices("FormatConversionProvider", FormatConversionProvider.class, loader);
        if (readers < 3 || converters < 2) {
            throw new IllegalStateException("JavaSound SPI providers missing: readers=" + readers + ", converters=" + converters);
        }
        if (args.length == 0) throw new IllegalArgumentException("Pass provider=url arguments");
        for (String argument : args) {
            int separator = argument.indexOf('=');
            if (separator < 1) throw new IllegalArgumentException("Expected provider=url: " + argument);
            verify(argument.substring(0, separator), new URL(argument.substring(separator + 1)));
        }
    }

    private static <T> int printServices(String label, Class<T> service, ClassLoader loader) {
        int count = 0;
        for (T provider : ServiceLoader.load(service, loader)) {
            System.out.println("SPI " + label + " " + provider.getClass().getName());
            count++;
        }
        return count;
    }

    private static void verify(String provider, URL url) throws Exception {
        try (BufferedInputStream body = new BufferedInputStream(url.openStream(), 64 * 1024);
             AudioInputStream encoded = AudioSystem.getAudioInputStream(body)) {
            AudioFormat source = encoded.getFormat();
            float rate = source.getSampleRate() > 0 ? source.getSampleRate() : 44100f;
            int channels = source.getChannels() > 0 ? source.getChannels() : 2;
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16,
                    channels, channels * 2, rate, false);
            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(pcm, encoded)) {
                byte[] frames = decoded.readNBytes(Math.max(pcm.getFrameSize() * 1024, 4096));
                if (frames.length < pcm.getFrameSize()) throw new IllegalStateException(provider + " produced no PCM frame");
                System.out.println("PCM " + provider + " source=" + source + " target=" + pcm
                        + " bytes=" + frames.length + " frames=" + (frames.length / pcm.getFrameSize()));
            }
        }
    }
}
