package com.mengsama.mod.mengsamanetmusic.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mengsama.mod.mengsamanetmusic.api.QqLoginService.LoginState.*;
import static org.junit.jupiter.api.Assertions.*;

 
class QqLoginGenerationTest {
    private final QqLoginState loginState = new QqLoginState();
    private final ExecutorService workers = Executors.newFixedThreadPool(2);

    @BeforeEach void clearSession() {
        loginState.listener(null);
        loginState.reset();
    }

    @AfterEach void cleanup() throws InterruptedException {
        workers.shutdownNow();
        assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        clearSession();
    }

    @Test void refreshedQrCannotBeOverwrittenByThePreviousDownload() throws Exception {
        long oldSession = loginState.generation();
        CountDownLatch downloadStarted = new CountDownLatch(1), releaseDownload = new CountDownLatch(1);
        Future<?> oldDownload = workers.submit(() -> {
            downloadStarted.countDown();
            await(releaseDownload);
            loginState.signature(oldSession, "synthetic-old-image-signature");
            loginState.phase(oldSession, WAITING_SCAN);
        });
        await(downloadStarted);
        loginState.reset();
        long newSession = loginState.generation();
        loginState.signature(newSession, "synthetic-new-image-signature");
        loginState.phase(newSession, FETCHING_QR);
        releaseDownload.countDown();

        assertCancelled(oldDownload);
        assertEquals("synthetic-new-image-signature", loginState.signature(newSession));
        assertEquals(FETCHING_QR, loginState.phase());
    }

    @Test void obsoletePollCannotNotifyTheReplacementScreenOrChangeItsDiagnostics() throws Exception {
        long oldSession = loginState.generation();
        CountDownLatch pollStarted = new CountDownLatch(1), releasePoll = new CountDownLatch(1);
        Future<?> oldPoll = workers.submit(() -> {
            pollStarted.countDown();
            await(releasePoll);
            loginState.phase(oldSession, SUCCESS);
        });
        await(pollStarted);
        loginState.reset();
        long newSession = loginState.generation();
        List<QqLoginService.LoginState> notifications = new CopyOnWriteArrayList<>();
        loginState.listener(notifications::add);
        loginState.phase(newSession, WAITING_SCAN);
        String newSummary = loginState.diagnostics();
        releasePoll.countDown();

        assertCancelled(oldPoll);
        assertThrows(CancellationException.class,
                () -> loginState.note(oldSession, "FAIL", -1, 0));
        assertEquals(List.of(WAITING_SCAN), notifications);
        assertEquals(WAITING_SCAN, loginState.phase());
        assertEquals(newSummary, loginState.diagnostics());
        assertEquals(QqLoginService.LoginError.NONE, loginState.error());
    }

    @Test void closingBeforeAnOldSuccessPreventsCredentialPublication() throws Exception {
        long oldSession = loginState.generation();
        AtomicInteger publishedCredentials = new AtomicInteger();
        CountDownLatch authorizationStarted = new CountDownLatch(1), finishAuthorization = new CountDownLatch(1);
        Future<?> oldSuccess = workers.submit(() -> {
            authorizationStarted.countDown();
            await(finishAuthorization);
            loginState.commit(oldSession, publishedCredentials::incrementAndGet);
        });
        await(authorizationStarted);
        loginState.reset();
        finishAuthorization.countDown();

        assertCancelled(oldSuccess);
        assertEquals(0, publishedCredentials.get());
        assertEquals(IDLE, loginState.phase());
    }

    @Test void resetAndCredentialCommitAreOneOrderedCriticalSection() throws Exception {
        long session = loginState.generation();
        AtomicInteger fakePersistedCredential = new AtomicInteger();
        List<String> order = new CopyOnWriteArrayList<>();
        CountDownLatch saveEntered = new CountDownLatch(1), finishSave = new CountDownLatch(1);
        CountDownLatch resetRequested = new CountDownLatch(1);
        Future<?> saving = workers.submit(() -> loginState.commit(session, () -> {
            order.add("save entered");
            saveEntered.countDown();
            await(finishSave);
            fakePersistedCredential.incrementAndGet();
            order.add("save finished");
        }));
        await(saveEntered);
        Future<?> loggingOut = workers.submit(() -> {
            resetRequested.countDown();
            loginState.reset();
             
            fakePersistedCredential.set(0);
            order.add("reset and logout");
        });
        await(resetRequested);
        finishSave.countDown();
        saving.get(5, TimeUnit.SECONDS);
        loggingOut.get(5, TimeUnit.SECONDS);

        assertEquals(List.of("save entered", "save finished", "reset and logout"), order);
        assertEquals(0, fakePersistedCredential.get());
        assertThrows(CancellationException.class,
                () -> loginState.commit(session, fakePersistedCredential::incrementAndGet));
    }

    @Test void obsoleteQueuedWorkStopsBeforeStartingAnotherRequest() throws Exception {
        long queuedSession = loginState.generation();
        AtomicInteger fakeNetworkRequests = new AtomicInteger();
        loginState.reset();
        Future<?> queued = workers.submit(() -> {
            loginState.require(queuedSession);
            fakeNetworkRequests.incrementAndGet();
        });
        assertCancelled(queued);
        assertEquals(0, fakeNetworkRequests.get());
        assertFalse(loginState.current(queuedSession));
        assertTrue(loginState.current(loginState.generation()));
    }

    @Test void resetRetainsTheListenerForTheNewQrAndCurrentSuccessCanCommit() {
        List<QqLoginService.LoginState> notifications = new CopyOnWriteArrayList<>();
        AtomicInteger publishedCredentials = new AtomicInteger();
        loginState.listener(notifications::add);
        loginState.reset();
        long session = loginState.generation();
        loginState.phase(session, WAITING_SCAN);
        loginState.commit(session, publishedCredentials::incrementAndGet);
        loginState.phase(session, SUCCESS);
        assertEquals(List.of(WAITING_SCAN, SUCCESS), notifications);
        assertEquals(1, publishedCredentials.get());
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for a controlled fake request"); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void assertCancelled(Future<?> future) {
        ExecutionException failure = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
        assertInstanceOf(CancellationException.class, failure.getCause());
    }
}
