package com.mengsama.mod.mengsamanetmusic.api;

import java.util.function.Consumer;
import static com.mengsama.mod.mengsamanetmusic.api.QqLoginService.*;

 


public final class QqLoginState {

    private record Snapshot(String signature, LoginState phase, LoginError error, String diagnostics) {
    }

    private final QqLoginSessionGuard guard = new QqLoginSessionGuard();

    private volatile Snapshot snapshot = empty();

    private volatile Consumer<LoginState> listener;

    private static Snapshot empty() {
        return new Snapshot("", LoginState.IDLE, LoginError.NONE, "尚无请求");
    }

    public long reset() {
        return guard.renew(() -> snapshot = empty());
    }

    public long generation() {
        return guard.generation();
    }

    public boolean current(long token) {
        return guard.isCurrent(token);
    }

    public void require(long token) {
        guard.requireCurrent(token);
    }

    public void commit(long token, Runnable operation) {
        guard.commit(token, operation);
    }

    public void signature(long token, String value) {
        commit(token, () -> snapshot = new Snapshot(value == null ? "" : value, snapshot.phase(), snapshot.error(), snapshot.diagnostics()));
    }

    public String signature(long token) {
        return guard.read(token, () -> snapshot.signature());
    }

    public LoginState phase() {
        return snapshot.phase();
    }

    public LoginError error() {
        return snapshot.error();
    }

    public String diagnostics() {
        return snapshot.diagnostics();
    }

    public void listener(Consumer<LoginState> value) {
        listener = value;
    }

    public void phase(long token, LoginState phase) {
        commit(token, () -> {
            snapshot = new Snapshot(snapshot.signature(), phase, snapshot.error(), snapshot.diagnostics());
            var callback = listener;
            if (callback != null)
                callback.accept(phase);
        });
    }

    public void note(long token, String step, int status, int bytes) {
        commit(token, () -> snapshot = new Snapshot(snapshot.signature(), snapshot.phase(), snapshot.error(), step + " [HTTP " + status + ", " + bytes + " bytes]"));
    }

    public LoginState fail(long token, LoginError error) {
        commit(token, () -> {
            snapshot = new Snapshot(snapshot.signature(), LoginState.FAILED, error, snapshot.phase().name() + " / " + snapshot.diagnostics() + " / " + error.name());
            phase(token, LoginState.FAILED);
        });
        return LoginState.FAILED;
    }

    public void serviceCodes(long token, long outer, long operation) {
        commit(token, () -> snapshot = new Snapshot(snapshot.signature(), snapshot.phase(), snapshot.error(),
                snapshot.diagnostics() + " [service=" + outer + ", operation=" + operation + "]"));
    }
}
