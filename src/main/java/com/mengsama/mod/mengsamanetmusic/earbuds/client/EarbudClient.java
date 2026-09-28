package com.mengsama.mod.mengsamanetmusic.earbuds.client;
import net.neoforged.fml.common.EventBusSubscriber;
import com.mengsama.mod.mengsamanetmusic.earbuds.*;
import com.mengsama.mod.mengsamanetmusic.network.*;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import com.mengsama.mod.mengsamanetmusic.gui.TransparentButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.util.*;

@EventBusSubscriber(modid="mengsamanetmusic",value=Dist.CLIENT)
public final class EarbudClient {
    private static final EarbudVisualState VISUALS=new EarbudVisualState();
    public static final Map<UUID,CompoundTag> DEVICES=VISUALS.devices();
    private static final Set<String> PRIVATE_TARGETS=new HashSet<>();
    private static final Map<String,Integer> SOURCES=new HashMap<>();
    private static CompoundTag pending;
    private static long receivedAt,serverTick;
    private static boolean middle,latched;private static int heldTicks;
    public static boolean isPrivate(String target) { return PRIVATE_TARGETS.contains(target); }
    private static boolean sharing() {
        var player=Minecraft.getInstance().player;if(player==null)return false;
        for(var row:DEVICES.values()) if(row.hasUUID("Guest")&&(row.getUUID("Owner").equals(player.getUUID())||row.getUUID("Guest").equals(player.getUUID())))return true;
        return false;
    }
    public static void forget(String target) {PRIVATE_TARGETS.remove(target);SOURCES.remove(target);}
    public static int sourceEntity(String target,int fallback) {return SOURCES.getOrDefault(target,fallback);}
    public static void play(PlayerPlayMusicPacket packet) { PRIVATE_TARGETS.add(packet.targetId());SOURCES.put(packet.targetId(),packet.playerID());PlayerPlayMusicPacket.handleClient(packet); }
    public static double tick(float partial) {var mc=Minecraft.getInstance();return serverTick+(mc.level==null?0:mc.level.getGameTime()-receivedAt)+partial;}
    public static void receive(CompoundTag tag) {
        var mc=Minecraft.getInstance();
        switch(tag.getString("Kind")) {
            case "invite" -> pending=tag.copy();
            case "closeInvite" -> {
                UUID id=tag.getUUID("Id");if(pending!=null&&id.equals(pending.getUUID("Id")))pending=null;
                if(mc.screen instanceof ConsentScreen screen&&screen.id.equals(id)) {screen.responded=true;mc.setScreen(null);}
            }
            case "state" -> {
                VISUALS.replace(tag,mc.level,mc.level==null?0:mc.level.getGameTime());
                serverTick=tag.getLong("Tick");receivedAt=mc.level==null?0:mc.level.getGameTime();
            }
            case "removeDevice" -> {
                if(tag.hasUUID("Device") && VISUALS.removeDevice(tag.getUUID("Device"))) EarbudRender.clear();
            }
        }
    }
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Pre event) {
        var mc=Minecraft.getInstance();if(event.getButton()!=GLFW.GLFW_MOUSE_BUTTON_MIDDLE)return;
        if(event.getAction()==GLFW.GLFW_RELEASE){middle=false;heldTicks=0;latched=false;return;}
        if(mc.screen==null&&mc.player!=null&&(mc.player.getMainHandItem().getItem() instanceof MusicPlayerItem||sharing())) {middle=true;event.setCanceled(true);}
    }
    @SubscribeEvent public static void pick(InputEvent.InteractionKeyMappingTriggered event) {
        var mc=Minecraft.getInstance();if(event.isPickBlock()&&mc.player!=null&&(mc.player.getMainHandItem().getItem() instanceof MusicPlayerItem||sharing()))event.setCanceled(true);
    }
    @SubscribeEvent public static void clientTick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(VISUALS.expire(mc.level,mc.level==null?0:mc.level.getGameTime())) EarbudRender.clear();
        if(mc.level==null||mc.player==null)return;
        if(pending!=null&&mc.screen==null){mc.setScreen(new ConsentScreen(pending));pending=null;return;}
        if(!middle||latched||mc.screen!=null)return;
        if(sharing()) {if(++heldTicks>=12){latched=true;mc.setScreen(new ConnectionScreen());}return;}
        if(!(mc.hitResult instanceof EntityHitResult hit)||!(hit.getEntity() instanceof Player guest)||!EarbudSlots.installed(mc.player.getMainHandItem())) {heldTicks=0;return;}
        if(++heldTicks>=12){latched=true;mc.setScreen(new SideScreen(guest.getUUID(),guest.getScoreboardName()));}
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {VISUALS.clear();PRIVATE_TARGETS.clear();SOURCES.clear();pending=null;middle=false;latched=false;heldTicks=0;EarbudRender.clear();}
    private static abstract class PanelScreen extends com.mengsama.mod.mengsamanetmusic.gui.ThemedOverlayScreen {
        PanelScreen(String title){super(Component.literal(title));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int x,int y,float partial){renderTransparentBackground(g);EarbudScreen.panel(g,width/2-130,height/2-62,260,124);g.drawCenteredString(font,title,width/2,height/2-50,0xffffe2cf);super.render(g,x,y,partial);}
    }
    private static final class SideScreen extends PanelScreen {
        private final UUID target;private final String name;
        SideScreen(UUID target,String name){super("递出一只耳机");this.target=target;this.name=name;}
        @Override protected void init(){for(int side=0;side<2;side++){final int value=side;addRenderableWidget(TransparentButton.builder(Component.literal(side==0?"递出左耳":"递出右耳"),b->{ModNetwork.CHANNEL.sendToServer(new EarbudActionPacket(1,target,value));onClose();}).pos(width/2-112+side*116,height/2+10).size(108,22).build());}}
        @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);g.drawCenteredString(font,font.plainSubstrByWidth("邀请 "+name+" 一起听",236),width/2,height/2-19,0xff553e3c);}
    }
    private static final class ConnectionScreen extends PanelScreen {
        ConnectionScreen(){super("正在一起听");}
        @Override protected void init(){addRenderableWidget(TransparentButton.builder(Component.literal("结束一起听"),b->{ModNetwork.CHANNEL.sendToServer(new EarbudActionPacket(3,EarbudActionPacket.NONE,0));onClose();}).pos(width/2-108,height/2+8).size(216,22).build());}
        @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);g.drawCenteredString(font,"断开后，耳机仍保存在原随身听中",width/2,height/2-19,0xff553e3c);}
    }
    private static final class ConsentScreen extends PanelScreen {
        final UUID id;private final String name;private final boolean wired;private final int side;boolean responded;
        ConsentScreen(CompoundTag tag){super("一起听邀请");id=tag.getUUID("Id");name=tag.getString("Name");wired=tag.getBoolean("Wired");side=tag.getInt("Side");}
        private void reply(boolean accept){responded=true;ModNetwork.CHANNEL.sendToServer(new EarbudActionPacket(2,id,accept?1:0));minecraft.setScreen(null);}
        @Override protected void init(){addRenderableWidget(TransparentButton.builder(Component.literal("同意"),b->reply(true)).pos(width/2-112,height/2+20).size(108,22).build());addRenderableWidget(TransparentButton.builder(Component.literal("拒绝"),b->reply(false)).pos(width/2+4,height/2+20).size(108,22).build());}
        @Override public void onClose(){if(!responded)reply(false);else super.onClose();}
        @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);g.drawCenteredString(font,font.plainSubstrByWidth(name+" 想递给你"+(side==0?"左":"右")+"耳机",236),width/2,height/2-23,0xff553e3c);g.drawCenteredString(font,(wired?"有线：4 格":"蓝牙：32 格")+" · 只有你们能听见",width/2,height/2-5,0xff795d54);}
    }
}
