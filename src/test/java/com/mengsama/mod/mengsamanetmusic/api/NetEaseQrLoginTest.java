package com.mengsama.mod.mengsamanetmusic.api;

import com.mengsama.mod.mengsamanetmusic.api.qq.QqHttp;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NetEaseQrLoginTest {
    private QqHttp.Response reply(QqHttp.Request request,String json,Map<String,List<String>> headers){
        assertEquals("https",request.uri().getScheme());assertEquals("music.163.com",request.uri().getHost());assertFalse(request.redirects());
        assertTrue(request.limit()<=256*1024);assertEquals("POST",request.method());
        String form=new String(request.body(),StandardCharsets.UTF_8);
        assertTrue(form.contains("params="));assertTrue(form.contains("encSecKey="));assertTrue(request.uri().getPath().startsWith("/weapi/"));
        return new QqHttp.Response(request.uri(),200,headers,json.getBytes(StandardCharsets.UTF_8));
    }
    @Test void requestsAnonymousKeyAndConstructsOnlyOfficialLoginUrl() throws Exception {
        NetEaseQrLogin api=new NetEaseQrLogin(r->{assertFalse(r.headers().containsKey("Cookie"));return reply(r,"{\"code\":200,\"unikey\":\"test_key_123456\"}",Map.of());});
        assertEquals("https://music.163.com/login?codekey=test_key_123456",NetEaseQrLogin.loginUrl(api.create()));
    }
    @Test void acceptsNestedKeyButRejectsErrorAndMalformedKeys() throws Exception {
        NetEaseQrLogin api=new NetEaseQrLogin(r->reply(r,"{\"code\":200,\"data\":{\"unikey\":\"test_key_123456\"}}",Map.of()));
        assertEquals("test_key_123456",api.create());
        for(String body:List.of("{\"code\":500}","{\"code\":200,\"unikey\":\"evil&redirect=x\"}")){
            NetEaseQrLogin bad=new NetEaseQrLogin(r->reply(r,body,Map.of()));assertThrows(IOException.class,bad::create);
        }
    }
    @Test void waitingScannedAndExpiredNeverReturnCookies() throws Exception {
        for(int code:new int[]{800,801,802}){
            NetEaseQrLogin api=new NetEaseQrLogin(r->reply(r,"{\"code\":"+code+"}",Map.of("Set-Cookie",List.of("MUSIC_U=ignored; Path=/"))));
            var result=api.check("test_key_123456");assertEquals(code,result.code());assertEquals("",result.cookie());
        }
    }
    @Test void confirmationRequiresMusicCookieAndVerifiedAccount() throws Exception {
        AtomicInteger requests=new AtomicInteger();
        NetEaseQrLogin api=new NetEaseQrLogin(r->{
            if(requests.getAndIncrement()==0)return reply(r,"{\"code\":803}",Map.of("set-cookie",List.of("MUSIC_U=test-secret; Path=/; HttpOnly","__csrf=test-csrf; Path=/","tracking=discard; Path=/")));
            assertEquals("MUSIC_U=test-secret; __csrf=test-csrf",r.headers().get("Cookie"));assertTrue(r.uri().getPath().endsWith("nuser/account/get"));
            assertEquals("csrf_token=test-csrf",r.uri().getRawQuery());
            return reply(r,"{\"code\":200,\"account\":{\"id\":123}}",Map.of());
        });
        var result=api.check("test_key_123456");assertEquals(803,result.code());assertTrue(result.cookie().contains("test-secret"));assertFalse(result.toString().contains("test-secret"));assertEquals(2,requests.get());
    }
    @Test void successWithoutSessionCookieCannotLogIn(){
        NetEaseQrLogin api=new NetEaseQrLogin(r->reply(r,"{\"code\":803}",Map.of("Set-Cookie",List.of("__csrf=test"))));
        assertThrows(IOException.class,()->api.check("test_key_123456"));
    }
    @Test void accountValidationFailureCannotReportSuccess(){
        AtomicInteger calls=new AtomicInteger();NetEaseQrLogin api=new NetEaseQrLogin(r->calls.getAndIncrement()==0?
                reply(r,"{\"code\":803}",Map.of("Set-Cookie",List.of("MUSIC_U=test"))):reply(r,"{\"code\":200,\"account\":null,\"profile\":null}",Map.of()));
        assertThrows(IOException.class,()->api.check("test_key_123456"));
    }
    @Test void cookieAttributesAndTrackingAreNotForwarded() throws Exception {
        String cookie=NetEaseQrLogin.loginCookies(List.of("MUSIC_U=secret; Path=/; Secure; HttpOnly; SameSite=None","__csrf=csrf; Path=/","tracking=ignore; Domain=.example.com"));
        assertEquals("MUSIC_U=secret; __csrf=csrf",cookie);
        assertEquals("",NetEaseQrLogin.loginCookies(List.of("MUSIC_U=deleted; Max-Age=0; Path=/")));
    }
    @Test void accountFallbackPreservesCookieAndCsrfAndAcceptsNestedProfile() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        long id=NetEaseQrLogin.accountId(r->{
            assertEquals("MUSIC_U=secret; __csrf=a+b",r.headers().get("Cookie"));
            assertEquals("csrf_token=a%2Bb",r.uri().getRawQuery());
            int call=calls.incrementAndGet();
            assertEquals(call==1?"/weapi/w/nuser/account/get":"/weapi/nuser/account/get",r.uri().getPath());
            return reply(r,call==1?"{\"code\":200,\"account\":null}":"{\"code\":200,\"data\":{\"account\":{\"id\":0},\"profile\":{\"userId\":42}}}",Map.of());
        },"MUSIC_U=secret; __csrf=a+b");
        assertEquals(42,id);assertEquals(2,calls.get());
    }
    @Test void accountFallbackCanRecoverFromHttpError() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        assertEquals(7,NetEaseQrLogin.accountId(r->calls.incrementAndGet()==1?
                new QqHttp.Response(r.uri(),403,Map.of(),new byte[0]):reply(r,"{\"code\":200,\"account\":{\"id\":7}}",Map.of()),"MUSIC_U=test"));
        assertEquals(2,calls.get());
    }
    @Test void inlineCredentialWorksAndHttpCookieOverridesIt() throws Exception {
        for(boolean header:new boolean[]{false,true}){
            AtomicInteger calls=new AtomicInteger();
            NetEaseQrLogin api=new NetEaseQrLogin(r->{
                if(calls.getAndIncrement()==0)return reply(r,"{\"code\":803,\"cookie\":\"MUSIC_U=inline; __csrf=csrf; tracking=discard\"}",
                        header?Map.of("Set-Cookie",List.of("MUSIC_U=header; Path=/")):Map.of());
                assertEquals("MUSIC_U="+(header?"header":"inline")+"; __csrf=csrf",r.headers().get("Cookie"));
                return reply(r,"{\"code\":200,\"profile\":{\"userId\":8}}",Map.of());
            });
            assertEquals(803,api.check("test_key_123456").code());assertEquals(2,calls.get());
        }
    }
    @Test void irrelevantMalformedCookieCannotBreakLoginAndDeletionWins() throws Exception {
        assertEquals("MUSIC_U=secret",NetEaseQrLogin.loginCookies(List.of("bad tracking text","MUSIC_U=secret; Path=/")));
        assertEquals("",NetEaseQrLogin.loginCookies(List.of("MUSIC_U=secret","MUSIC_U=gone; Max-Age=0")));
    }
    @Test void serviceFailureIncludesStageAndCodeButNeverResponseSecrets(){
        NetEaseQrLogin api=new NetEaseQrLogin(r->reply(r,"{\"code\":502,\"message\":\"private-cookie-secret\"}",Map.of()));
        IOException failure=assertThrows(IOException.class,()->api.check("test_key_123456"));
        assertTrue(failure.getMessage().contains("POLL/SERVICE"));assertTrue(failure.getMessage().contains("code=502"));
        assertFalse(failure.getMessage().contains("private-cookie-secret"));assertFalse(failure.getMessage().contains("test_key"));
    }
    @Test void malformedAndTransportFailuresAreSafeAndStageSpecific(){
        NetEaseQrLogin malformed=new NetEaseQrLogin(r->reply(r,"<html>secret-cookie</html>",Map.of()));
        IOException failure=assertThrows(IOException.class,malformed::create);
        assertTrue(failure.getMessage().contains("CREATE/JSON"));assertFalse(failure.getMessage().contains("secret-cookie"));
        NetEaseQrLogin offline=new NetEaseQrLogin(r->{throw new IOException("https://music.163.com/?key=secret-key");});
        failure=assertThrows(IOException.class,()->offline.check("test_key_123456"));
        assertTrue(failure.getMessage().contains("POLL/TRANSPORT"));assertNull(failure.getCause());
        String display=NetEaseLoginFailure.describe(new java.util.concurrent.CompletionException(failure),NetEaseLoginFailure.Stage.CREATE);
        assertEquals(failure.getMessage(),display);assertFalse(display.contains("secret-key"));
        assertFalse(NetEaseLoginFailure.describe(new RuntimeException("secret-key"),NetEaseLoginFailure.Stage.CREATE).contains("secret-key"));
    }
    @Test void successfulPollWithMissingCookieIdentifiesCredentialStage(){
        NetEaseQrLogin api=new NetEaseQrLogin(r->reply(r,"{\"code\":803}",Map.of()));
        IOException failure=assertThrows(IOException.class,()->api.check("test_key_123456"));
        assertTrue(failure.getMessage().contains("COOKIE/MISSING_COOKIE"));
    }
    @Test void repeatedAccountFailureIsBoundedAndDoesNotAcceptAnonymousAccount(){
        AtomicInteger calls=new AtomicInteger();
        IOException failure=assertThrows(IOException.class,()->NetEaseQrLogin.accountId(r->{calls.incrementAndGet();return reply(r,"{\"code\":200,\"account\":null}",Map.of());},"MUSIC_U=test"));
        assertEquals(2,calls.get());assertTrue(failure.getMessage().contains("ACCOUNT/NO_ACCOUNT"));
    }
    @Test void behaviorChallengeIsRecognizedAndDoesNotFetchAccountOrCommitLogin(){
        AtomicInteger calls=new AtomicInteger();
        NetEaseQrLogin api=new NetEaseQrLogin(r->{calls.incrementAndGet();return reply(r,"{\"code\":8821,\"message\":\"secret challenge contents\"}",Map.of());});
        IOException error=assertThrows(IOException.class,()->api.check("test_key_123456"));
        assertEquals(1,calls.get());assertTrue(NetEaseLoginFailure.requiresVerification(error));
        assertTrue(NetEaseLoginFailure.shortStatus(error).startsWith("8821"));
        assertFalse(error.getMessage().contains("刷新重试"));assertFalse(error.getMessage().contains("secret"));
    }
    @Test void accountChallengeStopsBeforeFallbackEndpoint(){
        AtomicInteger calls=new AtomicInteger();
        IOException error=assertThrows(IOException.class,()->NetEaseQrLogin.accountId(r->{calls.incrementAndGet();return reply(r,"{\"code\":8821}",Map.of());},"MUSIC_U=test"));
        assertEquals(1,calls.get());assertTrue(NetEaseLoginFailure.requiresVerification(error));
    }
    @Test void unknownErrorsAreNotMisclassifiedAsBehaviorChallenges(){
        var known=new NetEaseLoginFailure(NetEaseLoginFailure.Stage.POLL,NetEaseLoginFailure.Reason.SERVICE,200,8821);
        assertTrue(NetEaseLoginFailure.requiresVerification(new java.util.concurrent.CompletionException(known)));
        var other=new NetEaseLoginFailure(NetEaseLoginFailure.Stage.POLL,NetEaseLoginFailure.Reason.SERVICE,200,502);
        assertFalse(NetEaseLoginFailure.requiresVerification(other));assertEquals("502：登录失败",NetEaseLoginFailure.shortStatus(other));
        assertFalse(NetEaseLoginFailure.requiresVerification(new IOException("8821")));
    }
    @Test void invalidKeysCannotTriggerNetworkOrInjectQrParameters(){
        NetEaseQrLogin api=new NetEaseQrLogin(r->{fail("Unexpected network request");return null;});
        for(String key:List.of("","123","abcdabcd&redirect=evil","x".repeat(257))){
            assertThrows(IOException.class,()->api.check(key));assertThrows(IllegalArgumentException.class,()->NetEaseQrLogin.loginUrl(key));
        }
    }
}
