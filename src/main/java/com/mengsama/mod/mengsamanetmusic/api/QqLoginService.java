package com.mengsama.mod.mengsamanetmusic.api;

import com.mengsama.mod.mengsamanetmusic.api.qq.*;
import com.mengsama.mod.mengsamanetmusic.util.AsyncIoExecutor;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

 


public final class QqLoginService {

    public enum LoginState {

        IDLE,
        FETCHING_QR,
        WAITING_SCAN,
        VERIFYING,
        AUTHORIZING,
        LOGGING_IN,
        SUCCESS,
        FAILED,
        QR_EXPIRED
    }

    public enum LoginError {

        NONE,
        QR_REQUEST_FAILED,
        QR_COOKIE_MISSING,
        POLL_REQUEST_FAILED,
        INVALID_CALLBACK,
        VERIFICATION_REQUEST_FAILED,
        SESSION_COOKIE_MISSING,
        OAUTH_REQUEST_FAILED,
        OAUTH_CODE_MISSING,
        MUSIC_LOGIN_FAILED,
        INVALID_CREDENTIAL
    }

    private static final QqLoginState SESSION = new QqLoginState();

    private static final QqAuthorizationFlow FLOW = new QqAuthorizationFlow(QqHttp.LIVE, SESSION, Clock.systemUTC(), QqCredentialManager::save);

    private static final ExecutorService WORKERS = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(16), job -> {
        Thread worker = new Thread(job, "MengSama-QQ-Authorization");
        worker.setDaemon(true);
        return worker;
    }, new ThreadPoolExecutor.AbortPolicy());

    private static CompletableFuture<LoginState> activePoll;

    private static long pollSession = -1;

    private QqLoginService() {
    }

    public static CompletableFuture<byte[]> fetchQrCode() {
        long token = SESSION.reset();
        SESSION.phase(token, LoginState.FETCHING_QR);
        return AsyncIoExecutor.supplyAsync(() -> {
            try {
                return FLOW.qr(token);
            } catch (CancellationException replaced) {
                throw replaced;
            } catch (Exception failure) {
                failWithDiagnostics(token, failure, LoginError.QR_REQUEST_FAILED);
                throw new CompletionException(failure);
            }
        }, WORKERS);
    }

    public static synchronized CompletableFuture<LoginState> pollLogin() {
        long token = SESSION.generation();
        if (activePoll != null && !activePoll.isDone() && pollSession == token)
            return activePoll;
        pollSession = token;
        activePoll = AsyncIoExecutor.supplyAsync(() -> {
            try {
                return FLOW.poll(token);
            } catch (CancellationException replaced) {
                throw replaced;
            } catch (Exception failure) {
                return failWithDiagnostics(token, failure, switch (SESSION.phase()) {
                    case VERIFYING -> LoginError.VERIFICATION_REQUEST_FAILED;
                    case AUTHORIZING -> LoginError.OAUTH_REQUEST_FAILED;
                    case LOGGING_IN -> LoginError.MUSIC_LOGIN_FAILED;
                    default -> LoginError.POLL_REQUEST_FAILED;
                });
            }
        }, WORKERS);
        return activePoll;
    }

    private static LoginError error(Exception failure, LoginError fallback) {
        return failure instanceof QqAuthorizationFlow.Failure typed ? typed.type : fallback;
    }

    private static LoginState failWithDiagnostics(long token, Exception failure, LoginError fallback) {
        LoginState result = SESSION.fail(token, error(failure, fallback));
         
        com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic.LOGGER.warn("[QQ登录] {} transport={}", SESSION.diagnostics(), failure.getClass().getSimpleName());
        return result;
    }

    public static final class QrSession {

        private QrSession() {
        }

        public static long generation() {
            return SESSION.generation();
        }

        public static boolean isCurrent(long token) {
            return SESSION.current(token);
        }













        public static LoginError getLastError() {
            return SESSION.error();
        }

        public static LoginState getCurrentStage() {
            return SESSION.phase();
        }

        public static String getSafeSummary() {
            return SESSION.diagnostics();
        }

        public static void setStateListener(Consumer<LoginState> listener) {
            SESSION.listener(listener);
        }



        public static void reset() {
            SESSION.reset();
        }
    }
}
