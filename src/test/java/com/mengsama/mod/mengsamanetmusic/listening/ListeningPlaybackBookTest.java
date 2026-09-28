package com.mengsama.mod.mengsamanetmusic.listening;
import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ListeningPlaybackBookTest {
    private SongInfo song(String id){SongInfo song=new SongInfo(id,"Song "+id,180);song.source="qq";song.providerId=id;return song;}
    @Test void manyListenersDoNotMultiplyOneDevicePlay(){
        var book=new ListeningPlaybackBook();var ledger=new ListeningLedger();
        for(int i=0;i<20;i++)book.accept("device",song("mid"),10,false).heard(ledger);
        assertEquals(1,ledger.ranked(false).get(0).value());
    }
    @Test void seeksAndRefreshesPreservePlayButExplicitReplayCountsAgain(){
        var book=new ListeningPlaybackBook();var ledger=new ListeningLedger();
        var original=book.accept("device",song("mid"),10,false);original.heard(ledger);
        assertSame(original,book.accept("device",song("mid"),11,true));book.get("device").heard(ledger);
        assertSame(original,book.accept("device",song("mid"),12,true));book.get("device").heard(ledger);
        assertEquals(1,ledger.ranked(false).get(0).value());
        assertNotSame(original,book.accept("device",song("mid"),13,false));book.get("device").heard(ledger);
        assertEquals(2,ledger.ranked(false).get(0).value());
    }
    @Test void changedSongAndOtherDevicesHaveIndependentPlays(){
        var book=new ListeningPlaybackBook();var ledger=new ListeningLedger();
        book.accept("one",song("a"),1,false).heard(ledger);book.accept("two",song("a"),2,false).heard(ledger);
        book.accept("one",song("b"),3,true).heard(ledger);
        assertEquals(2,ledger.ranked(false).size());assertEquals(2,ledger.ranked(false).get(0).value());
    }
    @Test void staleResponsesCannotReplaceCurrentSongAndUndeliveredAudioDoesNotCount(){
        var book=new ListeningPlaybackBook();var ledger=new ListeningLedger();var current=book.accept("device",song("b"),9,false);
        assertNull(book.accept("device",song("a"),8,false));assertSame(current,book.get("device"));assertTrue(ledger.ranked(false).isEmpty());
    }
    @Test void directUrlCredentialsAreNotPresentInRankingIdentity(){
        var book=new ListeningPlaybackBook();var ledger=new ListeningLedger();SongInfo song=new SongInfo("https://host.example/song.mp3?token=secret","Song",100);
        book.accept("device",song,1,false).heard(ledger);assertFalse(ledger.ranked(false).get(0).toString().contains("secret"));assertFalse(ledger.ranked(false).get(0).key().contains("https"));
    }
}
