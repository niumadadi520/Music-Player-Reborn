package com.mengsama.mod.mengsamanetmusic.api;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

 
public final class CoverUrlUtil {
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?[0-9a-fA-F]+);");
    private static final Pattern NETEASE_IMAGE_HOST = Pattern.compile("(?i)^p\\d*\\.music\\.126\\.net$");

    private CoverUrlUtil() {}

    public static String normalize(String rawUrl) {
        if (rawUrl == null) return "";
        String url = decodeEntities(rawUrl).trim().replaceAll("[\\r\\n\\t]", "");
        if (url.isBlank()) return "";
        if (url.startsWith("//")) url = "https:" + url;
        else if (url.regionMatches(true, 0, "http://", 0, 7) && isNeteaseImageHost(url)) {
            url = "https://" + url.substring(7);
        }
        if (!url.regionMatches(true, 0, "https://", 0, 8) && !url.regionMatches(true, 0, "http://", 0, 7)) return "";
        return url;
    }

    public static String forDisplay(String rawUrl) {
        String url = normalize(rawUrl);
        if (url.isEmpty() || !isNeteaseImage(url) || url.indexOf('?') >= 0 || url.indexOf('#') >= 0) return url;
        return url + "?param=128y128";
    }

    public static boolean isSupportedRasterUrl(String rawUrl) {
        String url = normalize(rawUrl).toLowerCase(Locale.ROOT);
        if (url.isEmpty()) return false;
        int end = url.length();
        int query = url.indexOf('?');
        if (query >= 0) end = Math.min(end, query);
        int fragment = url.indexOf('#');
        if (fragment >= 0) end = Math.min(end, fragment);
        String path = url.substring(0, end);
        return !path.endsWith(".webp") && !path.endsWith(".avif");
    }

    private static boolean isNeteaseImage(String url) {
        return isNeteaseImageHost(url);
    }

    private static boolean isNeteaseImageHost(String url) {
        try {
            String host = new java.net.URI(url).getHost();
            return host != null && NETEASE_IMAGE_HOST.matcher(host).matches();
        } catch (Exception ignored) {
            return false;
        }
    }

    static String decodeEntities(String value) {
        String decoded = value.replace("&amp;", "&").replace("&#38;", "&")
                .replace("&quot;", "\"").replace("&#39;", "'");
        Matcher matcher = NUMERIC_ENTITY.matcher(decoded);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            try {
                String digits = matcher.group(1);
                int radix = digits.startsWith("x") || digits.startsWith("X") ? 16 : 10;
                int codePoint = Integer.parseInt(radix == 16 ? digits.substring(1) : digits, radix);
                matcher.appendReplacement(result, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
            } catch (RuntimeException error) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
