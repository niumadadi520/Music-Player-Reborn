package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.api.*;
import com.mengsama.mod.mengsamanetmusic.api.qq.QqHttp;
import com.mengsama.mod.mengsamanetmusic.util.AsyncIoExecutor;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

final class NetEaseQrLoginScreen extends Screen {
    private static final AtomicLong IDS=new AtomicLong();
    private final Screen parent;
    private NetEaseQrLogin api;
    private final NetEaseQrAttempt attempt=new NetEaseQrAttempt();
    private final ResourceLocation textureId=new ResourceLocation("mengsamanetmusic","netease_login_qr/"+IDS.incrementAndGet());
    private QqLoginLayout layout;
    private DynamicTexture texture;
    private int pixels;
    private String key="",failure="登录失败，请点击刷新重试";
    private String failureStatus="登录失败，悬停查看详情";
    private boolean verificationRequired;
    private net.minecraft.client.gui.components.AbstractButton primaryButton;
    private long accountRevision;
    NetEaseQrLoginScreen(Screen parent){super(Component.literal("网易云扫码登录"));this.parent=parent;}
    @Override protected void init(){
        layout=QqLoginLayout.fit(width,height);
        primaryButton=addRenderableWidget(QqLoginSkin.button(layout.button(0),Component.literal(verificationRequired?"网页登录":"刷新"),()->{
            if(verificationRequired)net.minecraft.Util.getPlatform().openUri("https://music.163.com/");
            else refresh();
        }));
        addRenderableWidget(QqLoginSkin.button(layout.button(1),Component.literal("Cookie"),()->minecraft.setScreen(new AccountPlaylistScreen.NetEaseAccountScreen(parent))));
        addRenderableWidget(QqLoginSkin.button(layout.button(2),Component.literal("返回"),this::onClose));
        if(attempt.state()==NetEaseQrAttempt.State.IDLE)refresh();
    }
    private void refresh(){
        release();key="";failure="登录失败，请点击刷新重试";verificationRequired=false;
        failureStatus="登录失败，悬停查看详情";primaryButton.setMessage(Component.literal("刷新"));accountRevision=NetEaseAccountSession.revision();
        long request=attempt.begin(Util.getMillis());
        NetEaseQrLogin selectedApi=new NetEaseQrLogin(QqHttp.LIVE);api=selectedApi;
        AsyncIoExecutor.supplyAsync(()->{
            try{return selectedApi.create();}catch(Exception e){throw new java.util.concurrent.CompletionException(e);}
        }).whenComplete((created,error)->Minecraft.getInstance().execute(()->{
            if(minecraft.screen!=this || !attempt.accepts(request,Util.getMillis()))return;
            if(error!=null){failed(request,error,NetEaseLoginFailure.Stage.CREATE);return;}
            try{
                var matrix=new QRCodeWriter().encode(NetEaseQrLogin.loginUrl(created),BarcodeFormat.QR_CODE,1,1,
                        Map.of(EncodeHintType.MARGIN,4,EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M));
                NativeImage image=new NativeImage(matrix.getWidth(),matrix.getHeight(),false);
                try{
                    for(int y=0;y<matrix.getHeight();y++)for(int x=0;x<matrix.getWidth();x++)image.setPixelRGBA(x,y,matrix.get(x,y)?0xFF000000:0xFFFFFFFF);
                    texture=new DynamicTexture(image);image=null;texture.setFilter(false,false);
                    minecraft.getTextureManager().register(textureId,texture);pixels=matrix.getWidth();key=created;
                    attempt.fetched(request,Util.getMillis());
                }finally{if(image!=null)image.close();}
            }catch(Exception invalid){failed(request,new NetEaseLoginFailure(NetEaseLoginFailure.Stage.IMAGE,NetEaseLoginFailure.Reason.IMAGE,0,0),NetEaseLoginFailure.Stage.IMAGE);}
        }));
    }
    @Override public void tick(){
        long request=attempt.poll(Util.getMillis());
        if(attempt.state()==NetEaseQrAttempt.State.EXPIRED)release();
        if(request<0 || key.isEmpty())return;
        String selected=key;long expected=accountRevision;NetEaseQrLogin selectedApi=api;
        AsyncIoExecutor.supplyAsync(()->{
            try{return selectedApi.check(selected);}catch(Exception e){throw new java.util.concurrent.CompletionException(e);}
        }).whenComplete((result,error)->Minecraft.getInstance().execute(()->{
            if(minecraft.screen!=this || !attempt.accepts(request,Util.getMillis()))return;
            if(error!=null){failed(request,error,NetEaseLoginFailure.Stage.POLL);return;}
            if(result.code()==803){
                if(!NetEaseAccountSession.commit(expected,result.cookie())){failure="账号状态已改变，请重新扫码";attempt.fail(request,Util.getMillis());release();return;}
                attempt.finish(request,803,Util.getMillis());release();onClose();return;
            }
            attempt.finish(request,result.code(),Util.getMillis());
            if(attempt.state()==NetEaseQrAttempt.State.EXPIRED || attempt.state()==NetEaseQrAttempt.State.FAILED)release();
        }));
    }
    private void failed(long request,Throwable error,NetEaseLoginFailure.Stage stage){
        failure=NetEaseLoginFailure.describe(error,stage);
        failureStatus=NetEaseLoginFailure.shortStatus(error);verificationRequired=NetEaseLoginFailure.requiresVerification(error);
        primaryButton.setMessage(Component.literal(verificationRequired?"网页登录":"刷新"));
        com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic.LOGGER.warn("[网易云扫码] {}",failure);
        attempt.fail(request,Util.getMillis());key="";release();
    }
    private String status(){return switch(attempt.state()){
        case FETCHING->"正在申请二维码…";case WAITING->"等待扫码";case SCANNED->"已扫码，请在手机上确认登录";
        case SUCCESS->"登录成功，正在返回歌单";case EXPIRED->"二维码已过期，请点击刷新";case FAILED->failureStatus;default->"点击刷新开始扫码";};}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);QqLoginSkin.renderPanel(g,layout);
        var title=layout.title();g.drawCenteredString(font,this.title,title.x()+title.width()/2,title.y(),QqLoginSkin.title());
        var subtitle=layout.subtitle();g.drawCenteredString(font,"使用手机网易云音乐扫码",subtitle.x()+subtitle.width()/2,subtitle.y(),QqLoginSkin.secondary());
        if(texture!=null){var area=QqLoginLayout.fitImage(layout.qrImageArea(),pixels,pixels);g.blit(textureId,area.x(),area.y(),area.width(),area.height(),0,0,pixels,pixels,pixels,pixels);}
        else if(verificationRequired){
            var area=layout.qrImageArea();
            String[] lines={"网易云要求安全验证","错误码：8821","请点击网页登录并完成验证","再通过 Cookie 导入登录"};
            int maxWidth=1;for(String line:lines)maxWidth=Math.max(maxWidth,font.width(line));
            float scale=Math.min(1F,Math.min(area.width()/(float)maxWidth,area.height()/52F));
            g.pose().pushPose();
            g.pose().translate(area.x()+area.width()/2F,area.y()+area.height()/2F,0);g.pose().scale(scale,scale,1);
            for(int i=0;i<lines.length;i++)g.drawCenteredString(font,lines[i],0,-24+i*13,QqLoginSkin.qrText());
            g.pose().popPose();
        }else{var area=layout.qrWhite();g.drawCenteredString(font,"二维码",area.x()+area.width()/2,area.y()+area.height()/2,QqLoginSkin.qrText());}
        var status=layout.status();g.drawCenteredString(font,font.plainSubstrByWidth(status(),status.width()),status.x()+status.width()/2,status.y(),QqLoginSkin.text());
        super.render(g,mx,my,partial);
        if(status.contains(mx,my))g.renderTooltip(font,font.split(Component.literal((attempt.state()==NetEaseQrAttempt.State.FAILED?failure:status())+"\n登录仅在本次游戏有效；换号失败保留旧账号。"),Math.min(250,width-24)),mx,my);
    }
    private void release(){if(texture!=null){texture=null;Minecraft.getInstance().getTextureManager().release(textureId);}pixels=0;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){PlayerScreenNavigation.returnTo(parent);}
    @Override public void removed(){attempt.close();key="";api=null;release();super.removed();}
}
