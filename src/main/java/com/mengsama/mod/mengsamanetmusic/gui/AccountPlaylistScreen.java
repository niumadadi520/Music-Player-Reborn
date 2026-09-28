package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.api.*;
import com.mengsama.mod.mengsamanetmusic.api.qq.QqHttp;
import com.mengsama.mod.mengsamanetmusic.client.ClientFavoriteStore;
import com.mengsama.mod.mengsamanetmusic.util.AsyncIoExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

final class AccountPlaylistScreen extends CollectionPanelScreen {
    private final BiConsumer<SongInfo,Boolean> activate;
    private final AtomicLong requests=new AtomicLong();
    private final AccountLibrary api=new AccountLibrary(QqHttp.LIVE);
    private int provider=1,appleIndex;
    private List<AccountLibrary.Playlist> applePlaylists=List.of();
    private SongList list;
    private EditBox filter;
    private AbstractButton refresh,login,select;
    private List<SongInfo> songs=List.of();
    private String stamp="",libraryName="选择音乐平台后读取账号歌单";
    private boolean busy;
    private long started;
    AccountPlaylistScreen(Screen parent,BiConsumer<SongInfo,Boolean> activate){super(parent,"收藏 · 歌单");this.activate=activate;}
    private static String netEaseCookie(){ return NetEaseAccountSession.cookie(); }
    private String stamp(){return switch(provider){
        case 0 -> "n:"+netEaseCookie();
        case 1 -> "q:"+QqCredentialManager.revision()+":"+QqCredentialManager.hasValidCredential();
        default -> "a:"+AppleMusicKitAuthorization.revision()+":"+AppleMusicKitAuthorization.isAuthorized()+":"+ProviderAuthControls.developerToken();
    };}
    @Override protected void init(){
        super.init();
        int w=(panelWidth-32)/3;
        String[] labels={"网易云音乐","QQ 音乐","Apple Music"};
        for(int i=0;i<3;i++){int choice=i;button(labels[i],left+12+i*(w+4),top+36,w,()->changeProvider(choice));}
        login=button("登录 / 换号",left+12,top+60,92,this::login);
        refresh=button("读取 / 刷新",left+108,top+60,84,this::load);
        select=button("选择歌单",left+196,top+60,panelWidth-208,this::chooseApple);
        filter=new EditBox(font,left+16,top+88,panelWidth-32,18,Component.literal("筛选账号歌单"));
        filter.setMaxLength(128);filter.setHint(Component.literal("搜索歌名 / 歌手"));filter.setResponder(s->filterSongs());addRenderableWidget(filter);
        list=new SongList();addRenderableWidget(list);paging();load();
    }
    private void changeProvider(int choice){if(provider==choice)return;requests.incrementAndGet();provider=choice;appleIndex=0;applePlaylists=List.of();songs=List.of();load();}
    private void chooseApple(){if(provider==2 && !applePlaylists.isEmpty() && !busy){appleIndex=(appleIndex+1)%applePlaylists.size();load();}}
    private void login(){
        requests.incrementAndGet();busy=false;
        if(provider==1)minecraft.setScreen(new QqLoginScreen(this));
        else if(provider==0)minecraft.setScreen(new NetEaseQrLoginScreen(this));
        else if(AppleMusicKitAuthorization.isAuthorized()) {AppleMusicKitAuthorization.revoke(ProviderAuthControls.developerToken());applePlaylists=List.of();load();}
        else {
            String token=ProviderAuthControls.developerToken();
            if(!AppleMusicKitAuthorization.isDeveloperTokenUsable(token)){status="请先在模组配置中填写有效的 Apple MusicKit 开发者令牌";return;}
            status="请在浏览器中完成 Apple Music 授权";
            AppleMusicKitAuthorization.authorize(token).whenComplete((ok,error)->Minecraft.getInstance().execute(()->{
                if(minecraft.screen==this){if(Boolean.TRUE.equals(ok))load();else status="Apple 授权未完成，请重试";}
            }));
        }
    }
    private void load(){
        if(list==null)return;
        long request=requests.incrementAndGet();
        String account=stamp();boolean changed=!account.equals(stamp);stamp=account;
        if(changed){songs=List.of();applePlaylists=List.of();appleIndex=0;libraryName="选择音乐平台后读取账号歌单";}
        filterSongs();busy=true;started=System.nanoTime();status="正在读取，请稍候…";
        int selectedProvider=provider;
        QqCredential credential=QqCredentialManager.getCredential();String cookie=netEaseCookie();
        Map<String,String> appleHeaders=AppleMusicKitAuthorization.libraryHeaders(ProviderAuthControls.developerToken());
        if(selectedProvider==0 && cookie.isBlank()){busy=false;status="请点击登录，使用手机网易云音乐扫码";return;}
        if(selectedProvider==1 && (credential==null || !credential.isValid())){busy=false;status="请先点击登录，扫码登录自己的 QQ 音乐账号";return;}
        if(selectedProvider==2 && appleHeaders.isEmpty()){busy=false;status="请配置 MusicKit 并登录授权 Apple Music 后读取歌单";return;}
        List<AccountLibrary.Playlist> choices=applePlaylists;int selectedIndex=appleIndex;
        java.util.function.BooleanSupplier guard=()->requests.get()==request && account.equals(stamp());
        AsyncIoExecutor.supplyAsync(()->{
            try{
                if(selectedProvider==0)return new Loaded(api.netEaseLiked(cookie,guard),List.of(),0);
                if(selectedProvider==1)return new Loaded(api.qqLiked(credential,guard),List.of(),0);
                var available=choices.isEmpty()?api.applePlaylists(appleHeaders,guard):choices;
                if(available.isEmpty())return new Loaded(new AccountLibrary.Result("Apple Music",List.of(),"资料库中还没有歌单"),available,0);
                int index=Math.min(selectedIndex,available.size()-1);
                if(choices.isEmpty())for(int i=0;i<available.size();i++){
                    String name=available.get(i).name().toLowerCase(Locale.ROOT);
                    if(name.contains("favorite songs") || name.contains("喜欢") || name.contains("喜愛") || name.contains("最愛")){index=i;break;}
                }
                return new Loaded(api.applePlaylist(available.get(index),appleHeaders,guard),available,index);
            }catch(Exception failure){throw new java.util.concurrent.CompletionException(failure);}
        }).whenComplete((loaded,error)->Minecraft.getInstance().execute(()->{
            if(minecraft.screen!=this || !guard.getAsBoolean())return;
            busy=false;
            if(error!=null){status="读取失败：请确认对应账号已登录，再点击刷新";return;}
            songs=loaded.result.songs();libraryName=loaded.result.name();status=loaded.result.note()+" · "+songs.size()+" 首";
            applePlaylists=loaded.choices;appleIndex=loaded.index;filterSongs();
        }));
    }
    private record Loaded(AccountLibrary.Result result,List<AccountLibrary.Playlist> choices,int index){}
    private void filterSongs(){
        if(list==null)return;
        String query=filter==null?"":filter.getValue().toLowerCase(Locale.ROOT).strip();
        list.replaceSongs(songs.stream().filter(s->(s.songName+" "+String.join(" ",s.artists)).toLowerCase(Locale.ROOT).contains(query)).map(list::entry).toList(),true);
    }
    @Override public void tick(){
        super.tick();if(filter!=null)filter.tick();
        if(!stamp.equals(stamp()))load();
        if(busy && System.nanoTime()-started>180_000_000_000L){requests.incrementAndGet();busy=false;status="读取超时，请稍后重试";}
        refresh.active=!busy;select.active=provider==2 && applePlaylists.size()>1 && !busy;
        select.setMessage(Component.literal(provider==2?"切换歌单 ("+(appleIndex+1)+"/"+Math.max(1,applePlaylists.size())+")":"我喜欢的歌曲"));
    }
    @Override public void removed(){requests.incrementAndGet();busy=false;}
    @Override protected int page(){return list==null?1:list.page();}
    @Override protected int pages(){return list==null?1:list.pageCount();}
    @Override protected void go(int page){if(list!=null)list.goToPage(page);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        frame(g);super.render(g,mx,my,partial);
        g.drawString(font,font.plainSubstrByWidth(libraryName,panelWidth-28),left+14,top+111,QqLoginSkin.text(),false);
        if(songs.isEmpty())g.drawCenteredString(font,busy?"正在读取账号歌单…":"登录对应账号后，点击读取 / 刷新",left+panelWidth/2,top+135,QqLoginSkin.secondary());
    }
    private final class SongList extends PagedSongList<SongEntry>{
        SongList(){super(AccountPlaylistScreen.this.minecraft,panelWidth-24,panelHeight,top+124,Math.max(top+136,top+panelHeight-58),30);setLeftPos(left+12);setRenderBackground(false);setRenderTopAndBottom(false);}
        SongEntry entry(SongInfo song){return new SongEntry(song);}
        @Override public int getRowWidth(){return getWidth()-12;}
        @Override protected int getScrollbarPosition(){return getLeft()+getWidth()-6;}
    }
    private final class SongEntry extends ObjectSelectionList.Entry<SongEntry>{
        private final SongInfo song;private int x,y,w,h;
        SongEntry(SongInfo song){this.song=song;}
        @Override public void render(GuiGraphics g,int i,int y,int x,int w,int h,int mx,int my,boolean hovered,float partial){
            this.x=x;this.y=y;this.w=w;this.h=h;
            SongRowRenderer.renderAccount(g,font,song,x,y,w,h,hovered,ClientFavoriteStore.contains(song));
        }
        @Override public boolean mouseClicked(double mx,double my,int button){
            if(button!=0 || busy || !stamp.equals(stamp()))return false;
            if(SongRowRenderer.hitFavorite(mx,my,x,y,w,h))SongFavoriteActions.toggle(song,c->status=c.getString(),()->{});
            else{
                if("apple".equals(song.source) && (song.songUrl==null || song.songUrl.isBlank())){status="这首歌没有可用试听地址";return true;}
                boolean play=!SongRowRenderer.hitAction(mx,my,x,y,w,h);
                onClose();
                if(minecraft.screen==parent)activate.accept(song.clone(),play);
            }
            return true;
        }
        @Override public Component getNarration(){return Component.literal(song.songName+"；播放、加入设备歌单或收藏到本地");}
    }
    static final class NetEaseAccountScreen extends CollectionPanelScreen{
        private long importRequest;
        private boolean importing;
        NetEaseAccountScreen(Screen parent){super(parent,"网易云账号登录");}
        @Override protected void init(){
            super.init();status="登录凭据仅用于本机读取歌单，本次游戏有效";
            button("粘贴登录 Cookie 并读取",left+20,top+76,panelWidth-40,()->{
                if(importing)return;
                String value=minecraft.keyboardHandler.getClipboard().strip();
                if(value.isEmpty() || value.length()>16384 || value.chars().anyMatch(c->c<32 || c==127)){status="剪贴板不是有效的登录 Cookie";return;}
                importing=true;status="正在验证登录，失败会保留旧账号";
                long selected=++importRequest,revision=NetEaseAccountSession.revision();
                AsyncIoExecutor.supplyAsync(()->{
                    try{return NetEaseQrLogin.accountId(QqHttp.LIVE,value);}
                    catch(Exception error){throw new java.util.concurrent.CompletionException(error);}
                }).whenComplete((id,error)->Minecraft.getInstance().execute(()->{
                    if(minecraft.screen!=this || selected!=importRequest)return;
                    importing=false;
                    if(error!=null){status=NetEaseLoginFailure.shortStatus(error)+"；旧账号已保留";return;}
                    if(!NetEaseAccountSession.commit(revision,value)){status="账号已改变，请重新导入";return;}
                    onClose();
                }));
            });
            button("清除本次登录",left+20,top+102,panelWidth-40,()->{NetEaseAccountSession.clear();status="已清除本次登录；原有配置文件保持不变";});
            button("打开网易云官网",left+20,top+128,panelWidth-40,()->net.minecraft.Util.getPlatform().openUri("https://music.163.com/"));
        }
        @Override public void removed(){importRequest++;importing=false;super.removed();}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){frame(g);super.render(g,mx,my,partial);
            g.drawString(font,font.plainSubstrByWidth("使用自己已登录的网易云网页 Cookie",panelWidth-40),left+20,top+48,QqLoginSkin.text(),false);
        }
    }
}
