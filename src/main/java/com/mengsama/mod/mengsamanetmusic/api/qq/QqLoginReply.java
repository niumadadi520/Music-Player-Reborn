package com.mengsama.mod.mengsamanetmusic.api.qq;

import com.mengsama.mod.mengsamanetmusic.api.QqLoginService.LoginState;
import java.util.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

 


public record QqLoginReply(String code, LoginState state, String verificationUrl) {

    public static QqLoginReply read(String body) {
        List<String> fields = arguments(body);
        if (fields.isEmpty())
            return new QqLoginReply("", LoginState.WAITING_SCAN, "");
        String code = fields.get(0);
        if (code.equals("65"))
            return new QqLoginReply(code, LoginState.QR_EXPIRED, "");
        if (!code.equals("0"))
            return new QqLoginReply(code, LoginState.WAITING_SCAN, "");
        String url = fields.size() > 2 ? fields.get(2) : "";
        if (url.isBlank() && fields.size() > 4)
            url = fields.get(4);
        return new QqLoginReply(code, url.isBlank() ? LoginState.FAILED : LoginState.VERIFYING, url);
    }

    private static List<String> arguments(String source) {
        if (source == null)
            return List.of();
        int name = source.indexOf("ptuiCB");
        if (name < 0)
            return List.of();
        int cursor = name + 6;
        while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++;
        if (cursor >= source.length() || source.charAt(cursor++) != '(')
            return List.of();
        List<String> values = new ArrayList<>();
        while (cursor < source.length() && values.size() < 16) {
            char token = source.charAt(cursor++);
            if (token == ')')
                return values;
            if (Character.isWhitespace(token) || token == ',')
                continue;
            if (token != '\'' && token != '"')
                return List.of();
            char quote = token;
            StringBuilder field = new StringBuilder();
            boolean ended = false;
            while (cursor < source.length()) {
                char ch = source.charAt(cursor++);
                if (ch == quote) {
                    ended = true;
                    break;
                }
                if (ch == '\\') {
                    if (cursor >= source.length())
                        return List.of();
                    ch = source.charAt(cursor++);
                    if (ch == 'x' || ch == 'u') {
                        int length = ch == 'x' ? 2 : 4;
                        if (cursor + length > source.length())
                            return List.of();
                        try {
                            field.append((char) Integer.parseInt(source.substring(cursor, cursor + length), 16));
                            cursor += length;
                            continue;
                        } catch (NumberFormatException malformed) {
                            return List.of();
                        }
                    }
                    ch = switch(ch) {
                        case 'n' ->
                            '\n';
                        case 'r' ->
                            '\r';
                        case 't' ->
                            '\t';
                        default ->
                            ch;
                    };
                }
                field.append(ch);
            }
            if (!ended)
                return List.of();
            values.add(field.toString());
        }
        return List.of();
    }

    public static Map<String, String> cookies(List<String> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String header : headers) try {
            for (HttpCookie cookie : HttpCookie.parse(header)) if (!cookie.hasExpired())
                result.put(cookie.getName(), cookie.getValue());
            else
                result.remove(cookie.getName());
        } catch (IllegalArgumentException malformed) {
             
        }
        return result;
    }

    public static String code(String value) {
        if (value == null)
            return "";
        var match = Pattern.compile("(?:[?&]|\\b)code=([^&\\s'\"<>]+)").matcher(value.replace("&amp;", "&"));
        if (!match.find())
            return "";
        try {
            return URLDecoder.decode(match.group(1), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException bad) {
            return "";
        }
    }

    public static String authorizationCode(Map<String, List<String>> headers, String body) {
        for (var entry : headers.entrySet()) if ("location".equalsIgnoreCase(entry.getKey()))
            for (String location : entry.getValue()) {
                String code = code(location);
                if (!code.isBlank())
                    return code;
            }
        return code(body);
    }

    public static long token(String value, int seed) {
        long result = seed;
        for (char character : Objects.requireNonNullElse(value, "").toCharArray()) result = (33 * result + character) & 0x7fffffffL;
        return result;
    }
}
