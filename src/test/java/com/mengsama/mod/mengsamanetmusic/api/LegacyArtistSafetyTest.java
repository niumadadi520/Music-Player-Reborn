package com.mengsama.mod.mengsamanetmusic.api;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LegacyArtistSafetyTest {
    @Test void hugeCounterReadsOnlyExistingFieldsAndPreservesOriginalTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("artistCount", Integer.MAX_VALUE);
        tag.putString("artist_12", "十二"); tag.putString("artist_2", "二"); tag.putString("artist_0", "零");
        tag.putString("artist_bad", "ignored"); tag.putString("artist_2147483648", "ignored");
        tag.putString("unknown-extension", "unchanged");
        CompoundTag before = tag.copy();
        SongInfo song = assertTimeoutPreemptively(Duration.ofSeconds(1), () -> SongInfo.deserializeNBT(tag));
        assertEquals(List.of("零", "二", "十二"), song.artists);
        assertEquals(before, tag);
    }
    @Test void historicalCounterStillExcludesFieldsBeyondItsEnd() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("artistCount", 2); tag.putString("artist_1", "B"); tag.putString("artist_0", "A"); tag.putString("artist_2", "C");
        assertEquals(List.of("A", "B"), SongInfo.deserializeNBT(tag).artists);
    }
}
