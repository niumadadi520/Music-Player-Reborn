package com.mengsama.mod.mengsamanetmusic.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NetEaseQrAttemptTest {
    @Test void pollsAtTwoSecondIntervalsWithOnlyOneRequestInFlight(){
        var state=new NetEaseQrAttempt();long id=state.begin(0);assertEquals(-1,state.poll(100));assertTrue(state.fetched(id,100));
        assertEquals(-1,state.poll(2099));assertEquals(id,state.poll(2100));assertEquals(-1,state.poll(5000));
        assertTrue(state.finish(id,801,5000));assertEquals(-1,state.poll(6999));assertEquals(id,state.poll(7000));
    }
    @Test void transitionsFromWaitingToPhoneConfirmationAndSuccess(){
        var state=new NetEaseQrAttempt();long id=state.begin(0);state.fetched(id,1);state.poll(2001);state.finish(id,802,2002);
        assertEquals(NetEaseQrAttempt.State.SCANNED,state.state());state.poll(4002);assertTrue(state.finish(id,803,4003));
        assertEquals(NetEaseQrAttempt.State.SUCCESS,state.state());assertEquals(-1,state.poll(8000));assertFalse(state.accepts(id,8001));
    }
    @Test void refreshInvalidatesBothOldFetchAndOldConfirmation(){
        var state=new NetEaseQrAttempt();long old=state.begin(0),next=state.begin(2);
        assertFalse(state.fetched(old,3));assertFalse(state.finish(old,803,3));assertTrue(state.fetched(next,4));
    }
    @Test void closingScreenRejectsAllLateResults(){
        var state=new NetEaseQrAttempt();long id=state.begin(0);state.fetched(id,1);state.poll(2001);state.close();
        assertFalse(state.finish(id,803,2002));assertFalse(state.fetched(id,2002));assertEquals(-1,state.poll(9000));
    }
    @Test void expirationCannotBeUndoneByLateSuccess(){
        var state=new NetEaseQrAttempt();long id=state.begin(0);state.fetched(id,10);assertFalse(state.finish(id,803,180000));
        assertEquals(NetEaseQrAttempt.State.EXPIRED,state.state());assertEquals(-1,state.poll(190000));
    }
    @Test void serverExpiryAndErrorsStopPollingUntilRefresh(){
        for(int code:new int[]{800,0,500}){var state=new NetEaseQrAttempt();long id=state.begin(0);state.fetched(id,1);state.finish(id,code,3000);assertEquals(-1,state.poll(6000));}
    }
}
