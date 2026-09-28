package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.api.SearchGeneration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

 
class GuiPlaybackRequestOrderTest {
    @Test void lateDetailsCannotReplaceASecondFavoriteThatAlreadyStartedPlaying() {
        SearchGeneration requests = new SearchGeneration();
        List<String> sent = new ArrayList<>();
        CompletableFuture<String> slowDetails = new CompletableFuture<>();
        long first = requests.begin("play");
        slowDetails.thenAccept(song -> {
            if (requests.isCurrent(first, "play")) sent.add(song);
        });

        requests.invalidate();  
        sent.add("B");
        slowDetails.complete("A");
        assertEquals(List.of("B"), sent);
    }

    @Test void newestDetailRequestWinsEvenForRepeatedClicksOnTheSameSong() {
        SearchGeneration requests = new SearchGeneration();
        long first = requests.begin("play");
        long second = requests.begin("play");
        assertFalse(requests.isCurrent(first, "play"));
        assertTrue(requests.isCurrent(second, "play"));
        requests.invalidate();  
        assertFalse(requests.isCurrent(second, "play"));
    }

    @Test void addingWithoutPlaybackAndIndependentSearchesDoNotSupersedeThePlayRequest() {
        SearchGeneration playbackRequests = new SearchGeneration();
        SearchGeneration searches = new SearchGeneration();
        long playback = playbackRequests.begin("play");
        List<String> added = new ArrayList<>();
        CompletableFuture<String> addOnly = new CompletableFuture<>();
        addOnly.thenAccept(added::add);  
        searches.begin("another query");
        searches.invalidate();
        addOnly.complete("saved song");
        assertEquals(List.of("saved song"), added);
        assertTrue(playbackRequests.isCurrent(playback, "play"));
    }
}
