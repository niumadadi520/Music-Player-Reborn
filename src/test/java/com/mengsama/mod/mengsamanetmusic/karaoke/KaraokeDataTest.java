package com.mengsama.mod.mengsamanetmusic.karaoke;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class KaraokeDataTest {
    @Test void acceptsFullUuidAndCompactCodeButRejectsTruncationAndGarbage() {
        UUID id=UUID.randomUUID();
        assertEquals(id,KaraokeCode.parse(KaraokeCode.format(id)));
        assertEquals(id,KaraokeCode.parse("  "+id.toString().replace("-","")+"  "));
        for(String value:new String[]{"", "1-1-1-1-1", id+"extra", "z".repeat(32), "1".repeat(31), "1".repeat(65)})assertNull(KaraokeCode.parse(value));
        assertNull(KaraokeCode.parse(null));
    }
    @Test void issuedIdsAreDistinctAndRetainedInPersistentData() {
        KaraokeData data=new KaraokeData();var ids=new HashSet<UUID>();
        for(int i=0;i<1000;i++){UUID id=data.create();assertTrue(ids.add(id));assertTrue(data.contains(id));}
        CompoundTag saved=data.save(new CompoundTag());
        var list=saved.getList("Microphones",8);assertEquals(ids.size(),list.size());
        for(var tag:list)assertTrue(ids.remove(KaraokeCode.parse(tag.getAsString())));
        assertTrue(ids.isEmpty());
    }
    @Test void audioBudgetRejectsFloodAndRecoversAtRealtimeRate() {
        KaraokeFrameBudget budget=new KaraokeFrameBudget();long t=1_000_000_000L;
        for(int i=0;i<10;i++)assertTrue(budget.take(t));
        for(int i=0;i<1000;i++)assertFalse(budget.take(t));
        assertTrue(budget.take(t+20_000_000L));assertFalse(budget.take(t+20_000_000L));
        assertFalse(budget.take(t-1));
    }
    @Test void volumeIsFiniteAndClamped() {
        assertEquals(0,KaraokeCode.volume(Integer.MIN_VALUE));assertEquals(100,KaraokeCode.volume(Integer.MAX_VALUE));
        assertEquals(25,KaraokeCode.volume(25));
    }
}
