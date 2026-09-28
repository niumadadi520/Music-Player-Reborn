package com.mengsama.mod.mengsamanetmusic.util;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;

 
public final class SongAddress {
    private SongAddress() {}
    public static OptionalLong findId(String address) {
        if (address == null) return OptionalLong.empty();
        try {
            URI uri = URI.create(address);
            String query = uri.getRawQuery();
            if (query == null && uri.getRawFragment() != null) query = URI.create(uri.getRawFragment()).getRawQuery();
            if (query == null) return OptionalLong.empty();
            for (String entry : query.split("&")) {
                String[] pair = entry.split("=", 2);
                if (pair.length != 2 || !"id".equals(URLDecoder.decode(pair[0], StandardCharsets.UTF_8))) continue;
                String digits = URLDecoder.decode(pair[1], StandardCharsets.UTF_8).replaceFirst("\\.mp3$", "");
                if (!digits.matches("[0-9]+")) return OptionalLong.empty();
                long value = Long.parseLong(digits);
                return value > 0 ? OptionalLong.of(value) : OptionalLong.empty();
            }
        } catch (IllegalArgumentException invalid) { return OptionalLong.empty(); }
        return OptionalLong.empty();
    }
    public static long requireId(String address) {
        return findId(address).orElseThrow(() -> new IllegalArgumentException("Song address has no positive id"));
    }
}
