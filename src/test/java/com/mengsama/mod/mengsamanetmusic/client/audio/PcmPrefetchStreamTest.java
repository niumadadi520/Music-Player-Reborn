package com.mengsama.mod.mengsamanetmusic.client.audio;

import org.junit.jupiter.api.Test;
import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class PcmPrefetchStreamTest {
    private static final AudioFormat FORMAT = new AudioFormat(8000,16,1,true,false);
    @Test void unevenConsumerReadsPreservePcmOrderAcrossAllRefills() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            byte[] expected = new byte[8192]; for(int i=0;i<expected.length;i++)expected[i]=(byte)(i*37);
            try (var stream = new PcmPrefetchStream(new AudioInputStream(new ByteArrayInputStream(expected),FORMAT,4096),514,()->false)) {
                ByteArrayOutputStream received = new ByteArrayOutputStream(); ByteBuffer chunk;
                while((chunk=stream.read(137))!=null){byte[] bytes=new byte[chunk.remaining()];chunk.get(bytes);received.write(bytes);}
                assertArrayEquals(expected,received.toByteArray()); assertEquals(FORMAT,stream.getFormat());
            }
        });
    }
    @Test void closingWhileDecoderIsBlockedDiscardsItsLateOutputAndReleasesReader() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            CountDownLatch refillEntered=new CountDownLatch(1),release=new CountDownLatch(1);
            AtomicInteger reads=new AtomicInteger(),closes=new AtomicInteger();
            InputStream source=new InputStream(){
                @Override public int read(){return 1;}
                @Override public int read(byte[] data,int offset,int count)throws IOException{
                    if(reads.incrementAndGet()>1){refillEntered.countDown();try{release.await();}catch(InterruptedException e){throw new IOException(e);}}
                    java.util.Arrays.fill(data,offset,offset+count,(byte)1);return count;
                }
                @Override public void close(){closes.incrementAndGet();release.countDown();}
            };
            var stream=new PcmPrefetchStream(new AudioInputStream(source,FORMAT,AudioSystem.NOT_SPECIFIED),32,()->false);
            assertNotNull(stream.read(32));assertTrue(refillEntered.await(1,TimeUnit.SECONDS));
            var reader=Executors.newSingleThreadExecutor();
            try{
                Future<ByteBuffer> waiting=reader.submit(()->stream.read(32));
                stream.close(); assertNull(waiting.get(1,TimeUnit.SECONDS)); assertNull(stream.read(32));
                stream.close(); assertEquals(1,closes.get());
            }finally{release.countDown();stream.close();reader.shutdownNow();}
        });
    }
    @Test void cancellationHidesBufferedSamplesAndLazyDecoderErrorsFailConstruction() throws Exception {
        AtomicBoolean obsolete=new AtomicBoolean();
        try(var stream=new PcmPrefetchStream(new AudioInputStream(new ByteArrayInputStream(new byte[64]),FORMAT,32),32,obsolete::get)){
            obsolete.set(true);assertNull(stream.read(16));
        }
        InputStream broken=new InputStream(){@Override public int read()throws IOException{throw new IOException("decoder broken");}};
        assertThrows(IOException.class,()->new PcmPrefetchStream(new AudioInputStream(broken,FORMAT,32),32,()->false));
    }
}
