package com.mengsama.mod.mengsamanetmusic.hud;

import com.mengsama.mod.mengsamanetmusic.client.lyric.LyricRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class TwoLineLyricAnimatorTest {
    private static final float EPSILON = .00001F;
    private final TwoLineLyricAnimator animator = new TwoLineLyricAnimator();
    private final LyricRepository.LyricData lyrics = data(Map.of(
            0L, "First line", 1_000L, "Second line", 2_000L, "Third line"));

    @Test
    void adjacentCuePromotesPreviewAndCompletesAfter260Milliseconds() {
        assertEquals(List.of("First line", "Second line"), texts(frame(950, 0)));
        TwoLineLyricAnimator.Frame start = frame(1_000, 50);
        assertTrue(start.transitioning());
        assertEquals("First line", start.outgoing());
        assertEquals("Second line", start.current());
        assertEquals("Third line", start.next());
        assertEquals(0F, start.progress(), EPSILON);
        assertEquals(.5F, frame(1_100, 180).progress(), EPSILON);
        assertTrue(frame(1_200, 309).transitioning());

        TwoLineLyricAnimator.Frame completed = frame(1_200, 310);
        assertFalse(completed.transitioning());
        assertEquals(1F, completed.progress(), EPSILON);
        assertEquals("", completed.outgoing());
        assertEquals(List.of("Second line", "Third line"), texts(completed));
        assertEquals(0F, row(completed, "Second line").offsetRows(), EPSILON);
    }

    @Test
    void motionAdvancesEveryRenderFrameEvenWhenThePlaybackTickHasNotChanged() {
        startTransition();
        float previousOffset = 1F;
        float previousOpacity = .55F;
        for (int delta = 16; delta < 260; delta += 16) {
            TwoLineLyricAnimator.Row current = row(frame(1_000, 50 + delta), "Second line");
            assertTrue(current.offsetRows() < previousOffset, "Motion must not wait for a 50 ms sound tick");
            assertTrue(current.opacity() > previousOpacity);
            previousOffset = current.offsetRows();
            previousOpacity = current.opacity();
        }
    }

    @Test
    void transitionNeverDrawsMoreThanTwoVisibleLinesOrOverlappingGlyphBands() {
        startTransition();
         
        for (int delta = 0; delta <= 260; delta++) {
            List<TwoLineLyricAnimator.Row> rows = frame(1_000, 50 + delta).rows();
            assertTrue(rows.size() <= 2, "Three lines appeared at transition millisecond " + delta);
            for (TwoLineLyricAnimator.Row row : rows) {
                assertTrue(row.opacity() > 0F && row.opacity() <= 1F);
            }
            if (rows.size() == 2) {
                float separation = Math.abs(rows.get(0).offsetRows() - rows.get(1).offsetRows());
                assertTrue(separation >= .75F - EPSILON,
                        "Glyph bands overlap at transition millisecond " + delta + ": " + separation);
            }
        }
    }

    @Test
    void outgoingLineFadesBeforeTheFollowingPreviewAppears() {
        startTransition();
        float previousOpacity = 1F;
        for (int delta = 0; delta < 130; delta += 10) {
            TwoLineLyricAnimator.Frame frame = frame(1_000, 50 + delta);
            float opacity = row(frame, "First line").opacity();
            assertTrue(opacity <= previousOpacity);
            assertFalse(texts(frame).contains("Third line"));
            previousOpacity = opacity;
        }
        TwoLineLyricAnimator.Frame halfway = frame(1_000, 180);
        assertEquals(List.of("Second line"), texts(halfway));
        assertEquals(List.of("Second line", "Third line"), texts(frame(1_000, 250)));
    }

    @Test
    void pauseFreezesTheTransitionAndResumeDoesNotAccumulatePausedTime() {
        startTransition();
        TwoLineLyricAnimator.Frame moving = frame(1_050, 100);
        assertEquals(moving, animator.frame("target", "song", lyrics, 1_050, nanos(5_000), true));
        assertEquals(moving, animator.frame("target", "song", lyrics, 1_050, nanos(8_000), true));

        TwoLineLyricAnimator.Frame resumed = frame(1_050, 8_016);
        assertTrue(resumed.transitioning());
        assertEquals(66F / 260F, resumed.progress(), EPSILON);
        assertTrue(row(resumed, "Second line").offsetRows() < row(moving, "Second line").offsetRows());
    }

    @Test
    void backwardSeekCancelsAnInFlightTransitionAndSelectsTheCorrectCue() {
        startTransition();
        frame(1_050, 100);
        TwoLineLyricAnimator.Frame sought = frame(900, 116);
        assertFalse(sought.transitioning());
        assertEquals("", sought.outgoing());
        assertEquals(List.of("First line", "Second line"), texts(sought));
    }

    @Test
    void largeForwardSeekDoesNotAnimateThroughSkippedCues() {
        startTransition();
        TwoLineLyricAnimator.Frame sought = frame(2_500, 66);
        assertFalse(sought.transitioning());
        assertEquals("", sought.outgoing());
        assertEquals(List.of("Third line"), texts(sought));
    }

    @Test
    void targetSwitchNeverCarriesTheOtherDevicesOutgoingLine() {
        startTransition();
        TwoLineLyricAnimator.Frame switched = animator.frame("other-target", "song", lyrics,
                1_050, nanos(100), false);
        assertResetToSecondLine(switched);
    }

    @Test
    void songIdentitySwitchNeverCarriesThePreviousSongsOutgoingLine() {
        startTransition();
        TwoLineLyricAnimator.Frame switched = animator.frame("target", "other-song", lyrics,
                1_050, nanos(100), false);
        assertResetToSecondLine(switched);
    }

    @Test
    void newLyricDataResetsTheTransitionEvenWhenTheSongIdentityIsUnchanged() {
        startTransition();
        LyricRepository.LyricData replacement = data(lyrics.lines());
        assertNotSame(lyrics, replacement);
        TwoLineLyricAnimator.Frame switched = animator.frame("target", "song", replacement,
                1_050, nanos(100), false);
        assertResetToSecondLine(switched);
    }

    @Test
    void fastCuesSnapToTheNewestLineWithoutQueuingOldTransitions() {
        LyricRepository.LyricData fast = data(Map.of(0L, "A", 100L, "B", 200L, "C", 300L, "D"));
        animator.frame("target", "song", fast, 50, nanos(0), false);
        assertTrue(animator.frame("target", "song", fast, 100, nanos(50), false).transitioning());
        TwoLineLyricAnimator.Frame caughtUp = animator.frame("target", "song", fast, 200, nanos(150), false);
        assertFalse(caughtUp.transitioning());
        assertEquals("", caughtUp.outgoing());
        assertEquals(List.of("C", "D"), texts(caughtUp));
    }

    @Test
    void skippingMultipleCloselySpacedCuesAlsoSnapsEvenBelowTheSeekThreshold() {
        LyricRepository.LyricData fast = data(Map.of(0L, "A", 100L, "B", 200L, "C", 300L, "D"));
        animator.frame("target", "song", fast, 50, nanos(0), false);
        TwoLineLyricAnimator.Frame caughtUp = animator.frame("target", "song", fast, 200, nanos(150), false);
        assertFalse(caughtUp.transitioning());
        assertEquals(List.of("C", "D"), texts(caughtUp));
    }

    @Test
    void beforeTheFirstCueOnlyTheUpcomingLineIsShown() {
        LyricRepository.LyricData delayed = data(Map.of(1_000L, "First", 2_000L, "Last"));
        TwoLineLyricAnimator.Frame before = animator.frame("target", "song", delayed, 950, nanos(0), false);
        assertEquals("", before.current());
        assertEquals(List.of("First"), texts(before));
        assertEquals(1F, before.rows().get(0).offsetRows(), EPSILON);

        TwoLineLyricAnimator.Frame first = animator.frame("target", "song", delayed, 1_000, nanos(50), false);
        assertFalse(first.transitioning());
        assertEquals(List.of("First", "Last"), texts(first));
        assertEquals(0F, row(first, "First").offsetRows(), EPSILON);
    }

    @Test
    void lastCueDoesNotWrapAroundToTheBeginning() {
        TwoLineLyricAnimator.Frame end = frame(20_000, 0);
        assertEquals("Third line", end.current());
        assertEquals("", end.next());
        assertEquals(List.of("Third line"), texts(end));
        assertFalse(end.transitioning());
    }

    @Test
    void missingEmptyOrUnavailableLyricsAndInvalidPlaybackClearStaleRows() {
        LyricRepository.LyricData[] absent = {null, LyricRepository.LyricData.empty(false),
                LyricRepository.LyricData.empty(true)};
        for (LyricRepository.LyricData data : absent) {
            startTransition();
            TwoLineLyricAnimator.Frame cleared = animator.frame("target", "song", data, 1_050, nanos(100), false);
            assertEquals(TwoLineLyricAnimator.Frame.empty(), cleared);
            assertTrue(cleared.rows().isEmpty());
            assertFalse(cleared.hasText());
            assertResetToSecondLine(frame(1_050, 116));
        }
        startTransition();
        assertEquals(TwoLineLyricAnimator.Frame.empty(), frame(-1, 100));
        assertResetToSecondLine(frame(1_050, 116));
    }

    @Test
    void explicitResetAndBlankCuesNeverKeepAnOldVisibleLine() {
        startTransition();
        animator.reset();
        assertResetToSecondLine(frame(1_050, 100));

        LyricRepository.LyricData blank = data(Map.of(0L, "First\nTranslation", 1_000L, " ", 2_000L, "Last\rLine"));
        TwoLineLyricAnimator.Frame first = animator.frame("target", "song", blank, 950, nanos(120), false);
        assertEquals("First Translation", first.current());
        TwoLineLyricAnimator.Frame gap = animator.frame("target", "song", blank, 1_000, nanos(170), false);
        assertFalse(gap.transitioning());
        assertEquals("", gap.outgoing());
        assertEquals(List.of("Last Line"), texts(gap));
    }

    private void startTransition() {
        animator.reset();
        frame(950, 0);
        assertTrue(frame(1_000, 50).transitioning());
    }

    private TwoLineLyricAnimator.Frame frame(long playbackMillis, long renderMillis) {
        return animator.frame("target", "song", lyrics, playbackMillis, nanos(renderMillis), false);
    }

    private static long nanos(long milliseconds) { return milliseconds * 1_000_000L; }

    private static LyricRepository.LyricData data(Map<Long, String> lines) {
        return new LyricRepository.LyricData(new TreeMap<>(lines), false);
    }

    private static List<String> texts(TwoLineLyricAnimator.Frame frame) {
        return frame.rows().stream().map(TwoLineLyricAnimator.Row::text).toList();
    }

    private static TwoLineLyricAnimator.Row row(TwoLineLyricAnimator.Frame frame, String text) {
        return frame.rows().stream().filter(row -> row.text().equals(text)).findFirst().orElseThrow();
    }

    private static void assertResetToSecondLine(TwoLineLyricAnimator.Frame frame) {
        assertFalse(frame.transitioning());
        assertEquals("", frame.outgoing());
        assertEquals(List.of("Second line", "Third line"), texts(frame));
        assertEquals(0F, row(frame, "Second line").offsetRows(), EPSILON);
    }
}
