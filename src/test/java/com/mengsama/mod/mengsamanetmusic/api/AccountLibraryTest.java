package com.mengsama.mod.mengsamanetmusic.api;

import com.google.gson.*;
import com.mengsama.mod.mengsamanetmusic.api.qq.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.atomic.*;
import static com.mengsama.mod.mengsamanetmusic.api.qq.QqFields.*;
import static org.junit.jupiter.api.Assertions.*;

class AccountLibraryTest {
    private static JsonArray array(JsonElement... values){JsonArray rows=new JsonArray();for(JsonElement value:values)rows.add(value);return rows;}
    private QqHttp.Response response(QqHttp.Request r,JsonObject body){return new QqHttp.Response(r.uri(),200,Map.of(),body.toString().getBytes(StandardCharsets.UTF_8));}
    private QqCredential credential(){return new QqCredential("10001","test-secret",0,0,"","");}
    private JsonObject track(int id){return properties("mid","testMID"+id,"name","歌 "+id,"interval",200);}
    @Test void qqUsesOnlyCapturedAccountAndRetrievesEveryPage() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        AccountLibrary api=new AccountLibrary(r->{
            assertFalse(r.redirects());assertEquals("https",r.uri().getScheme());
            assertEquals("uin=10001; qm_keyst=test-secret",r.headers().get("Cookie"));
            if(calls.getAndIncrement()==0){assertEquals("c6.y.qq.com",r.uri().getHost());return response(r,properties("code",0,"data",properties("creator",properties("encrypt_uin","encrypted"))));}
            JsonObject body=parse(new String(r.body(),StandardCharsets.UTF_8));assertEquals("10001",text(object(body,"comm"),"uin"));
            assertEquals("test-secret",text(object(body,"comm"),"authst"));assertEquals("2",text(object(body,"comm"),"tmeLoginType"));
            JsonObject params=object(body,"library","param");assertEquals("encrypted",text(params,"enc_host_uin"));assertEquals(201,number(params,"dirid",0));
            int begin=(int)number(params,"song_begin",0);JsonArray entries=new JsonArray();
            for(int i=begin;i<Math.min(begin+100,205);i++)entries.add(track(i));
            return response(r,properties("code",0,"library",properties("code",0,"data",properties("songlist",entries,"total_song_num",205))));
        });
        var result=api.qqLiked(credential(),()->true);assertEquals(205,result.songs().size());assertEquals(4,calls.get());
        assertEquals("testMID204",result.songs().get(204).providerId);assertEquals("qq",result.songs().get(0).source);
    }
    @Test void qqRejectsMissingLoginBeforeNetwork(){
        AccountLibrary api=new AccountLibrary(r->{fail("Anonymous library request");return null;});
        assertThrows(IOException.class,()->api.qqLiked(new QqCredential(),()->true));
    }
    @Test void qqRepeatedPageCannotLoopOrPublishPartialSuccess(){
        AtomicInteger calls=new AtomicInteger();
        AccountLibrary api=new AccountLibrary(r->{
            if(calls.getAndIncrement()==0)return response(r,properties("data",properties("creator",properties("encrypt_uin","e"))));
            return response(r,properties("library",properties("data",properties("songlist",array(track(1)),"total_song_num",500))));
        });
        assertThrows(IOException.class,()->api.qqLiked(credential(),()->true));assertEquals(3,calls.get());
    }
    @Test void accountChangeCancelsBeforeNextPrivateRequest(){
        AtomicBoolean current=new AtomicBoolean(true);AtomicInteger calls=new AtomicInteger();
        AccountLibrary api=new AccountLibrary(r->{calls.incrementAndGet();current.set(false);return response(r,properties("data",properties("creator",properties("encrypt_uin","e"))));});
        assertThrows(IOException.class,()->api.qqLiked(credential(),current::get));assertEquals(1,calls.get());
    }
    @Test void neteaseAuthenticatesThenBatchesLikesInOriginalOrder() throws Exception {
        AtomicInteger details=new AtomicInteger();
        AccountLibrary api=new AccountLibrary(r->{
            assertEquals("MUSIC_U=local-test",r.headers().get("Cookie"));assertEquals("music.163.com",r.uri().getHost());assertFalse(r.redirects());
            String path=r.uri().getPath();
            if(path.endsWith("account/get"))return response(r,properties("code",200,"account",properties("id",7)));
            if(path.endsWith("like/get")){JsonArray ids=new JsonArray();for(int i=401;i>=1;i--)ids.add(i);ids.add(1);return response(r,properties("code",200,"ids",ids));}
            details.incrementAndGet();String query=URLDecoder.decode(r.uri().getRawQuery(),StandardCharsets.UTF_8);
            JsonArray batch=JsonParser.parseString(query.substring(2)).getAsJsonArray();assertTrue(batch.size()<=200);
            JsonArray entries=new JsonArray();for(JsonElement item:batch){long id=number(item.getAsJsonObject(),"id",0);entries.add(properties("id",id,"name","歌 "+id,"dt",200000));}
            return response(r,properties("code",200,"songs",entries));
        });
        var result=api.netEaseLiked("MUSIC_U=local-test",()->true);assertEquals(401,result.songs().size());assertEquals(3,details.get());
        assertEquals(401,result.songs().get(0).songId);assertEquals(1,result.songs().get(400).songId);
    }
    @Test void expiredNetEaseAccountDoesNotFetchAnyLikes(){
        AtomicInteger calls=new AtomicInteger();AccountLibrary api=new AccountLibrary(r->{calls.incrementAndGet();return response(r,properties("code",200));});
        assertThrows(IOException.class,()->api.netEaseLiked("MUSIC_U=expired",()->true));assertEquals(2,calls.get());
    }
    @Test void missingLikesFieldIsFailureRatherThanEmptyLibrary(){
        AtomicInteger calls=new AtomicInteger();AccountLibrary api=new AccountLibrary(r->response(r,calls.getAndIncrement()==0?properties("code",200,"account",properties("id",1)):properties("code",200)));
        assertThrows(IOException.class,()->api.netEaseLiked("MUSIC_U=x",()->true));
    }
    @Test void appleUsesAllPagesAndRejectsCrossOriginCredentialRedirects(){
        AtomicInteger calls=new AtomicInteger();AccountLibrary api=new AccountLibrary(r->{
            calls.incrementAndGet();assertFalse(r.redirects());assertEquals("user-test",r.headers().get("Music-User-Token"));
            return response(r,properties("data",new JsonArray(),"next","https://evil.example/steal"));
        });
        assertThrows(IOException.class,()->api.applePlaylists(Map.of("Music-User-Token","user-test"),()->true));assertEquals(1,calls.get());
    }
    @Test void appleLibraryPlaylistsFollowPagination() throws Exception {
        AtomicInteger calls=new AtomicInteger();AccountLibrary api=new AccountLibrary(r->{int index=calls.incrementAndGet();return response(r,
                properties("data",array(properties("id","p."+index,"attributes",properties("name",index==1?"Favorite Songs":"Playlist"))),"next",index==1?"/v1/me/library/playlists?offset=1":""));});
        var result=api.applePlaylists(Map.of("Music-User-Token","user-test"),()->true);assertEquals(2,result.size());assertEquals("Favorite Songs",result.get(0).name());
    }
    @Test void applePreviewMetadataRetainsProviderIdentity() throws Exception {
        AccountLibrary api=new AccountLibrary(r->response(r,properties("data",array(properties("relationships",properties("catalog",properties("data",array(properties("id","123","attributes",properties("name","Song","artistName","Artist","previews",array(properties("url","https://audio-ssl.itunes.apple.com/test.m4a"))))))))))));
        var result=api.applePlaylist(new AccountLibrary.Playlist("p.1","Favorite Songs"),Map.of("Music-User-Token","user-test"),()->true);
        assertEquals(1,result.songs().size());SongInfo song=result.songs().get(0);assertEquals("apple",song.source);assertEquals("123",song.providerId);assertEquals(30,song.songTime);
    }
}
