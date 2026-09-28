package com.mengsama.mod.mengsamanetmusic.gui;

 
final class NetEaseQrAttempt {
    enum State { IDLE, FETCHING, WAITING, SCANNED, SUCCESS, EXPIRED, FAILED, CLOSED }
    private State state=State.IDLE;
    private long generation,deadline,nextPoll;
    private boolean pending;
    State state(){return state;}
    long begin(long now){generation++;deadline=now+180000;pending=true;state=State.FETCHING;return generation;}
    boolean accepts(long token,long now){expire(now);return token==generation && state!=State.CLOSED && state!=State.EXPIRED && state!=State.SUCCESS && state!=State.FAILED;}
    boolean fetched(long token,long now){if(!accepts(token,now))return false;pending=false;state=State.WAITING;nextPoll=now+2000;return true;}
    long poll(long now){expire(now);if(pending || now<nextPoll || state!=State.WAITING && state!=State.SCANNED)return -1;pending=true;return generation;}
    boolean finish(long token,int code,long now){
        if(!accepts(token,now))return false;
        pending=false;nextPoll=now+2000;
        state=switch(code){case 800->State.EXPIRED;case 801->State.WAITING;case 802->State.SCANNED;case 803->State.SUCCESS;default->State.FAILED;};
        return true;
    }
    void fail(long token,long now){finish(token,0,now);}
    void expire(long now){if((state==State.WAITING || state==State.SCANNED || state==State.FETCHING) && now>=deadline){state=State.EXPIRED;pending=false;generation++;}}
    void close(){generation++;pending=false;state=State.CLOSED;}
}
