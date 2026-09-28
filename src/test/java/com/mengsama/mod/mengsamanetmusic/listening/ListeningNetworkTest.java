package com.mengsama.mod.mengsamanetmusic.listening;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ListeningNetworkTest {
    @Test void heartbeatCarriesOnlyTargetAndGeneration(){
        var packet=new ListeningNetwork.Beat(List.of(new ListeningNetwork.Sample("device:test",123)));
        var b=new FriendlyByteBuf(Unpooled.buffer());try{packet.encode(b);assertEquals(packet,ListeningNetwork.Beat.decode(b));assertFalse(b.isReadable());}finally{b.release();}
    }
    @Test void oversizedAndNegativeSourceCountsAreRejectedBeforeAllocation(){
        for(int count:new int[]{-1,33,Integer.MAX_VALUE}){
            var b=new FriendlyByteBuf(Unpooled.buffer());try{b.writeVarInt(count);assertThrows(IllegalArgumentException.class,()->ListeningNetwork.Beat.decode(b));}finally{b.release();}
        }
    }
    @Test void rankingRoundTripIncludesPageAndRequestIdentity(){
        var packet=new ListeningNetwork.Board(89,true,2,4,List.of(new ListeningLedger.Row("uuid","Player","",123456)));
        var b=new FriendlyByteBuf(Unpooled.buffer());try{packet.encode(b);assertEquals(packet,ListeningNetwork.Board.decode(b));assertFalse(b.isReadable());}finally{b.release();}
    }
    @Test void rankingRejectsMoreThanTwentyRows(){
        assertThrows(IllegalArgumentException.class,()->new ListeningNetwork.Board(1,false,1,1,Collections.nCopies(21,new ListeningLedger.Row("s","Song","",1))));
    }
}
