package com.mengsama.mod.mengsamanetmusic.api;

import org.junit.jupiter.api.Test;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import static org.junit.jupiter.api.Assertions.*;

class NetEaseEnvelopeTest {
    @Test void unicodePayloadSurvivesTheProviderTwoLayerEnvelope() throws Exception {
        String original = "{\"ids\":[123],\"title\":\"认真的雪 & +\"}";
        String encoded = NetEaseEnvelope.encode(original,"0123456789abcdef");
        String[] parts = encoded.split("&encSecKey=");
        assertEquals(256,parts[1].length()); assertTrue(parts[1].matches("[0-9a-f]+"));
        byte[] value = URLDecoder.decode(parts[0].substring(7),StandardCharsets.UTF_8).getBytes(StandardCharsets.US_ASCII);
        Cipher decrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
        for (String key : new String[]{"0123456789abcdef","0CoJUm6Qyw8W8jud"}) {
            decrypt.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key.getBytes(StandardCharsets.US_ASCII),"AES"),
                    new IvParameterSpec("0102030405060708".getBytes(StandardCharsets.US_ASCII)));
            value = decrypt.doFinal(Base64.getDecoder().decode(value));
        }
        assertEquals(original,new String(value,StandardCharsets.UTF_8));
    }
    @Test void eachRequestUsesFreshEntropyAndRejectsInvalidKeys() throws Exception {
        assertNotEquals(NetEaseEnvelope.encode("{}"),NetEaseEnvelope.encode("{}"));
        assertThrows(IllegalArgumentException.class,()->NetEaseEnvelope.encode("{}","short"));
    }
}
