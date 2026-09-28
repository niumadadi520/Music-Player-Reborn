package com.mengsama.mod.mengsamanetmusic.listening;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ListeningLedgerTest {
    @Test void playerClockDoesNotDuplicateSimultaneousDeviceSamples(){
        var ledger=new ListeningLedger();UUID player=UUID.randomUUID();ledger.sample(player,"A",0,true);
        for(int tick=20;tick<=1200;tick+=20){ledger.sample(player,"A",tick,true);ledger.sample(player,"A",tick,true);}
        assertEquals(1200,ledger.ranked(true).get(0).value());
    }
    @Test void pauseDisconnectAndOfflineGapDoNotAccumulate(){
        var ledger=new ListeningLedger();UUID player=UUID.randomUUID();ledger.sample(player,"A",0,true);ledger.sample(player,"A",20,true);
        ledger.sample(player,"A",40,false);ledger.sample(player,"A",400,true);ledger.sample(player,"A",420,true);
        ledger.forget(player);ledger.sample(player,"A",10000,true);ledger.sample(player,"A",11000,true);
        assertEquals(40,ledger.ranked(true).get(0).value());
    }
    @Test void playerIdentitySurvivesRenameAndOtherPlayersHaveIndependentClocks(){
        var ledger=new ListeningLedger();UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        ledger.sample(a,"Before",0,true);ledger.sample(a,"After",20,true);ledger.sample(b,"B",0,true);ledger.sample(b,"B",20,true);ledger.sample(b,"B",40,true);
        var rows=ledger.ranked(true);assertEquals(b.toString(),rows.get(0).key());assertEquals("After",rows.get(1).title());
    }
    @Test void songTiesSortStablyAndCountsSaturate(){
        var ledger=new ListeningLedger();ledger.played("z","Z","");ledger.played("a","A","");assertEquals("a",ledger.ranked(false).get(0).key());
        ledger.restore(false,new ListeningLedger.Row("a","A","",Long.MAX_VALUE));ledger.played("a","A","");assertEquals(Long.MAX_VALUE,ledger.ranked(false).get(0).value());
    }
    @Test void worldDataRoundTripRetainsUnknownFieldsAndExactTotals(){
        CompoundTag tag=new CompoundTag();tag.putString("Other","keep");tag.putInt("Version",1);
        ListTag rows=new ListTag();CompoundTag row=new CompoundTag();row.putString("Key","a");row.putString("Title","A");row.putLong("Value",10);row.putString("FutureMetadata","keep");rows.add(row);tag.put("Songs",rows);
        ListeningData data=ListeningData.load(tag);data.ledger.played("a","A","artist");CompoundTag saved=data.save(new CompoundTag());
        assertEquals("keep",saved.getString("Other"));assertEquals("keep",saved.getList("Songs",10).getCompound(0).getString("FutureMetadata"));
        assertEquals(11,ListeningData.load(saved).ledger.ranked(false).get(0).value());assertEquals(10,tag.getList("Songs",10).getCompound(0).getLong("Value"));
    }
    @Test void futureDataFormatIsNeverRewritten(){
        CompoundTag tag=new CompoundTag();tag.putInt("Version",9);tag.putString("Songs","future-format");
        ListeningData data=ListeningData.load(tag);assertFalse(data.writable());assertEquals(tag,data.save(new CompoundTag()));
    }
    @Test void emptySaveAndSeparateWorldInstancesAreIndependent(){
        ListeningData a=new ListeningData(),b=new ListeningData();a.ledger.played("song","A","");assertTrue(b.ledger.ranked(false).isEmpty());
        assertEquals(1,a.save(new CompoundTag()).getInt("Version"));
    }
}
