package com.mengsama.mod.mengsamanetmusic.karaoke.client;

import com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeState;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KaraokeEntryTest {
    private KaraokeState state(KaraokeState.Kind kind) {
        return new KaraokeState(1,kind,null,false,false,true,80,List.of(),"");
    }
    @Test void unknownStateAndOrdinaryMusicDevicesHaveNoKaraokeEntry() {
        assertFalse(KaraokeUi.hasSettings(null));
        assertFalse(KaraokeUi.hasSettings(state(KaraokeState.Kind.NONE)));
        assertFalse(KaraokeUi.canToggle(state(KaraokeState.Kind.NONE)));
    }
    @Test void microphonesHaveKaraokeAndSpeakerRetainsConnectionVolumeSettings() {
        for(var kind:List.of(KaraokeState.Kind.HANDHELD,KaraokeState.Kind.STANDING)) {
            assertTrue(KaraokeUi.hasSettings(state(kind)));assertTrue(KaraokeUi.isMicrophone(state(kind)));assertTrue(KaraokeUi.canToggle(state(kind)));
        }
        assertTrue(KaraokeUi.hasSettings(state(KaraokeState.Kind.SPEAKER)));
        assertFalse(KaraokeUi.isMicrophone(state(KaraokeState.Kind.SPEAKER)));
        assertFalse(KaraokeUi.canToggle(state(KaraokeState.Kind.SPEAKER)));
    }
}
