package com.mengsama.mod.mengsamanetmusic.gui;

import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongPaginationTest {
    @Test void eachPageContainsAtMostTwentySongsAndLastPageKeepsPhysicalSlots() {
        var data = IntStream.range(0, 54).boxed().toList();
        var pages = new SongPagination<Integer>(); pages.replace(data, true);
        assertEquals(3, pages.pages()); assertEquals(data.subList(0,20), pages.visible());
        pages.goTo(2); assertEquals(data.subList(20,40), pages.visible());
        pages.goTo(3); assertEquals(data.subList(40,54), pages.visible());
        assertEquals(54, data.size()); assertEquals(53, pages.visible().get(13));
    }
    @Test void boundaryCountsHaveNoExtraEmptyPage() {
        var pages = new SongPagination<Integer>();
        for (int total : new int[]{0,1,19,20,21,40,41,100}) {
            pages.replace(IntStream.range(0,total).boxed().toList(), true);
            assertEquals(Math.max(1,(total+19)/20), pages.pages());
            for(int i=1;i<=pages.pages();i++){pages.goTo(i);assertTrue(pages.visible().size()<=20);}
        }
    }
    @Test void jumpClampsZeroNegativeAndOversizedPageWithoutLosingItems() {
        var pages=new SongPagination<Integer>(); pages.replace(IntStream.range(0,54).boxed().toList(),true);
        pages.goTo(Integer.MAX_VALUE);assertEquals(3,pages.page());
        pages.goTo(0);assertEquals(1,pages.page());
        pages.goTo(Integer.MIN_VALUE);assertEquals(1,pages.page());assertEquals(54,pages.size());
    }
    @Test void deletingLastPageClampsToRemainingPageAndFilteringResetsToFirstPage() {
        var pages=new SongPagination<Integer>();pages.replace(IntStream.range(0,41).boxed().toList(),true);pages.goTo(3);
        pages.replace(IntStream.range(0,40).boxed().toList(),false);assertEquals(2,pages.page());assertEquals(20,pages.visible().get(0));
        pages.replace(List.of(2,17,53),true);assertEquals(1,pages.page());assertEquals(List.of(2,17,53),pages.visible());
    }
    @Test void asynchronousMetadataReplacementKeepsSelectedPage() {
        var pages=new SongPagination<String>();pages.replace(IntStream.range(0,60).mapToObj(i->"old-"+i).toList(),true);pages.goTo(2);
        pages.replace(IntStream.range(0,60).mapToObj(i->"new-"+i).toList(),false);
        assertEquals(2,pages.page());assertEquals("new-20",pages.visible().get(0));
    }
    @Test void pageSnapshotDoesNotMutateInputOrAllowExternalModification() {
        var source=new ArrayList<>(List.of(3,18,53));var pages=new SongPagination<Integer>();pages.replace(source,true);source.clear();
        assertEquals(List.of(3,18,53),pages.visible());assertThrows(UnsupportedOperationException.class,()->pages.visible().clear());
        pages.replace(null,false);assertEquals(1,pages.page());assertTrue(pages.visible().isEmpty());
    }
    @Test void navigationButtonsStayInsideContentAndAwayFromSongRowsAndPlaybackControls() {
        for(int[] size:new int[][]{{396,390},{360,300},{308,228},{600,500}}){
            var layout=new RosewoodPlayerLayout(size[0],size[1]);var bar=SongPageLayout.within(layout.content());
            var controls=List.of(bar.previous(),bar.summary(),bar.next(),bar.input(),bar.jump());
            for(var control:controls){
                assertTrue(control.x()>=layout.content().x()&&control.right()<=layout.content().right());
                assertTrue(control.bottom()<=layout.content().bottom());assertFalse(control.overlaps(layout.songContent()));
                assertFalse(control.overlaps(layout.band(RosewoodPlayerLayout.Part.CONTROLS)));
            }
            for(int i=0;i<controls.size();i++)for(int j=i+1;j<controls.size();j++)assertFalse(controls.get(i).overlaps(controls.get(j)));
        }
    }
}
