package com.mengsama.mod.mengsamanetmusic.api;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class NetEaseAccountSessionTest {
    @BeforeEach void prepare(){NetEaseAccountSession.set("MUSIC_U=old-test");}
    @AfterEach void reset(){NetEaseAccountSession.clear();}
    @Test void successfulCommitReplacesOnlyExpectedAccount(){
        long revision=NetEaseAccountSession.revision();assertTrue(NetEaseAccountSession.commit(revision,"MUSIC_U=new-test"));assertEquals("MUSIC_U=new-test",NetEaseAccountSession.cookie());
        assertFalse(NetEaseAccountSession.commit(revision,"MUSIC_U=stale-test"));assertEquals("MUSIC_U=new-test",NetEaseAccountSession.cookie());
    }
    @Test void logoutPreventsLateQrResponseFromResurrectingAccount(){
        long revision=NetEaseAccountSession.revision();NetEaseAccountSession.clear();assertFalse(NetEaseAccountSession.commit(revision,"MUSIC_U=late-test"));assertEquals("",NetEaseAccountSession.cookie());
    }
    @Test void invalidCredentialNeverReplacesWorkingAccount(){
        long revision=NetEaseAccountSession.revision();assertFalse(NetEaseAccountSession.commit(revision,""));assertFalse(NetEaseAccountSession.commit(revision,"MUSIC_U=x\r\nHeader: y"));
        assertEquals("MUSIC_U=old-test",NetEaseAccountSession.cookie());assertEquals(revision,NetEaseAccountSession.revision());
    }
}
