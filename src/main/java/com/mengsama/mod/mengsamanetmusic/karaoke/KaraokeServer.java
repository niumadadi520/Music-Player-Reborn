package com.mengsama.mod.mengsamanetmusic.karaoke;

import com.mengsama.mod.mengsamanetmusic.platform.ItemData;
import net.neoforged.fml.common.EventBusSubscriber;
import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerMenu;
import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerPlaylistMenu;
import com.mengsama.mod.mengsamanetmusic.karaoke.voice.KaraokeVoiceBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid="mengsamanetmusic")
public final class KaraokeServer {
    private static final class Session {
        final UUID microphone, nonce;
        final KaraokeBlockEntity block;
        final Map<KaraokeBlockEntity,UUID> channelIds = new HashMap<>();
        List<KaraokeVoiceBridge.Target> targets = List.of();
        long refreshAt;
        long lastVoice = System.nanoTime();
        Session(UUID microphone, UUID nonce, KaraokeBlockEntity block) {this.microphone=microphone;this.nonce=nonce;this.block=block;}
    }
    private record Claim(Object holder, UUID player) {}
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, UUID> OWNERS = new HashMap<>();
    private static final Map<UUID, Claim> CLAIMS = new HashMap<>();
    private static final Set<KaraokeBlockEntity> DEVICES = new HashSet<>();
    private static final Set<KaraokeBlockEntity> PENDING_LOADS = new HashSet<>();
    private static final Map<UUID, Set<KaraokeBlockEntity>> SPEAKERS = new HashMap<>();
    private static final Map<UUID, KaraokeState> LAST_STATE = new HashMap<>();
    private static final Map<UUID, UUID> NONCES = new ConcurrentHashMap<>();
    private static final Map<UUID, KaraokeFrameBudget> AUDIO_BUDGET = new ConcurrentHashMap<>();
    private static final Map<UUID, KaraokeFrameBudget> CONTROL_BUDGET = new ConcurrentHashMap<>();
    private static int ticks;
    private KaraokeServer() {}

    public static boolean allowVoice(UUID player, UUID nonce) {
        return nonce != null && nonce.equals(NONCES.get(player)) && AUDIO_BUDGET.computeIfAbsent(player,k->new KaraokeFrameBudget()).take(System.nanoTime());
    }
    public static boolean allowControl(UUID player) { return CONTROL_BUDGET.computeIfAbsent(player,k->new KaraokeFrameBudget()).take(System.nanoTime()); }
    public static void releaseClaim(UUID id, Object owner) { var c=CLAIMS.get(id);if(c!=null&&c.holder==owner)CLAIMS.remove(id); }
    private static boolean live(Claim claim, MinecraftServer server) {
        if (claim.holder instanceof KaraokeBlockEntity be) {
            if (!be.hasMicrophone() || be.isRemoved() || !(be.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return false;
            var pos=be.getBlockPos(); var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            return chunk!=null && chunk.getBlockEntity(pos,net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK)==be;
        }
        var player=server.getPlayerList().getPlayer(claim.player);
        if(player==null)return false;
        for(int i=0;i<player.getInventory().getContainerSize();i++) if(player.getInventory().getItem(i)==claim.holder)return true;
        return false;
    }
    public static void observeItem(ServerPlayer player, ItemStack stack) {
        UUID id=KaraokeMicrophoneItem.ensureId(stack,player.server);
        Claim other=CLAIMS.get(id);
        if(other!=null&&other.holder!=stack&&live(other,player.server)) {
            id=KaraokeData.get(player.server).create();
            ItemData.putUUID(stack, KaraokeMicrophoneItem.ID_TAG,id);
            ItemData.putUUID(stack, "MusicPlayerInstanceId", UUID.randomUUID());
        }
        CLAIMS.put(id,new Claim(stack,player.getUUID()));
    }
    public static void registerDevice(KaraokeBlockEntity device) {
        if(device.getLevel()==null||device.getLevel().isClientSide)return;
        PENDING_LOADS.remove(device);
        if(device.isStand()&&!device.hasMicrophone())return;
        UUID id=device.deviceId();
        if(device.hasMicrophone() && id!=null) {
            var server=device.getLevel().getServer();var other=CLAIMS.get(id);
            if(other!=null&&other.holder!=device&&live(other,server)) { id=KaraokeData.get(server).create();device.setDeviceId(id); }
            KaraokeData.get(server).remember(id); CLAIMS.put(id,new Claim(device,null));
        }
        DEVICES.add(device); updateSpeaker(device);
    }
    public static void updateSpeaker(KaraokeBlockEntity device) {
        invalidateTargets();
        for(var iterator=SPEAKERS.values().iterator();iterator.hasNext();) {
            var set=iterator.next();set.remove(device);if(set.isEmpty())iterator.remove();
        }
        if(device.isSpeaker()&&!device.isRemoved()) for(UUID id:device.connections()) SPEAKERS.computeIfAbsent(id,k->new HashSet<>()).add(device);
    }
    public static void unregisterDevice(KaraokeBlockEntity device) {
        PENDING_LOADS.remove(device);
        invalidateTargets();
        DEVICES.remove(device);
        for(var iterator=SPEAKERS.values().iterator();iterator.hasNext();) {var set=iterator.next();set.remove(device);if(set.isEmpty())iterator.remove();}
        UUID id=device.cachedDeviceId();if(id!=null)releaseClaim(id,device);
        UUID owner=OWNERS.get(id);
        if(owner!=null && SESSIONS.get(owner)!=null && SESSIONS.get(owner).block==device) {
            var server=device.getLevel().getServer();var player=server==null?null:server.getPlayerList().getPlayer(owner);
            stop(owner,player);
        }
    }
     
    public static void stopItemForTransfer(ServerPlayer player, ItemStack stack) {
        Session session=SESSIONS.get(player.getUUID());
        if(session!=null&&session.microphone.equals(KaraokeMicrophoneItem.getId(stack)))stop(player.getUUID(),player);
        com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem.stopForTransfer(stack,player);
        if(player.containerMenu instanceof MusicPlayerMenu menu && menu.resolveValidatedDevice(player)==stack)player.closeContainer();
    }
     
    public static void stopDeviceForTransfer(KaraokeBlockEntity device) {
        if(device.getLevel()==null||device.getLevel().isClientSide)return;
        unregisterDevice(device);
        device.stopForTransfer();
        var server=device.getLevel().getServer();
        if(server!=null)for(ServerPlayer viewer:server.getPlayerList().getPlayers())
            if(viewer.containerMenu instanceof MusicPlayerPlaylistMenu menu&&menu.getBlockEntity()==device)viewer.closeContainer();
    }
     
    public static void stopDeviceForUnload(KaraokeBlockEntity device) {
        if(device.getLevel()==null||device.getLevel().isClientSide)return;
        unregisterDevice(device);
        device.stopForUnload();
        var server=device.getLevel().getServer();
        if(server!=null&&server.isRunning()&&!server.isStopped())for(ServerPlayer viewer:server.getPlayerList().getPlayers())
            if(viewer.containerMenu instanceof MusicPlayerPlaylistMenu menu&&menu.getBlockEntity()==device)viewer.closeContainer();
    }
    public static void deferDeviceLoad(KaraokeBlockEntity device) {
        if(device.getLevel()!=null && !device.getLevel().isClientSide && device.getLevel().getServer()!=null
                && device.getLevel().getServer().isRunning() && !device.getLevel().getServer().isStopped()) PENDING_LOADS.add(device);
    }
    private static KaraokeBlockEntity menuBlock(ServerPlayer player) {
        return player.containerMenu instanceof MusicPlayerPlaylistMenu menu && menu.stillValid(player)
                && menu.getBlockEntity() instanceof KaraokeBlockEntity be && (!be.isStand()||be.hasMicrophone()) ? be : null;
    }
    private static ItemStack menuItem(ServerPlayer player) {
        if(player.containerMenu instanceof MusicPlayerMenu menu && menu.isPlayerHandContext()) {
            ItemStack stack=menu.resolveValidatedDevice(player);
            if(stack.getItem() instanceof KaraokeMicrophoneItem)return stack;
        }
        return ItemStack.EMPTY;
    }
    public static void action(ServerPlayer player,int menuId,KaraokeNetwork.Action action,String value) {
        if(player.containerMenu.containerId!=menuId || player.isSpectator())return;
        if(action==KaraokeNetwork.Action.STOP) { stop(player.getUUID(),player);sendState(player,"麦克风已关闭");return; }
        if(action==KaraokeNetwork.Action.REFRESH) { sendState(player,"");return; }
        KaraokeBlockEntity block=menuBlock(player);
        ItemStack item=menuItem(player);
        if(action==KaraokeNetwork.Action.TOGGLE) {
            UUID mic=block!=null&&!block.isSpeaker()?block.deviceId():item.isEmpty()?null:KaraokeMicrophoneItem.getId(item);
            if(mic==null)return;
            if("false".equals(value)) {
                Session session=SESSIONS.get(player.getUUID());
                if(session!=null&&session.microphone.equals(mic)) stop(player.getUUID(),player);
                sendState(player,"");return;
            }
            if(!"true".equals(value))return;
            if(!KaraokeVoiceBridge.available() || !KaraokeVoiceBridge.playerConnected(player.getUUID())) {sendState(player,"请先安装并连接 Simple Voice Chat，普通语音可保持静音");return;}
            if(block!=null&&player.distanceToSqr(Vec3.atCenterOf(block.getBlockPos()))>16) {sendState(player,"请靠近麦克风（4 格以内）");return;}
            UUID owner=OWNERS.get(mic);
            if(owner!=null&&!owner.equals(player.getUUID())) {sendState(player,"这支麦克风正在被其他玩家使用");return;}
            Session old=SESSIONS.get(player.getUUID());
            if(old!=null&&old.microphone.equals(mic)) {sendState(player,"");return;}
            if(old==null&&SESSIONS.size()>=64){sendState(player,"当前已有 64 位玩家正在 K 歌，请稍后再试");return;}
            stop(player.getUUID(),null);
            Session session=new Session(mic,UUID.randomUUID(),block);
            SESSIONS.put(player.getUUID(),session);OWNERS.put(mic,player.getUUID());NONCES.put(player.getUUID(),session.nonce);
            AUDIO_BUDGET.put(player.getUUID(),new KaraokeFrameBudget());
            if(block!=null)block.setMicrophoneActive(true);
            KaraokeNetwork.capture(player,session.nonce);sendState(player,"K 歌麦克风已开启，歌声从已连接的音响播放");return;
        }
        if(block==null||!block.isSpeaker())return;
        String message="";
        if(action==KaraokeNetwork.Action.SET_VOLUME) {
            try { int volume=Integer.parseInt(value);if(volume<0||volume>100)return;block.setVolume(volume);invalidateTargets(); }
            catch(NumberFormatException ignored){return;}
        } else {
            UUID id=KaraokeCode.parse(value);
            if(id==null){sendState(player,"连接码格式不正确，请复制麦克风的完整连接码");return;}
            if(action==KaraokeNetwork.Action.ADD_CONNECTION) {
                if(!KaraokeData.get(player.server).contains(id))message="找不到这个麦克风连接码";
                else if(block.connections().contains(id))message="这支麦克风已经连接";
                else if(!block.connect(id))message="每台音响最多连接 32 支麦克风";
                else message="麦克风已连接";
            } else if(action==KaraokeNetwork.Action.REMOVE_CONNECTION)block.disconnect(id);
            updateSpeaker(block);
        }
        sendState(player,message);
    }
    private static boolean valid(ServerPlayer player,Session session) {
        if(player==null||!player.isAlive()||player.isSpectator())return false;
        if(session.block!=null)return session.block.hasMicrophone()&&!session.block.isRemoved()&&session.block.getLevel()==player.level()
                &&session.microphone.equals(session.block.deviceId())&&player.distanceToSqr(Vec3.atCenterOf(session.block.getBlockPos()))<=16;
        for(ItemStack held:List.of(player.getMainHandItem(),player.getOffhandItem()))
            if(held.getItem() instanceof KaraokeMicrophoneItem&&session.microphone.equals(KaraokeMicrophoneItem.getId(held)))return true;
        return false;
    }
    public static void receiveVoice(ServerPlayer player,UUID nonce,byte[] opus) {
        Session session=SESSIONS.get(player.getUUID());
        if(session==null||!session.nonce.equals(nonce)||opus.length==0||opus.length>2048)return;
        if(!valid(player,session)){stop(player.getUUID(),player);return;}
        session.lastVoice=System.nanoTime();
        if(player.server.getTickCount() >= session.refreshAt) refreshTargets(player,session);
        for(var target:session.targets) {
            if(target.level().getBlockEntity(target.pos()) instanceof KaraokeBlockEntity speaker && speaker.isSpeaker())speaker.voicePulse();
        }
        KaraokeVoiceBridge.route(player.getUUID(),nonce,opus,session.targets);
    }
    private static void invalidateTargets() { for(var session:SESSIONS.values())session.refreshAt=0; }
    private static void refreshTargets(ServerPlayer player,Session session) {
        List<KaraokeVoiceBridge.Target> targets=new ArrayList<>();
        Set<KaraokeBlockEntity> linked=SPEAKERS.getOrDefault(session.microphone,Set.of());
        session.channelIds.keySet().retainAll(linked);
        for(KaraokeBlockEntity speaker:linked) {
            if(speaker.isRemoved()||speaker.volume()==0||!(speaker.getLevel() instanceof net.minecraft.server.level.ServerLevel level))continue;
             
            boolean listener=level.players().stream().anyMatch(p->p.distanceToSqr(Vec3.atCenterOf(speaker.getBlockPos()))<=KaraokeCode.SPEAKER_RANGE*KaraokeCode.SPEAKER_RANGE);
            if(!listener)continue;
            UUID channel=session.channelIds.computeIfAbsent(speaker,be->UUID.nameUUIDFromBytes(
                    (session.nonce+":"+level.dimension().location()+":"+speaker.getBlockPos().asLong()).getBytes(StandardCharsets.UTF_8)));
            targets.add(new KaraokeVoiceBridge.Target(channel,level,speaker.getBlockPos(),speaker.volume()));
            if(targets.size()>=64)break;
        }
        session.targets=List.copyOf(targets);session.refreshAt=player.server.getTickCount()+3;
    }
    public static void captureFailed(ServerPlayer player,UUID nonce,String reason) {
        Session session=SESSIONS.get(player.getUUID());
        if(session==null||!session.nonce.equals(nonce))return;
        stop(player.getUUID(),player);sendState(player,"麦克风已关闭："+reason.substring(0,Math.min(160,reason.length())));
    }
    private static void stop(UUID performer,ServerPlayer player) {
        Session session=SESSIONS.remove(performer);NONCES.remove(performer);AUDIO_BUDGET.remove(performer);
        if(session!=null){OWNERS.remove(session.microphone,performer);KaraokeVoiceBridge.endSession(performer,session.nonce);
            if(session.block!=null&&!session.block.isRemoved())session.block.setMicrophoneActive(false);}
        if(player!=null)KaraokeNetwork.capture(player,null);
    }
    public static KaraokeState state(ServerPlayer player,String message) {
        var block=menuBlock(player);var item=menuItem(player);var session=SESSIONS.get(player.getUUID());
        var kind=block!=null?(block.isSpeaker()?KaraokeState.Kind.SPEAKER:KaraokeState.Kind.STANDING)
                :item.isEmpty()?KaraokeState.Kind.NONE:KaraokeState.Kind.HANDHELD;
        UUID mic=block!=null&&!block.isSpeaker()?block.deviceId():!item.isEmpty()?KaraokeMicrophoneItem.getId(item):session==null?null:session.microphone;
        UUID owner=mic==null?null:OWNERS.get(mic);
        boolean mine=owner!=null&&owner.equals(player.getUUID());
        List<String> links=block!=null&&block.isSpeaker()?block.connections().stream().map(KaraokeCode::format).sorted().toList():List.of();
        return new KaraokeState(player.containerMenu.containerId,kind,mic,owner!=null,mine,
                KaraokeVoiceBridge.available()&&KaraokeVoiceBridge.playerConnected(player.getUUID()),block==null?80:block.volume(),links,message);
    }
    public static void sendState(ServerPlayer player,String message) {
        var state=state(player,message);LAST_STATE.put(player.getUUID(),state);KaraokeNetwork.sendState(player,state);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        
        MinecraftServer server=ServerLifecycleHooks.getCurrentServer();if(server==null||!server.isRunning()||server.isStopped())return;
         
        for(KaraokeBlockEntity device:new ArrayList<>(PENDING_LOADS)) {
            if(device.isRemoved() || device.getLevel()==null)PENDING_LOADS.remove(device);
            else device.tickLifecycle();
        }
        for(var entry:new ArrayList<>(SESSIONS.entrySet())) {
            var player=server.getPlayerList().getPlayer(entry.getKey());
            if(!valid(player,entry.getValue())||!KaraokeVoiceBridge.available()||!KaraokeVoiceBridge.playerConnected(entry.getKey()))stop(entry.getKey(),player);
            else if(System.nanoTime()-entry.getValue().lastVoice>10_000_000_000L) {
                stop(entry.getKey(),player);sendState(player,"麦克风没有返回音频，已自动关闭，请检查输入设备");
            }
        }
        KaraokeVoiceBridge.tick();
        if(++ticks%20==0) {
            CLAIMS.values().removeIf(claim->!live(claim,server));
            for(ServerPlayer player:server.getPlayerList().getPlayers())
                if(player.containerMenu instanceof MusicPlayerMenu||player.containerMenu instanceof MusicPlayerPlaylistMenu) {
                    var state=state(player,"");if(!state.equals(LAST_STATE.get(player.getUUID()))) {LAST_STATE.put(player.getUUID(),state);KaraokeNetwork.sendState(player,state);}
                }
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer player){stop(player.getUUID(),null);LAST_STATE.remove(player.getUUID());CONTROL_BUDGET.remove(player.getUUID());}
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        for(UUID player:new ArrayList<>(SESSIONS.keySet()))stop(player,null);
        CLAIMS.clear();DEVICES.clear();PENDING_LOADS.clear();SPEAKERS.clear();LAST_STATE.clear();CONTROL_BUDGET.clear();AUDIO_BUDGET.clear();NONCES.clear();OWNERS.clear();ticks=0;
    }
}
