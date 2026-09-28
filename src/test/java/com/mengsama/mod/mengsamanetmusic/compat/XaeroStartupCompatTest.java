package com.mengsama.mod.mengsamanetmusic.compat;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
class XaeroStartupCompatTest {
    @Test void existingMapStateIsNeverReplaced() throws Exception {
        Object saved = new Object();
        assertFalse(XaeroStartupCompat.initializeIfMissing(() -> saved, () -> fail("Must preserve existing map state")));
    }
    @Test void missingStateIsInitializedOnceAndRepeatedStartupIsSafe() throws Exception {
        var state = new AtomicReference<>(); var calls = new AtomicInteger(); Object created = new Object();
        XaeroStartupCompat.Initializer init = () -> { calls.incrementAndGet(); state.set(created); };
        assertTrue(XaeroStartupCompat.initializeIfMissing(state::get, init));
        assertFalse(XaeroStartupCompat.initializeIfMissing(state::get, init));
        assertEquals(1, calls.get()); assertSame(created, state.get());
    }
    @Test void failedNativeInitializationIsNotReportedAsSuccess() {
        assertThrows(IllegalStateException.class, () -> XaeroStartupCompat.initializeIfMissing(() -> null, () -> {}));
    }
}
