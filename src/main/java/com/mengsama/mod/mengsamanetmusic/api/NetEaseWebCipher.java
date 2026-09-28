package com.mengsama.mod.mengsamanetmusic.api;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

 
final class NetEaseWebCipher {
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final String ALPHABET="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final BigInteger MODULUS=new BigInteger("e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7",16);
    static Map<String,String> seal(String json) {
        StringBuilder key=new StringBuilder(16);
        for(int i=0;i<16;i++)key.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return seal(json,key.toString());
    }
    static Map<String,String> seal(String json,String key) {
        if(!key.matches("[a-zA-Z0-9]{16}"))throw new IllegalArgumentException("Invalid request key");
        try {
            String params=encrypt(encrypt(json,"0CoJUm6Qyw8W8jud"),key);
            byte[] reversed=new StringBuilder(key).reverse().toString().getBytes(StandardCharsets.US_ASCII);
            String rsa=new BigInteger(1,reversed).modPow(BigInteger.valueOf(65537),MODULUS).toString(16);
            return Map.of("params",params,"encSecKey","0".repeat(256-rsa.length())+rsa);
        }catch(GeneralSecurityException error){throw new IllegalStateException("Cannot encode web request");}
    }
    private static String encrypt(String value,String key) throws GeneralSecurityException {
        Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key.getBytes(StandardCharsets.US_ASCII),"AES"),new IvParameterSpec("0102030405060708".getBytes(StandardCharsets.US_ASCII)));
        return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
