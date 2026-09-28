package com.mengsama.mod.mengsamanetmusic.api;

import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

 
public final class NetEaseEnvelope {
    private static final SecureRandom ENTROPY = new SecureRandom();
    private static final BigInteger EXPONENT = BigInteger.valueOf(65537);
    private static final BigInteger MODULUS = new BigInteger(
            "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7"
            + "b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280"
            + "104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932"
            + "575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b"
            + "3ece0462db0a22b8e7", 16);

    public static String encode(String json) throws GeneralSecurityException {
        byte[] random = new byte[8];
        ENTROPY.nextBytes(random);
        return encode(json, HexFormat.of().formatHex(random));
    }
    static String encode(String json, String nonce) throws GeneralSecurityException {
        if (!nonce.matches("[0-9a-f]{16}")) throw new IllegalArgumentException("Expected a 16-character ASCII nonce");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        for (String key : new String[]{"0CoJUm6Qyw8W8jud", nonce}) {
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
                    new IvParameterSpec("0102030405060708".getBytes(StandardCharsets.UTF_8)));
            payload = Base64.getEncoder().encode(cipher.doFinal(payload));
        }
        byte[] reversed = nonce.getBytes(StandardCharsets.US_ASCII);
        for (int a = 0, b = reversed.length - 1; a < b; a++, b--) {
            byte swap = reversed[a]; reversed[a] = reversed[b]; reversed[b] = swap;
        }
        String wrapped = new BigInteger(1, reversed).modPow(EXPONENT, MODULUS).toString(16);
        return "params=" + URLEncoder.encode(new String(payload, StandardCharsets.US_ASCII), StandardCharsets.UTF_8)
                + "&encSecKey=" + "0".repeat(256 - wrapped.length()) + wrapped;
    }
}
