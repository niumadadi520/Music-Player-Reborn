package com.mengsama.mod.mengsamanetmusic.api;

import com.google.gson.*;
import com.mengsama.mod.mengsamanetmusic.api.qq.QqHttp;
import org.junit.jupiter.api.Test;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NetEaseWebProtocolTest {
    private static final String KEY="a1B2c3D4e5F6g7H8";
    private static String decrypt(String text,String key) throws Exception {
        Cipher c=Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key.getBytes(StandardCharsets.US_ASCII),"AES"),new IvParameterSpec("0102030405060708".getBytes(StandardCharsets.US_ASCII)));
        return new String(c.doFinal(Base64.getDecoder().decode(text)),StandardCharsets.UTF_8);
    }
    private static JsonObject payload(QqHttp.Request request){
        try {
            Map<String,String> fields=new HashMap<>();
            for(String field:new String(request.body(),StandardCharsets.UTF_8).split("&")){
                var pair=field.split("=",2);fields.put(pair[0],URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
            }
            assertEquals(Set.of("params","encSecKey"),fields.keySet());
            assertEquals(256,fields.get("encSecKey").length());
            return JsonParser.parseString(decrypt(decrypt(fields.get("params"),KEY),"0CoJUm6Qyw8W8jud")).getAsJsonObject();
        }catch(Exception error){throw new AssertionError(error);}
    }
    private QqHttp.Response response(QqHttp.Request request,String body,String... cookies){
        assertEquals("https",request.uri().getScheme());assertEquals("music.163.com",request.uri().getHost());
        assertFalse(request.redirects());assertEquals("POST",request.method());assertTrue(request.limit()<=262144);
        return new QqHttp.Response(request.uri(),200,Map.of("Set-Cookie",List.of(cookies)),body.getBytes(StandardCharsets.UTF_8));
    }
    @Test void encoderMatchesIndependentNodeCryptoVector() throws Exception {
        try(var in=getClass().getResourceAsStream("/netease-web-vector.json")){
            assertNotNull(in);JsonObject v=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            var result=NetEaseWebCipher.seal(v.get("json").getAsString(),v.get("key").getAsString());
            assertEquals(v.get("params").getAsString(),result.get("params"));assertEquals(v.get("encSecKey").getAsString(),result.get("encSecKey"));
        }
    }
    @Test void requestsUseFreshKeysAndNeverContainPlainQrTokens(){
        var one=NetEaseWebCipher.seal("{\"key\":\"sensitive-qr-key\"}");var two=NetEaseWebCipher.seal("{\"key\":\"sensitive-qr-key\"}");
        assertNotEquals(one,two);assertFalse(one.values().stream().anyMatch(s->s.contains("sensitive-qr-key")));
    }
    @Test void fullWebFlowCarriesOnlyItsOwnSessionThroughConfirmation() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        NetEaseWebSession session=new NetEaseWebSession(r->{
            int call=calls.getAndIncrement();JsonObject data=payload(r);
            if(call<3)assertEquals(1,data.get("type").getAsInt());
            if(call==0){assertEquals("/weapi/login/qrcode/unikey",r.uri().getPath());assertFalse(r.headers().containsKey("Cookie"));return response(r,"{\"code\":200,\"unikey\":\"test_key_123456\"}","JSESSIONID-WYYY=session-one; Path=/; Secure","__csrf=csrf-one; Path=/; Secure");}
            assertTrue(r.headers().get("Cookie").contains("JSESSIONID-WYYY=session-one"));assertEquals("csrf-one",data.get("csrf_token").getAsString());
            if(call==1)return response(r,"{\"code\":801}");
            if(call==2)return response(r,"{\"code\":803}","MUSIC_U=authorized; Path=/; Secure");
            assertEquals("/weapi/w/nuser/account/get",r.uri().getPath());assertTrue(r.headers().get("Cookie").contains("MUSIC_U=authorized"));
            return response(r,"{\"code\":200,\"account\":{\"id\":7}}");
        },json->NetEaseWebCipher.seal(json,KEY));
        NetEaseQrLogin api=new NetEaseQrLogin(session);String key=api.create();assertEquals(801,api.check(key).code());
        var confirmed=api.check(key);assertEquals(803,confirmed.code());assertTrue(confirmed.cookie().contains("MUSIC_U=authorized"));assertEquals(4,calls.get());
    }
    @Test void freshAttemptDoesNotReceiveAnotherAttemptsCookies() throws Exception {
        NetEaseWebSession one=new NetEaseWebSession(r->response(r,"{\"code\":200}","JSESSIONID-WYYY=one; Path=/; Secure"));
        one.request(NetEaseLoginFailure.Stage.CREATE,"login/qrcode/unikey",Map.of("type","1"),"");
        NetEaseWebSession two=new NetEaseWebSession(r->{assertFalse(r.headers().containsKey("Cookie"));return response(r,"{\"code\":200}");});
        two.request(NetEaseLoginFailure.Stage.CREATE,"login/qrcode/unikey",Map.of("type","1"),"");
        assertTrue(two.credentialHeaders().isEmpty());assertFalse(one.credentialHeaders().isEmpty());
    }
    @Test void foreignAndUnrelatedCookiesAreNotRetained() throws Exception {
        NetEaseWebSession session=new NetEaseWebSession(r->response(r,"{\"code\":200}","MUSIC_U=foreign; Domain=evil.example; Path=/","tracking=discard; Path=/"));
        session.request(NetEaseLoginFailure.Stage.CREATE,"login/qrcode/unikey",Map.of("type","1"),"");
        assertTrue(session.credentialHeaders().isEmpty());
    }
}
