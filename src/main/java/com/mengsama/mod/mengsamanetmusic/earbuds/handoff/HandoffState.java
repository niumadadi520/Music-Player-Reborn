package com.mengsama.mod.mengsamanetmusic.earbuds.handoff;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

 
public final class HandoffState {
    private final Map<UUID, Session> activeByPlayer = new HashMap<>();
    public enum Status { ACTIVE, COMMITTED, CANCELLED }
    public record Session(UUID id, UUID source, UUID receiver, HandoffMotion.Side side,
                          boolean wired, long startedTick, Status status) {
        public double seconds(long serverTick,float partialTick) {
            return Math.max(0,(serverTick+partialTick-startedTick)/20.0);
        }
        public Session withStatus(Status value) {
            return new Session(id,source,receiver,side,wired,startedTick,value);
        }
    }
    public interface InventoryTransaction {
         
        boolean canStart(UUID source,UUID receiver,HandoffMotion.Side side,boolean wired);
         
        boolean transferAtomically(Session session);
    }

    public Session begin(UUID source,UUID receiver,HandoffMotion.Side side,boolean wired,
                         long serverTick,InventoryTransaction items) {
        Objects.requireNonNull(source); Objects.requireNonNull(receiver); Objects.requireNonNull(side);
        if (source.equals(receiver) || activeByPlayer.containsKey(source) || activeByPlayer.containsKey(receiver)
                || !items.canStart(source,receiver,side,wired)) return null;
        Session session=new Session(UUID.randomUUID(),source,receiver,side,wired,serverTick,Status.ACTIVE);
        activeByPlayer.put(source,session); activeByPlayer.put(receiver,session);
        return session;
    }

     
    public Session tick(Session requested,long serverTick,boolean conditionsStillValid,InventoryTransaction items) {
        Session active=activeByPlayer.get(requested.source());
        if (active==null || !active.id().equals(requested.id())) return null;
        if (active.status()==Status.COMMITTED) {
            if (serverTick-active.startedTick()>=72) unlock(active);
            return active;  
        }
        if (!conditionsStillValid) return finish(active,Status.CANCELLED);
        if (serverTick-active.startedTick()<53) return active;
        if (!items.transferAtomically(active)) return finish(active,Status.CANCELLED);
        Session committed=active.withStatus(Status.COMMITTED);
        activeByPlayer.put(committed.source(),committed); activeByPlayer.put(committed.receiver(),committed);
        return committed;  
    }
     
    public Session cancel(UUID participant) {
        Session session=activeByPlayer.get(participant);
        return session==null?null:finish(session,session.status()==Status.COMMITTED?Status.COMMITTED:Status.CANCELLED);
    }
    private Session finish(Session session,Status status) {
        unlock(session);
        return session.withStatus(status);
    }
    private void unlock(Session session) {
        activeByPlayer.remove(session.source()); activeByPlayer.remove(session.receiver());
    }
}
