package com.mengsama.mod.mengsamanetmusic.api;

import com.google.gson.JsonObject;
import com.mengsama.mod.mengsamanetmusic.api.qq.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import static com.mengsama.mod.mengsamanetmusic.api.NetEaseLoginFailure.*;

 
final class NetEaseWebSession {
    private final QqHttp.Transport http;
    private final Function<String,Map<String,String>> encoder;
    private final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    NetEaseWebSession(QqHttp.Transport http){this(http,NetEaseWebCipher::seal);}
    NetEaseWebSession(QqHttp.Transport http,Function<String,Map<String,String>> encoder){this.http=http;this.encoder=encoder;}
    QqHttp.Response request(Stage stage,String path,Map<String,String> query,String accountCookie) throws IOException {
        URI uri=URI.create("https://music.163.com/weapi/"+path);
        Map<String,String> combined=new LinkedHashMap<>();
        for(String source:List.of(String.join("; ",cookies.get(uri,Map.of()).getOrDefault("Cookie",List.of())),accountCookie))
            for(String pair:source.split(";")){
                int split=pair.indexOf('=');if(split<=0)continue;
                String name=pair.substring(0,split).strip(),value=pair.substring(split+1).strip();
                if(name.startsWith("$"))continue;
                if(value.startsWith("\"") && value.endsWith("\"") && value.length()>1)value=value.substring(1,value.length()-1);
                combined.put(name,value);
            }
        String cookie=combined.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining("; "));
        if(cookie.length()>16384 || cookie.chars().anyMatch(c->c<32 || c==127))throw new NetEaseLoginFailure(stage,Reason.INVALID_COOKIE,0,0);
        String csrf="";
        for(String pair:cookie.split(";")){
            int split=pair.indexOf('=');
            if(split>0 && pair.substring(0,split).strip().equals("__csrf")){csrf=pair.substring(split+1).strip();break;}
        }
        JsonObject params=new JsonObject();query.forEach(params::addProperty);params.addProperty("csrf_token",csrf);
        if(query.containsKey("type"))params.addProperty("type",Integer.parseInt(query.get("type")));
        Map<String,String> headers=new HashMap<>();headers.put("User-Agent",QqProtocol.USER_AGENT);
        headers.put("Referer","https://music.163.com/");headers.put("Origin","https://music.163.com");
        headers.put("Content-Type","application/x-www-form-urlencoded");headers.put("Cache-Control","no-cache");
        if(!cookie.isBlank())headers.put("Cookie",cookie);
        QqHttp.Response response;
        try {
            byte[] form=QqProtocol.query("https://music.163.com/",encoder.apply(params.toString())).getRawQuery().getBytes(StandardCharsets.UTF_8);
            response=http.exchange(new QqHttp.Request(QqProtocol.query(uri.toString(),Map.of("csrf_token",csrf)),"POST",form,headers,256*1024,false));
        }catch(IOException | RuntimeException error){throw new NetEaseLoginFailure(stage,Reason.TRANSPORT,0,0);}
        if(response.status()/100!=2)throw new NetEaseLoginFailure(stage,Reason.HTTP,response.status(),0);
         
        Set<String> allowed=Set.of("MUSIC_U","MUSIC_R","__csrf","NMTID","JSESSIONID-WYYY");
        for(String header:response.values("Set-Cookie")){
            if(header.length()>32768)continue;
            try {
                var parsed=HttpCookie.parse(header);
                if(!parsed.isEmpty() && parsed.stream().allMatch(c->allowed.contains(c.getName())))cookies.put(uri,Map.of("Set-Cookie",List.of(header)));
            }catch(IllegalArgumentException ignored){}
        }
        if(cookies.getCookieStore().getCookies().size()>24){cookies.getCookieStore().removeAll();throw new NetEaseLoginFailure(stage,Reason.INVALID_COOKIE,response.status(),0);}
        return response;
    }
    List<String> credentialHeaders(){
        return cookies.getCookieStore().getCookies().stream().filter(c->!c.hasExpired()).map(c->c.getName()+"="+c.getValue()).toList();
    }
}
