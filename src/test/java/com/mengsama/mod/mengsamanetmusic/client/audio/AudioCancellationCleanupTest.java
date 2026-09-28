package com.mengsama.mod.mengsamanetmusic.client.audio;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AudioCancellationCleanupTest {
    static class Tracked extends ByteArrayInputStream {
        boolean closed;
        Tracked() { super(new byte[176400]); }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }
    @Test void supersededSeekClosesUnderlyingInputDuringConstruction() {
        Tracked input = new Tracked();
        AudioInputStream pcm = new AudioInputStream(input, new AudioFormat(44100, 16, 2, true, false), 44100);
        assertThrows(IOException.class, () -> new NetMusicAudioStream(pcm, 1, () -> true));
        assertTrue(input.closed);
    }
    @Test void normalCloseIsIdempotentAndReadTerminates() throws Exception {
        Tracked input = new Tracked();
        NetMusicAudioStream stream = new NetMusicAudioStream(new AudioInputStream(input,
                new AudioFormat(44100, 16, 2, true, false), 44100));
        stream.close(); stream.close();
        assertTrue(input.closed);
        assertNull(stream.read(1024));
    }
}
