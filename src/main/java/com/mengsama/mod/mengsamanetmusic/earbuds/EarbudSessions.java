package com.mengsama.mod.mengsamanetmusic.earbuds;

import net.neoforged.fml.common.EventBusSubscriber;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import com.mengsama.mod.mengsamanetmusic.compat.BackpackAccess;
import com.mengsama.mod.mengsamanetmusic.network.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import java.util.*;

 
@EventBusSubscriber(modid="mengsamanetmusic")
public final class EarbudSessions {
    record Invitation(UUID id,UUID owner,UUID guest,UUID device,int side,boolean wired,long expires) {
        boolean mayAnswer(UUID sender,long now) { return guest.equals(sender) && now<=expires; }
    }
    private static final class Share {
        final Invitation invite; final long start; boolean committed; long lastWarning;
        Share(Invitation invite,long tick) { this.invite=invite;start=tick; }
    }
    private static final Map<UUID,Invitation> INVITES=new HashMap<>();
    private static final Map<UUID,Share> SHARES=new HashMap<>();
    private static final Map<UUID,Long> COOLDOWN=new HashMap<>();
    private static final Map<UUID,UUID> SELECTED=new HashMap<>();
    private static final Map<UUID,PlayerPlayMusicPacket> AUDIO=new HashMap<>();
    private EarbudSessions() {}
    public static ItemStack find(Player player,UUID id) {
        return com.mengsama.mod.mengsamanetmusic.compat.PortableDevices.find(player,id,false);
    }
    public static ItemStack device(Player player) {
        Share active=SHARES.get(player.getUUID());
        if(active!=null) return find(player,active.invite.device);
        ItemStack selected=find(player,SELECTED.get(player.getUUID()));
        ItemStack local=EarbudDeviceSelection.choose(selected,player.getMainHandItem(),player.getOffhandItem(),ItemStack.EMPTY);
        return local.isEmpty() ? BackpackAccess.earbudDevice(player) : local;
    }
    private static Share share(UUID participant) {
        for(Share s:SHARES.values()) if(s.invite.owner.equals(participant)||s.invite.guest.equals(participant)) return s;
        return null;
    }
    private static boolean busy(UUID id) {
        return share(id)!=null || INVITES.values().stream().anyMatch(i->i.owner.equals(id)||i.guest.equals(id));
    }
    private static void tell(ServerPlayer player,String message) { if(player!=null) player.displayClientMessage(Component.literal(message),true); }
    public static void invite(ServerPlayer owner,UUID guestId,int side) {
        if(side!=0 && side!=1) return;
        long tick=owner.server.getTickCount();
        if(tick<COOLDOWN.getOrDefault(owner.getUUID(),0L)) return;
        COOLDOWN.put(owner.getUUID(),tick+60);
        ServerPlayer guest=owner.server.getPlayerList().getPlayer(guestId);ItemStack stack=owner.getMainHandItem();
        if(guest==null || guest==owner || !guest.isAlive() || guest.isSpectator() || !owner.isAlive()
                || guest.level()!=owner.level() || owner.distanceToSqr(guest)>9 || !owner.hasLineOfSight(guest)
                || !EarbudSlots.installed(stack)) { tell(owner,"请手持已连接耳机的随身听，靠近并对准玩家"); return; }
         
        var toward=guest.getEyePosition().subtract(owner.getEyePosition()).normalize();
        if(owner.getLookAngle().dot(toward)<.75) return;
        if(EarbudSlots.mask(stack)!=3) { tell(owner,"一起听需要完整的一对耳机，请先装入左右耳");return; }
        if(busy(owner.getUUID())||busy(guestId)||EarbudSlots.installed(device(guest))) {tell(owner,"你或对方正在使用耳机，暂时无法递出");return;}
        var invitation=new Invitation(UUID.randomUUID(),owner.getUUID(),guestId,MusicPlayerItem.getOrCreateInstanceId(stack),side,EarbudSlots.wired(stack),tick+400);
        INVITES.put(invitation.id,invitation);
        CompoundTag tag=new CompoundTag(); tag.putString("Kind","invite");tag.putUUID("Id",invitation.id);tag.putString("Name",owner.getScoreboardName());tag.putBoolean("Wired",invitation.wired);tag.putInt("Side",side);
        ModNetwork.sendToClientPlayer(new EarbudStatePacket(tag),guest);tell(owner,"已发送邀请，等待对方同意或拒绝（20 秒）");
    }
    public static void reply(ServerPlayer guest,UUID invitationId,boolean accepted) {
        Invitation i=INVITES.get(invitationId);
        if(i==null || !i.mayAnswer(guest.getUUID(),guest.server.getTickCount())) return;
        INVITES.remove(invitationId); closeInvite(guest,i.id);
        ServerPlayer owner=guest.server.getPlayerList().getPlayer(i.owner);
        if(!accepted) { tell(owner,"对方拒绝了一起听邀请");return; }
        ItemStack stack=owner==null?ItemStack.EMPTY:owner.getMainHandItem();
        if(owner==null||guest.server.getTickCount()>i.expires||guest.isSpectator()||!guest.isAlive()||!owner.isAlive()
                ||owner.level()!=guest.level()||owner.distanceToSqr(guest)>9||!owner.hasLineOfSight(guest)
                ||!i.device.equals(MusicPlayerItem.getInstanceId(stack))||EarbudSlots.mask(stack)!=3
                ||EarbudSlots.wired(stack)!=i.wired||share(i.owner)!=null||share(i.guest)!=null||EarbudSlots.installed(device(guest))) {
            tell(guest,"邀请已失效，请重新靠近后邀请");tell(owner,"递耳机条件已改变，邀请已取消");return;
        }
        SELECTED.put(i.owner,i.device);SHARES.put(i.owner,new Share(i,guest.server.getTickCount()));
        tell(owner,"对方已同意，正在递出耳机，请稍候");tell(guest,"已同意，正在接过耳机");
        sync(guest.server);
    }
    private static void closeInvite(ServerPlayer player,UUID id) {
        if(player==null)return;CompoundTag tag=new CompoundTag();tag.putString("Kind","closeInvite");tag.putUUID("Id",id);ModNetwork.sendToClientPlayer(new EarbudStatePacket(tag),player);
    }
    public static void disconnect(ServerPlayer player,String reason) {
        Share s=share(player.getUUID()); if(s!=null) end(player.server,s,reason);
        var pending=new ArrayList<>(INVITES.values());
        for(Invitation i:pending) if(i.owner.equals(player.getUUID())||i.guest.equals(player.getUUID())) {
            INVITES.remove(i.id);closeInvite(player.server.getPlayerList().getPlayer(i.guest),i.id);
        }
    }
    private static void end(MinecraftServer server,Share s,String reason) {
        SHARES.remove(s.invite.owner);ServerPlayer owner=server.getPlayerList().getPlayer(s.invite.owner),guest=server.getPlayerList().getPlayer(s.invite.guest);
        if(guest!=null) { var audio=AUDIO.get(s.invite.owner); if(audio!=null) ModNetwork.sendToClientPlayer(new StopMusicPacketClient(audio.targetId()),guest); }
        tell(owner,reason);tell(guest,reason);
    }
     
    public static void detachDevice(MinecraftServer server, UUID device) {
        if (server == null || device == null || server.isStopped()) return;
        for (Share share : new ArrayList<>(SHARES.values()))
            if (device.equals(share.invite.device)) end(server,share,"随身听已从背包取出，一起听已断开");
        for (Invitation invite : new ArrayList<>(INVITES.values())) if (device.equals(invite.device)) {
            INVITES.remove(invite.id);closeInvite(server.getPlayerList().getPlayer(invite.guest),invite.id);
        }
        SELECTED.values().removeIf(device::equals);
        var iterator=AUDIO.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            if (!device.equals(com.mengsama.mod.mengsamanetmusic.compat.PlaybackTargetId.instanceId(entry.getValue().targetId()))) continue;
            ServerPlayer owner=server.getPlayerList().getPlayer(entry.getKey());
            if(owner!=null) ModNetwork.sendToClientPlayer(new StopMusicPacketClient(entry.getValue().targetId()),owner);
            iterator.remove();
        }
        CompoundTag removal=new CompoundTag();removal.putString("Kind","removeDevice");removal.putUUID("Device",device);
        for(ServerPlayer viewer:server.getPlayerList().getPlayers())
            ModNetwork.sendToClientPlayer(new EarbudStatePacket(removal),viewer);
    }
    public static void sendPrivate(ServerPlayer owner,ItemStack stack,Object packet) {
        if(owner==null) return;
        if(!EarbudSlots.installed(stack)) { ModNetwork.sendToClientPlayer(packet,owner);return; }
        UUID id=MusicPlayerItem.getOrCreateInstanceId(stack);
        Share s=share(owner.getUUID());
        if(s!=null && (!s.invite.owner.equals(owner.getUUID())||!s.invite.device.equals(id))) {
            if(packet instanceof PlayerPlayMusicPacket) end(owner.server,s,"播放设备已改变，一起听已结束");
            s=null;
        }
        if(packet instanceof PlayerPlayMusicPacket) SELECTED.put(owner.getUUID(),id);
        Object outgoing=packet;
        if(packet instanceof PlayerPlayMusicPacket play) {AUDIO.put(owner.getUUID(),play);outgoing=new EarbudAudioPacket(play);}
        ModNetwork.sendToClientPlayer(outgoing,owner);
        if(s!=null && s.committed) {
            ServerPlayer guest=owner.server.getPlayerList().getPlayer(s.invite.guest);
            if(guest!=null && guest.level()==owner.level() && EarbudSlots.inRange(s.invite.wired,owner.distanceToSqr(guest))) ModNetwork.sendToClientPlayer(outgoing,guest);
        }
        if(packet instanceof StopMusicPacketClient) {
            var audio=AUDIO.get(owner.getUUID());
            if(audio!=null && id.equals(com.mengsama.mod.mengsamanetmusic.compat.PlaybackTargetId.instanceId(audio.targetId()))) AUDIO.remove(owner.getUUID());
        }
    }
    private static void joinAudio(ServerPlayer owner,ServerPlayer guest,ItemStack stack) {
        PlayerPlayMusicPacket audio=AUDIO.get(owner.getUUID());
        if(audio==null||!MusicPlayerItem.isPlay(stack)) return;
        int elapsed=Math.max(0,audio.timeSecond()-Math.max(0,MusicPlayerItem.getCurrentTime(stack)-64)/20);
        var replay=new PlayerPlayMusicPacket(audio.playerID(),audio.targetId(),audio.url(),audio.timeSecond(),audio.songName(),audio.slot(),audio.requestGeneration(),0,audio.info(),false,false,elapsed);
        ModNetwork.sendToClientPlayer(new EarbudAudioPacket(replay),guest);
        if(MusicPlayerItem.isPaused(stack)) ModNetwork.sendToClientPlayer(new PauseMusicPacketClient(audio.targetId(),true,audio.requestGeneration()),guest);
    }
    public static boolean refreshShared(ServerPlayer guest,RefreshPlaybackPacket packet) {
        Share share=share(guest.getUUID());
        if(share==null||!share.committed||!share.invite.guest.equals(guest.getUUID()))return false;
        ServerPlayer owner=guest.server.getPlayerList().getPlayer(share.invite.owner);
        var audio=AUDIO.get(share.invite.owner);
        if(owner==null||audio==null||!audio.targetId().equals(packet.targetId())||packet.blockPos()!=null||owner.getId()!=packet.entityId()
                ||owner.level()!=guest.level()||!EarbudSlots.inRange(share.invite.wired,owner.distanceToSqr(guest)))return true;
        ItemStack device=find(owner,share.invite.device);
        if(device.isEmpty()||!EarbudSlots.installed(device))return true;
        var song=com.mengsama.mod.mengsamanetmusic.item.MusicListItem.getSongInfo(MusicPlayerItem.getCurrentCd(device));
        if(device.isEmpty()||!MusicPlayerItem.isPlay(device)||song==null||packet.song()==null||!song.sameIdentity(packet.song())||!song.canRefreshProvider())return true;
        if(BackpackAccess.refresh(owner,null,packet.targetId(),packet.requestGeneration(),packet.requestNonce(),song))return true;
        if(PlaybackRefreshSessions.consume(packet.targetId(),packet.requestGeneration(),packet.requestNonce(),song,owner.getUUID().toString())) {
            int elapsed=Math.max(0,song.songTime-Math.max(0,MusicPlayerItem.getCurrentTime(device)-64)/20);
            MusicPlayerItem.refreshToClient(device,song,owner,packet.requestNonce(),elapsed,MusicPlayerItem.isPaused(device));
        }
        return true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server=event.getServer();long tick=server.getTickCount();
        for(Invitation i:new ArrayList<>(INVITES.values())) if(tick>i.expires||server.getPlayerList().getPlayer(i.owner)==null||server.getPlayerList().getPlayer(i.guest)==null) {
            INVITES.remove(i.id);closeInvite(server.getPlayerList().getPlayer(i.guest),i.id);tell(server.getPlayerList().getPlayer(i.owner),"一起听邀请已超时或对方已离线");
        }
        for(Share s:new ArrayList<>(SHARES.values())) {
            ServerPlayer owner=server.getPlayerList().getPlayer(s.invite.owner),guest=server.getPlayerList().getPlayer(s.invite.guest);
            ItemStack stack=owner==null?ItemStack.EMPTY:find(owner,s.invite.device);
            if(owner==null||guest==null||!owner.isAlive()||!guest.isAlive()||owner.isSpectator()||guest.isSpectator()||owner.level()!=guest.level()
                    ||EarbudSlots.mask(stack)!=3||EarbudSlots.wired(stack)!=s.invite.wired||EarbudSlots.installed(device(guest))
                    ||!EarbudSlots.inRange(s.invite.wired,owner.distanceToSqr(guest))) {end(server,s,"一起听已断开；耳机保留在随身听中");continue;}
            if(!s.committed && (owner.getMainHandItem()!=stack || owner.distanceToSqr(guest)>9)) {end(server,s,"递耳机动作已取消，请保持靠近并手持随身听");continue;}
            if(!s.committed && tick-s.start>=53) {s.committed=true;joinAudio(owner,guest,stack);tell(owner,"已连接；长按中键可结束一起听");tell(guest,"已连接；长按中键可结束一起听");}
            if(EarbudSlots.nearLimit(s.invite.wired,owner.distanceToSqr(guest)) && tick-s.lastWarning>=40) {
                s.lastWarning=tick;String message=s.invite.wired?"耳机线快拉到极限了！超过 4 格将断开":"蓝牙信号变弱！超过 32 格将断开";tell(owner,message);tell(guest,message);
            }
        }
        if(tick%10==0) {
            var audioIterator=AUDIO.entrySet().iterator();
            while(audioIterator.hasNext()) {
                var entry=audioIterator.next();ServerPlayer player=server.getPlayerList().getPlayer(entry.getKey());
                ItemStack stack=player==null?ItemStack.EMPTY:find(player,com.mengsama.mod.mengsamanetmusic.compat.PlaybackTargetId.instanceId(entry.getValue().targetId()));
                if(player==null||!player.isAlive()||!EarbudSlots.installed(stack)||!MusicPlayerItem.isPlay(stack)) {
                    if(player!=null)ModNetwork.sendToClientPlayer(new StopMusicPacketClient(entry.getValue().targetId()),player);
                    audioIterator.remove();
                }
            }
            sync(server);
        }
        if(tick%200==0) {
            COOLDOWN.entrySet().removeIf(e->e.getValue()<tick);
            AUDIO.entrySet().removeIf(e->{ServerPlayer p=server.getPlayerList().getPlayer(e.getKey());return p==null||!MusicPlayerItem.isPlay(device(p));});
            SELECTED.keySet().removeIf(id->server.getPlayerList().getPlayer(id)==null);
        }
    }
    private static void sync(MinecraftServer server) {
        Map<ServerPlayer,CompoundTag> states=new LinkedHashMap<>();
        for(ServerPlayer owner:server.getPlayerList().getPlayers()) {
            if(!owner.isAlive()||owner.isSpectator())continue;
            ItemStack device=device(owner);if(!EarbudSlots.installed(device))continue;
            CompoundTag row=new CompoundTag();row.putUUID("Owner",owner.getUUID());row.putUUID("Device",MusicPlayerItem.getOrCreateInstanceId(device));
            row.putString("Attachment",com.mengsama.mod.mengsamanetmusic.compat.PortableDevices.attachment(owner,device));
            row.putBoolean("Wired",EarbudSlots.wired(device));row.putInt("Mask",EarbudSlots.mask(device));
            Share share=SHARES.get(owner.getUUID());
            if(share!=null) {row.putUUID("Guest",share.invite.guest);row.putUUID("Session",share.invite.id);row.putInt("Side",share.invite.side);row.putLong("Start",share.start);row.putBoolean("Committed",share.committed);}
            states.put(owner,row);
        }
        for(ServerPlayer viewer:server.getPlayerList().getPlayers()) {
            CompoundTag tag=new CompoundTag();tag.putString("Kind","state");tag.putLong("Tick",server.getTickCount());ListTag list=new ListTag();
            for(var entry:states.entrySet()) if(entry.getKey().level()==viewer.level()&&entry.getKey().distanceToSqr(viewer)<=64*64)list.add(entry.getValue().copy());
            tag.put("Devices",list);ModNetwork.sendToClientPlayer(new EarbudStatePacket(tag),viewer);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { INVITES.clear();SHARES.clear();COOLDOWN.clear();SELECTED.clear();AUDIO.clear(); }
}
