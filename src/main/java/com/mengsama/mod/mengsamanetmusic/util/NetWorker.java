package com.mengsama.mod.mengsamanetmusic.util;


import java.io.IOException;
import java.net.*;
import java.util.Map;

public class NetWorker {
    public static String get(String url, Map<String,String> headers) throws IOException { return HttpExchange.text(url, null, headers); }
    public static String post(String url, String body, Map<String,String> headers) throws IOException { return HttpExchange.text(url, body, headers); }

     
    public static String resolveRedirect(String url, int maxRedirects, Map<String, String> headers) throws IOException {
        String currentUrl = url;
        URL original = URI.create(url).toURL();
        for (int i = 0; i < maxRedirects; i++) {
            HttpURLConnection connection = null;
            try {
                URL current = URI.create(currentUrl).toURL();
                connection = (HttpURLConnection) current.openConnection(getProxyFromConfig());
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                if (headers != null) AudioRequestHeaders.sanitize(original, current, headers).forEach(connection::setRequestProperty);

                int responseCode = connection.getResponseCode();
                if (!isRedirect(responseCode)) return currentUrl;
                String location = connection.getHeaderField("Location");
                if (location == null || location.isBlank()) {
                    throw new IOException("Redirect with no Location header: " + responseCode);
                }
                currentUrl = current.toURI().resolve(location).toString();
            } catch (URISyntaxException | IllegalArgumentException e) {
                throw new IOException("Invalid redirect URL: " + currentUrl, e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        throw new IOException("Too many redirects (max: " + maxRedirects + ") for " + url);
    }

    private static boolean isRedirect(int status) { return HttpExchange.REDIRECTS.contains(status); }

    public static java.net.Proxy getProxyFromConfig() {
        return java.net.Proxy.NO_PROXY;
    }

}
