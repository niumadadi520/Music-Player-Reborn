package com.mengsama.mod.mengsamanetmusic.api;

import com.google.gson.JsonObject;
import com.mengsama.mod.mengsamanetmusic.api.qq.*;
import java.io.IOException;
import java.net.HttpCookie;
import java.util.*;
import static com.mengsama.mod.mengsamanetmusic.api.qq.QqFields.*;
import static com.mengsama.mod.mengsamanetmusic.api.NetEaseLoginFailure.*;

 
public final class NetEaseQrLogin {
    private static final String QR_TYPE="1";
    private static final Set<String> LOGIN_COOKIES=Set.of("MUSIC_U","__csrf","NMTID","MUSIC_R");
    public record Check(int code,String cookie) {
        @Override public String toString(){return "NetEase QR status "+code+" (credential omitted)";}
    }
    private final NetEaseWebSession web;
    public NetEaseQrLogin(QqHttp.Transport http){this(new NetEaseWebSession(http));}
    NetEaseQrLogin(NetEaseWebSession web){this.web=web;}
    private static JsonObject body(QqHttp.Response response,Stage stage) throws IOException {
        try{return parse(response.text());}
        catch(IOException | RuntimeException error){throw new NetEaseLoginFailure(stage,Reason.JSON,response.status(),0);}
    }
    public String create() throws IOException {
        var response=web.request(Stage.CREATE,"login/qrcode/unikey",Map.of("type",QR_TYPE),"");
        var body=body(response,Stage.CREATE);long code=number(body,"code",0);
        String key=text(body,"unikey");if(key.isEmpty())key=text(object(body,"data"),"unikey");
        if(code!=200 || !validKey(key))throw new NetEaseLoginFailure(Stage.CREATE,code==200?Reason.MISSING_KEY:Reason.SERVICE,response.status(),code);
        return key;
    }
    public static String loginUrl(String key){
        if(!validKey(key))throw new IllegalArgumentException("Invalid QR key");
        return QqProtocol.query("https://music.163.com/login",Map.of("codekey",key)).toString();
    }
    private static boolean validKey(String key){return key!=null && key.matches("[A-Za-z0-9_-]{8,256}");}
    public Check check(String key) throws IOException {
        if(!validKey(key))throw new NetEaseLoginFailure(Stage.POLL,Reason.MISSING_KEY,0,0);
         
        var response=web.request(Stage.POLL,"login/qrcode/client/login",Map.of("key",key,"type",QR_TYPE),"");
        var body=body(response,Stage.POLL);int code=(int)number(body,"code",0);
        if(code==800 || code==801 || code==802)return new Check(code,"");
        if(code!=803)throw new NetEaseLoginFailure(Stage.POLL,Reason.SERVICE,response.status(),code);
        List<String> cookies=new ArrayList<>();
         
        String inline=text(body,"cookie");
        if(inline.length()>32768)throw new NetEaseLoginFailure(Stage.COOKIE,Reason.INVALID_COOKIE,response.status(),code);
        cookies.addAll(Arrays.asList(inline.split(";")));cookies.addAll(web.credentialHeaders());cookies.addAll(response.values("Set-Cookie"));
        String cookie=loginCookies(cookies);
        if(cookie.isBlank())throw new NetEaseLoginFailure(Stage.COOKIE,Reason.MISSING_COOKIE,response.status(),code);
        accountId(web,cookie);
        return new Check(803,cookie);
    }
     
    public static long accountId(QqHttp.Transport http,String cookie) throws IOException {
        return accountId(new NetEaseWebSession(http),cookie);
    }
    private static long accountId(NetEaseWebSession web,String cookie) throws IOException {
        NetEaseLoginFailure failure=null;
        for(String path:List.of("w/nuser/account/get","nuser/account/get")){
            try{
                var response=web.request(Stage.ACCOUNT,path,Map.of(),cookie);
                var body=body(response,Stage.ACCOUNT);long code=number(body,"code",0);
                if(code==200)for(JsonObject payload:List.of(body,object(body,"data"))){
                    long id=number(object(payload,"account"),"id",0);
                    if(id<=0)id=number(object(payload,"profile"),"userId",0);
                    if(id>0)return id;
                }
                failure=new NetEaseLoginFailure(Stage.ACCOUNT,code==200?Reason.NO_ACCOUNT:Reason.SERVICE,response.status(),code);
            }catch(NetEaseLoginFailure error){failure=error;}
             
            if(NetEaseLoginFailure.requiresVerification(failure))throw failure;
        }
        throw failure;
    }
    static String loginCookies(List<String> headers) throws IOException {
        Map<String,String> values=new LinkedHashMap<>();
        for(String header:headers){
            if(header.length()>32768)throw new NetEaseLoginFailure(Stage.COOKIE,Reason.INVALID_COOKIE,0,803);
            try{
                for(HttpCookie cookie:HttpCookie.parse(header.strip())){
                    String name=cookie.getName(),value=cookie.getValue();
                    if(!LOGIN_COOKIES.contains(name))continue;
                    if(cookie.hasExpired()){values.remove(name);continue;}
                    if(value.isBlank() || value.length()>12000 || value.chars().anyMatch(c->c<33 || c==127 || c==';' || c==','))continue;
                    values.put(name,value);
                }
            }catch(IllegalArgumentException invalid){
                 
            }
        }
        if(!values.containsKey("MUSIC_U"))return "";
        String result=values.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining("; "));
        if(result.length()>16384)throw new NetEaseLoginFailure(Stage.COOKIE,Reason.INVALID_COOKIE,0,803);
        return result;
    }
}
