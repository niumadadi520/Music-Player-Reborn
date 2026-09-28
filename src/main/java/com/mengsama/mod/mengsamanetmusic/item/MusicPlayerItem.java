package com.mengsama.mod.mengsamanetmusic.item;

import com.mengsama.mod.mengsamanetmusic.platform.ItemData;
import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.block.PortableMusicPlayerBlock;
import com.mengsama.mod.mengsamanetmusic.block.MusicPlayerBlock;
import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerMenu;
import com.mengsama.mod.mengsamanetmusic.network.ModNetwork;
import com.mengsama.mod.mengsamanetmusic.network.PlayerPlayMusicPacket;
import com.mengsama.mod.mengsamanetmusic.util.AsyncIoExecutor;
import com.mengsama.mod.mengsamanetmusic.util.NetWorker;
import com.mengsama.mod.mengsamanetmusic.util.PlayMode;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MusicPlayerItem extends MusicDeviceBlockItem {
    private static final int CD_SLOTS = 54;
    private static final String PLAY_INDEX_KEY = "PlayIndex";
    private static final String PLAY_MODE_KEY = "PlayMode";
    private static final String IS_PLAY_KEY = "IsPlay";
    private static final String IS_PAUSED_KEY = "IsPaused";
    private static final String BROADCAST_KEY = "Broadcast";
    private static final String CURRENT_TIME_KEY = "CurrentTime";
    private static final String AUTO_ADVANCE_ARMED_KEY = "AutoAdvanceArmed";
    private static final String INSTANCE_ID_KEY = "MusicPlayerInstanceId";
    private static final java.util.concurrent.ConcurrentHashMap<java.util.UUID, java.util.concurrent.atomic.AtomicLong> PLAY_REQUEST_GENERATIONS = new java.util.concurrent.ConcurrentHashMap<>();

    public MusicPlayerItem(Block block, Properties properties) {
        this(block, properties, "pink_walkman");
    }

    protected MusicPlayerItem(Block block, Properties properties, String modelName) {
        super(block, properties, modelName);
    }

    @Override public boolean isPerspectiveAware() { return true; }

    public static String handheldPlaybackAnimation(ItemStack stack, net.minecraft.world.item.ItemDisplayContext view) {
        boolean firstPerson=view==net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || view==net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        return "animation.pink_walkman."+(firstPerson && stack!=null && !stack.isEmpty() && isPlay(stack) && !isPaused(stack)?"play":"idle");
    }

     
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return equipmentChanged(oldStack, newStack, slotChanged);
    }

    static boolean equipmentChanged(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (slotChanged || oldStack.isEmpty() != newStack.isEmpty() || oldStack.getItem() != newStack.getItem()) return true;
        java.util.UUID previous = getInstanceId(oldStack), next = getInstanceId(newStack);
         
        return previous != null && next != null && !previous.equals(next);
    }

     
    public static boolean supportsEarbuds(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof MusicPlayerItem
                && !(stack.getItem() instanceof com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeMicrophoneItem);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        BlockHitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockState targetState = level.getBlockState(hitResult.getBlockPos());
            if (targetState.getBlock() instanceof PortableMusicPlayerBlock ||
                    targetState.getBlock() instanceof MusicPlayerBlock) {
                return InteractionResultHolder.pass(stack);
            }
        }

        InteractionResultHolder<ItemStack> result = super.use(level, player, hand);
        if (result.getResult().consumesAction()) {
            return result;
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            java.util.UUID instanceId = getOrCreateInstanceId(stack);
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() {
                    return Component.translatable("item.mengsamanetmusic.music_player");
                }

                @Override
                public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player p) {
                    return MusicPlayerMenu.forPlayerHand(windowId, playerInv, instanceId);
                }
            }, buf -> {
                buf.writeByte(MusicPlayerMenu.Context.PLAYER_HAND.ordinal());
                buf.writeUUID(instanceId);
            });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean isSelected) {
        if (level.isClientSide) return;
        if (entity instanceof ServerPlayer wearer && !isBroadcast(stack)
                && !com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.canListen(wearer, stack)) {
            changeBroadcast(stack, wearer, true);
        }
        if (!isPlay(stack) || isPaused(stack)) return;
        tickTime(stack);
        int currentTime = getCurrentTime(stack);
        if (currentTime == 0 && ItemData.get(stack).getBoolean(AUTO_ADVANCE_ARMED_KEY)) {
            ItemData.putBoolean(stack, AUTO_ADVANCE_ARMED_KEY, false);
            advanceToNext(stack);
            ItemStack cd = getCurrentCd(stack);
            if (cd.isEmpty()) {
                setPlay(stack, false);
                broadcastStop(stack, entity);
                return;
            }
            SongInfo songInfo = MusicListItem.getSongInfo(cd);
            if (songInfo != null) {
                if (entity instanceof ServerPlayer sp) setPlayToClient(stack, songInfo, sp);
                else if (entity instanceof net.minecraft.world.entity.LivingEntity living)
                    setPlayToEntity(stack, songInfo, living);
            }
        }
    }

    public static long currentRequestGeneration(Entity entity) {
        java.util.concurrent.atomic.AtomicLong value = entity == null ? null : PLAY_REQUEST_GENERATIONS.get(entity.getUUID());
        return value == null ? 0L : value.get();
    }

    public static void setPlayToClient(ItemStack stack, SongInfo info, ServerPlayer player) {
        setPlayToClient(stack, info, player, 0L, 0);
    }

    public static void setPlayToClient(ItemStack stack, SongInfo info, ServerPlayer player, long refreshNonce) {
        setPlayToClient(stack, info, player, refreshNonce, 0);
    }

    public static void setPlayToClient(ItemStack stack, SongInfo info, ServerPlayer player, long refreshNonce, int startSecond) {
        setPlayToClient(stack, info, player, refreshNonce, startSecond, false);
    }

    public static void seekToClient(ItemStack stack, SongInfo info, ServerPlayer player, int startSecond, boolean paused) {
        setPlayToClient(stack, info, player, 0L, startSecond, paused);
    }
    public static void refreshToClient(ItemStack stack, SongInfo info, ServerPlayer player, long nonce, int second, boolean paused) {
        setPlayToClient(stack, info, player, nonce, second, paused);
    }

    private static void setPlayToClient(ItemStack stack, SongInfo info, ServerPlayer player, long refreshNonce,
                                        int startSecond, boolean preservePause) {
        ServerLevel serverLevel = player.serverLevel();
        if (!com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.canListen(player, stack)) setBroadcast(stack, true);
        SongInfo clone = info.clone();
        int inventorySlot = findInventorySlot(player, stack);
        java.util.UUID instanceId = getOrCreateInstanceId(stack);
        String targetId = "item:" + player.getUUID() + ":" + inventorySlot + ":" + instanceId;
        long requestGeneration = PLAY_REQUEST_GENERATIONS
                .computeIfAbsent(player.getUUID(), ignored -> new java.util.concurrent.atomic.AtomicLong())
                .updateAndGet(ignored -> com.mengsama.mod.mengsamanetmusic.network.PlaybackGenerations.next());
        setPlay(stack, true);
        setPaused(stack, preservePause);
        MengSamaNetMusic.LOGGER.info("[播放阶段] 请求已收 target={} generation={} source={}", targetId, requestGeneration, clone.source);
        resolveUrlAsync(clone).thenAcceptAsync(resolved -> {
            ItemStack currentStack = com.mengsama.mod.mengsamanetmusic.compat.PortableDevices.find(player, instanceId, true);
            try {
                if (!isPlay(currentStack) || PLAY_REQUEST_GENERATIONS.get(player.getUUID()).get() != requestGeneration
                        || !stillCarried(player, instanceId)) return;
                if (!hasPlayableUrl(resolved)) {
                    failPlayback(currentStack, player, refreshNonce != 0L ? "临时音源已失效" : "URL解析失败：未获得可播放地址");
                    return;
                }
                MengSamaNetMusic.LOGGER.info("[播放阶段] URL结果 target={} generation={} result=success host={}",
                        targetId, requestGeneration, safeUrlHost(resolved.songUrl));
                updateCurrentSongMetadata(currentStack, resolved, clone);
                int clampedStart = Math.max(0, Math.min(resolved.songTime, startSecond));
                setCurrentTime(currentStack, Math.max(1, (resolved.songTime - clampedStart) * 20 + 64));
                com.mengsama.mod.mengsamanetmusic.network.PlaybackRefreshSessions.publish(
                        targetId, requestGeneration, stableSongInfo(resolved, clone), player.getUUID().toString());
                boolean broadcast = isBroadcast(currentStack) || !com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.canListen(player, currentStack);
                if (broadcast) setBroadcast(currentStack, true);
                PlayerPlayMusicPacket msg = new PlayerPlayMusicPacket(
                        player.getId(), targetId, resolved.songUrl, resolved.songTime, resolved.songName,
                        getPlayIndex(currentStack), requestGeneration, refreshNonce, resolved, false, broadcast, clampedStart);
                if (broadcast) {
                    ModNetwork.sendToNearby(serverLevel, player.blockPosition(), msg);
                    MengSamaNetMusic.LOGGER.info("[播放阶段] 广播 target={} generation={} recipients=nearby", targetId, requestGeneration);
                } else {
                    com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSessions.sendPrivate(player, currentStack, msg);
                    MengSamaNetMusic.LOGGER.info("[播放阶段] 广播 target={} generation={} recipients=owner", targetId, requestGeneration);
                }
                if (isPaused(currentStack)) {
                    var pause = new com.mengsama.mod.mengsamanetmusic.network.PauseMusicPacketClient(
                            targetId, true, requestGeneration);
                    if (broadcast) ModNetwork.sendToNearby(serverLevel, player.blockPosition(), pause);
                    else com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSessions.sendPrivate(player, currentStack, pause);
                }
            } catch (Exception e) {
                failPlayback(currentStack, player, "播放请求处理异常");
                MengSamaNetMusic.LOGGER.error("[播放阶段] 广播异常 target={} generation={}", targetId, requestGeneration, e);
            }
        }, serverLevel.getServer()).exceptionally(error -> {
            serverLevel.getServer().execute(() -> {
                ItemStack current = com.mengsama.mod.mengsamanetmusic.compat.PortableDevices.find(player, instanceId, true);
                if (isPlay(current) && currentRequestGeneration(player) == requestGeneration)
                    failPlayback(current, player, "音乐请求繁忙或解析失败，请稍后重试");
            });
            return null;
        });
    }

    public static void setPlayToEntity(ItemStack stack, SongInfo info, net.minecraft.world.entity.LivingEntity entity) {
        setPlayToEntity(stack, info, entity, 0L, 0);
    }

    public static void setPlayToEntity(ItemStack stack, SongInfo info, net.minecraft.world.entity.LivingEntity entity, long refreshNonce) {
        setPlayToEntity(stack, info, entity, refreshNonce, 0);
    }

    public static void setPlayToEntity(ItemStack stack, SongInfo info, net.minecraft.world.entity.LivingEntity entity, long refreshNonce, int startSecond) {
        setPlayToEntity(stack, info, entity, refreshNonce, startSecond, false);
    }

    public static void seekToEntity(ItemStack stack, SongInfo info, net.minecraft.world.entity.LivingEntity entity,
                                    int startSecond, boolean paused) {
        setPlayToEntity(stack, info, entity, 0L, startSecond, paused);
    }

    private static void setPlayToEntity(ItemStack stack, SongInfo info, net.minecraft.world.entity.LivingEntity entity,
                                        long refreshNonce, int startSecond, boolean preservePause) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;
        SongInfo clone = info.clone();
        java.util.UUID instanceId = getOrCreateInstanceId(stack);
        String targetId = com.mengsama.mod.mengsamanetmusic.compat.EntityMusicDevice.targetId(entity, stack);
        long requestGeneration = PLAY_REQUEST_GENERATIONS
                .computeIfAbsent(entity.getUUID(), ignored -> new java.util.concurrent.atomic.AtomicLong())
                .updateAndGet(ignored -> com.mengsama.mod.mengsamanetmusic.network.PlaybackGenerations.next());
         
         
        setPlay(stack, true);
        setPaused(stack, preservePause);
        MengSamaNetMusic.LOGGER.info("[播放阶段] 请求已收 target={} generation={} source={}", targetId, requestGeneration, clone.source);
        com.mengsama.mod.mengsamanetmusic.compat.MaidMusicAccess.activate(entity, stack, targetId);
        resolveUrlAsync(clone).thenAcceptAsync(resolved -> {
            ItemStack current = com.mengsama.mod.mengsamanetmusic.compat.EntityMusicDevice.heldPlayer(entity);
            java.util.UUID currentInstance = current.isEmpty() ? null : getInstanceId(current);
            if (entity.isRemoved() || !instanceId.equals(currentInstance)
                    || !isPlay(current) || PLAY_REQUEST_GENERATIONS.get(entity.getUUID()).get() != requestGeneration) return;
            if (!hasPlayableUrl(resolved)) {
                failPlayback(current, entity, refreshNonce != 0L ? "临时音源已失效" : "URL解析失败：未获得可播放地址");
                return;
            }
            MengSamaNetMusic.LOGGER.info("[播放阶段] URL结果 target={} generation={} result=success host={}",
                    targetId, requestGeneration, safeUrlHost(resolved.songUrl));
            updateCurrentSongMetadata(current, resolved, clone);
            int clampedStart = Math.max(0, Math.min(resolved.songTime, startSecond));
            setCurrentTime(current, Math.max(1, (resolved.songTime - clampedStart) * 20 + 64));
            boolean maidSource = com.mengsama.mod.mengsamanetmusic.compat.MaidMusicAccess.isMaid(entity);
            String refreshOwner = entity instanceof ServerPlayer ? entity.getUUID().toString() : com.mengsama.mod.mengsamanetmusic.compat.MaidMusicAccess.ownerKey(entity);
            com.mengsama.mod.mengsamanetmusic.network.PlaybackRefreshSessions.publish(
                    targetId, requestGeneration, stableSongInfo(resolved, clone), refreshOwner);
            PlayerPlayMusicPacket msg = new PlayerPlayMusicPacket(entity.getId(), targetId, resolved.songUrl,
                    resolved.songTime, resolved.songName, -1, requestGeneration, refreshNonce, resolved, maidSource, false, clampedStart);
            com.mengsama.mod.mengsamanetmusic.compat.MaidMusicAccess.startLyrics(entity, targetId, resolved);
            ModNetwork.sendToNearby(serverLevel, entity.blockPosition(), msg);
            if (preservePause) ModNetwork.sendToNearby(serverLevel, entity.blockPosition(),
                    new com.mengsama.mod.mengsamanetmusic.network.PauseMusicPacketClient(
                            targetId, true, requestGeneration));
            MengSamaNetMusic.LOGGER.info("[播放阶段] 广播 target={} generation={} radius=96", targetId, requestGeneration);
        }, serverLevel.getServer()).exceptionally(error -> {
            serverLevel.getServer().execute(() -> {
                if (isPlay(stack) && currentRequestGeneration(entity) == requestGeneration)
                    failPlayback(stack, entity, "音乐请求繁忙或解析失败，请稍后重试");
            });
            MengSamaNetMusic.LOGGER.error("[播放阶段] URL解析异常 target={} generation={}", targetId, requestGeneration, error);
            return null;
        });
    }

     
    public static SongInfo stableSongInfo(SongInfo resolved, SongInfo stableSource) {
        SongInfo stable = (stableSource == null ? resolved : stableSource).clone();
        if (resolved != null) {
            if (resolved.songName != null && !resolved.songName.isBlank()) stable.songName = resolved.songName;
            if (resolved.songTime > 0) stable.songTime = resolved.songTime;
            if (resolved.artists != null && !resolved.artists.isEmpty()) stable.artists = new java.util.ArrayList<>(resolved.artists);
            if (resolved.picUrl != null && !resolved.picUrl.isBlank()) stable.picUrl = resolved.picUrl;
            if (resolved.coverUrl != null && !resolved.coverUrl.isBlank()) stable.coverUrl = resolved.coverUrl;
            if (resolved.albumMid != null && !resolved.albumMid.isBlank()) stable.albumMid = resolved.albumMid;
            if (resolved.albumName != null && !resolved.albumName.isBlank()) stable.albumName = resolved.albumName;
            if ((stable.source == null || stable.source.isBlank() || "unknown".equals(stable.source)) && resolved.source != null) stable.source = resolved.source;
            if ((stable.providerId == null || stable.providerId.isBlank()) && resolved.providerId != null) stable.providerId = resolved.providerId;
            if (stable.songId <= 0) stable.songId = resolved.songId;
        }
        stable.normalizeIdentity();
        String original = stableSource == null ? stable.rawUrl : stableSource.rawUrl;
        if (original == null || original.isBlank()) original = stableSource == null ? stable.songUrl : stableSource.songUrl;
        stable.rawUrl = original == null ? "" : original;
        stable.songUrl = stable.rawUrl;
        stable.playbackHeaders.clear();
        return stable;
    }

    private static void updateCurrentSongMetadata(ItemStack player, SongInfo resolved, SongInfo stableSource) {
        if (player == null || player.isEmpty() || resolved == null) return;
        SongInfo stable = stableSongInfo(resolved, stableSource);
        int slot = getPlayIndex(player);
        NonNullList<ItemStack> cds = loadAllCds(player);
        if (slot < 0 || slot >= cds.size()) return;
        ItemStack cd = cds.get(slot);
        if (cd.isEmpty()) return;
        if (cd.getItem() instanceof MusicListItem) MusicListItem.setSongInfo(stable, cd);
        saveCdToItem(player, slot, cd);
    }

    public static boolean hasPlayableUrl(SongInfo info) {
        if (info == null || info.songUrl == null || info.songUrl.isBlank()) return false;
        try {
            String protocol = new URI(info.songUrl).getScheme();
            return "http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String safeUrlHost(String url) {
        try { return new URI(url).getHost(); } catch (Exception ignored) { return "invalid"; }
    }

    private static void failPlayback(ItemStack stack, Entity entity, String reason) {
        if (!stack.isEmpty()) setCurrentTime(stack, 1);  
        com.mengsama.mod.mengsamanetmusic.compat.MaidMusicAccess.stopLyrics(entity);
        MengSamaNetMusic.LOGGER.warn("[播放阶段] 失败 entity={} reason={}", entity.getUUID(), reason);
        if (entity instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal("音乐播放失败：" + reason));
        } else {
            com.mengsama.mod.mengsamanetmusic.compat.MaidMusicAccess.notifyOwner(entity, reason);
        }
    }

    public static CompletableFuture<SongInfo> resolveUrlAsync(SongInfo stableInfo) {
         
        final SongInfo info = stableInfo == null ? new SongInfo() : stableInfo.clone();
        info.normalizeIdentity();
        return AsyncIoExecutor.supplyAsync(() -> {
            if ("qq".equals(info.source) && info.providerId != null && !info.providerId.isBlank()) {
                try {
                     
                     
                    String cookie = com.mengsama.mod.mengsamanetmusic.api.VipCookieState.getServerEffectiveVipCookie();
                    SongInfo refreshed = com.mengsama.mod.mengsamanetmusic.api.QqMusicUtils.resolveSong(info.providerId, cookie, 0);
                    if (refreshed != null && refreshed.songUrl != null && !refreshed.songUrl.isBlank()) {
                        if (refreshed.songName == null || refreshed.songName.isBlank()) refreshed.songName = info.songName;
                        if (refreshed.artists == null || refreshed.artists.isEmpty()) refreshed.artists = info.artists;
                        if (refreshed.songTime <= 0) refreshed.songTime = info.songTime;
                        if (refreshed.albumMid == null || refreshed.albumMid.isBlank()) refreshed.albumMid = info.albumMid;
                        if (refreshed.coverUrl == null || refreshed.coverUrl.isBlank()) refreshed.coverUrl = info.coverUrl;
                        if (refreshed.picUrl == null || refreshed.picUrl.isBlank()) refreshed.picUrl = info.picUrl;
                        if ((refreshed.coverUrl == null || refreshed.coverUrl.isBlank()) && refreshed.albumMid != null) {
                            refreshed.coverUrl = com.mengsama.mod.mengsamanetmusic.api.QqMusicUtils.buildAlbumCoverUrl(refreshed.albumMid, "");
                        }
                        if (refreshed.picUrl == null || refreshed.picUrl.isBlank()) refreshed.picUrl = refreshed.coverUrl;
                        refreshed.playbackHeaders.putAll(com.mengsama.mod.mengsamanetmusic.api.QqMusicUtils.playbackHeaders(cookie));
                        return refreshed;
                    }
                } catch (Exception e) {
                    MengSamaNetMusic.LOGGER.warn("Failed to refresh QQ URL for {}: {}", info.providerId, e.getMessage());
                }
            }
            if ("apple".equals(info.source)) {
                 
                 
                return com.mengsama.mod.mengsamanetmusic.api.AppleMusicApi.isSafePreviewUrl(info.songUrl)
                        ? info : new SongInfo("", info.songName, 0);
            }
            if ("netease".equals(info.source) && info.songId > 0) {
                try {
                    SongInfo refreshed = MengSamaNetMusic.NET_EASE_API.get163Song(info.songId);
                    if (refreshed != null && refreshed.songUrl != null && !refreshed.songUrl.isBlank()
                            && !refreshed.songUrl.equals(info.songUrl)) {
                        if (refreshed.songName == null || refreshed.songName.isBlank()) refreshed.songName = info.songName;
                        if (refreshed.artists == null || refreshed.artists.isEmpty()) refreshed.artists = info.artists;
                        if (refreshed.songTime <= 0) refreshed.songTime = info.songTime;
                        refreshed.playbackHeaders.putAll(com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic.NET_EASE_API.getRequestPropertyData());
                        return refreshed;
                    }
                } catch (Exception e) {
                    MengSamaNetMusic.LOGGER.warn("Failed to refresh NetEase URL for {}: {}", info.songId, e.getMessage());
                }
                String fallback = info.rawUrl == null || info.rawUrl.isBlank()
                        ? "https://music.163.com/song/media/outer/url?id=" + info.songId + ".mp3" : info.rawUrl;
                String metingUrl = com.mengsama.mod.mengsamanetmusic.api.MetingApi.getSongUrl(info.songId);
                info.songUrl = metingUrl != null && !metingUrl.isBlank() ? metingUrl : fallback;
                info.playbackHeaders.putAll(MengSamaNetMusic.NET_EASE_API.getRequestPropertyData());
                return info;
            }
            String url = info.songUrl;
            if (url == null || url.isBlank()) return info;
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return info;
            }
            try {
                if (url.contains("music.163.com") && url.contains("outer/url")) {
                    long songId = extractSongId(url);
                    if (songId > 0) {
                        MengSamaNetMusic.LOGGER.info("Resolving song URL for id: {}", songId);
                        String resolvedUrl = resolveRedirectUrl(url);
                        if (resolvedUrl != null && !resolvedUrl.equals(url) && !resolvedUrl.contains("404")) {
                            info.songUrl = resolvedUrl;
                            MengSamaNetMusic.LOGGER.info("Resolved song URL via redirect: {} -> {}", url, resolvedUrl);
                            return info;
                        }
                        String metingUrl = com.mengsama.mod.mengsamanetmusic.api.MetingApi.getSongUrl(songId);
                        if (metingUrl != null && !metingUrl.isEmpty()) {
                            info.songUrl = metingUrl;
                            MengSamaNetMusic.LOGGER.info("Resolved VIP song URL via Meting API: {} -> {}", url, metingUrl);
                            return info;
                        }
                        MengSamaNetMusic.LOGGER.warn("Failed to resolve URL for song id: {}", songId);
                    }
                } else {
                    String resolvedUrl = resolveRedirectUrl(url);
                    if (resolvedUrl != null && !resolvedUrl.equals(url)) {
                        info.songUrl = resolvedUrl;
                    }
                }
            } catch (Exception e) {
                MengSamaNetMusic.LOGGER.warn("Failed to resolve URL for song: {}", url, e);
            }
            return info;
        });
    }

    private static long extractSongId(String url) {
        try {
            int idIdx = url.indexOf("id=");
            if (idIdx >= 0) {
                String sub = url.substring(idIdx + 3);
                int dot = sub.indexOf(".mp3");
                if (dot > 0) sub = sub.substring(0, dot);
                return Long.parseLong(sub);
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static String resolveRedirectUrl(String urlString) {
        try {
            return NetWorker.resolveRedirect(urlString, 5,
                    MengSamaNetMusic.NET_EASE_API.getRequestPropertyData());
        } catch (Exception e) {
            MengSamaNetMusic.LOGGER.warn("Failed to resolve redirect for {}: {}", urlString, e.getMessage());
            return urlString;
        }
    }

     
    public static boolean removePlaylistEntry(ItemStack device, int slot) {
        NonNullList<ItemStack> cds = loadAllCds(device);
        if (slot < 0 || slot >= cds.size() || cds.get(slot).isEmpty()) return false;
        int oldIndex = getPlayIndex(device);
        ItemStack selected = oldIndex >= 0 && oldIndex < cds.size() ? cds.get(oldIndex) : ItemStack.EMPTY;
        boolean removedCurrent = slot == oldIndex;
        cds.set(slot, ItemStack.EMPTY);
        NonNullList<ItemStack> packed = packPlaylist(cds);
        int nextIndex = 0;
        if (!removedCurrent && !selected.isEmpty()) {
            for (int i = 0; i < packed.size(); i++) if (packed.get(i) == selected) { nextIndex = i; break; }
        } else {
            int before = 0;
            for (int i = 0; i < slot; i++) if (!cds.get(i).isEmpty()) before++;
            nextIndex = Math.min(before, Math.max(0, findLastNonEmpty(packed)));
        }
        saveAllCdsToItem(device, packed);
        setPlayIndex(device, nextIndex);
        if (removedCurrent) { setPlay(device, false); setCurrentTime(device, 0); }
        return true;
    }

    public static void saveAllCdsToItem(ItemStack playerItem, NonNullList<ItemStack> cds) {
        NonNullList<ItemStack> packed = packPlaylist(cds);
        CompoundTag previous = ItemData.child(playerItem, "Item");
        CompoundTag nbt = previous == null ? new CompoundTag() : previous.copy();
        ContainerHelper.saveAllItems(nbt, packed, com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup());
        ItemData.put(playerItem, "Item", nbt);
        int first = findFirstNonEmpty(packed);
        int last = findLastNonEmpty(packed);
        int index = getPlayIndex(playerItem);
        if (first < 0) {
            setPlayIndex(playerItem, 0);
            setPlay(playerItem, false);
            setCurrentTime(playerItem, 0);
        } else if (index < first || index > last || packed.get(index).isEmpty()) {
            setPlayIndex(playerItem, Math.min(Math.max(index, first), last));
        }
    }

     
    public static NonNullList<ItemStack> packPlaylist(List<ItemStack> source) {
        NonNullList<ItemStack> packed = NonNullList.withSize(CD_SLOTS, ItemStack.EMPTY);
        int out = 0;
        for (ItemStack item : source) {
            if (!item.isEmpty() && out < CD_SLOTS) packed.set(out++, item);
        }
        return packed;
    }

    public static void saveCdToItem(ItemStack playerItem, int slot, ItemStack cd) {
        NonNullList<ItemStack> items = NonNullList.withSize(CD_SLOTS, ItemStack.EMPTY);
        CompoundTag existing = ItemData.child(playerItem, "Item");
        if (existing != null) {
            com.mengsama.mod.mengsamanetmusic.platform.StoredItems.loadAll(existing, items, com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup());
        }
        items.set(slot, cd);
        saveAllCdsToItem(playerItem, items);
    }

    public static int getPlayIndex(ItemStack stack) {
        if (ItemData.has(stack) && ItemData.nullable(stack).contains(PLAY_INDEX_KEY)) {
            return ItemData.nullable(stack).getInt(PLAY_INDEX_KEY);
        }
        return 0;
    }

    public static void setPlayIndex(ItemStack stack, int index) {
        ItemData.putInt(stack, PLAY_INDEX_KEY, index);
    }

    public static PlayMode getPlayMode(ItemStack stack) {
        if (ItemData.has(stack) && ItemData.nullable(stack).contains(PLAY_MODE_KEY)) {
            return PlayMode.getMode(ItemData.nullable(stack).getInt(PLAY_MODE_KEY));
        }
        return PlayMode.SEQUENTIAL;
    }

    public static void setPlayMode(ItemStack stack, PlayMode mode) {
        ItemData.putInt(stack, PLAY_MODE_KEY, mode.ordinal());
    }

    public static boolean isPlay(ItemStack stack) {
        if (ItemData.has(stack) && ItemData.nullable(stack).contains(IS_PLAY_KEY)) {
            return ItemData.nullable(stack).getBoolean(IS_PLAY_KEY);
        }
        return false;
    }

    public static void setPlay(ItemStack stack, boolean play) {
        ItemData.putBoolean(stack, IS_PLAY_KEY, play);
        if (!play) ItemData.putBoolean(stack, IS_PAUSED_KEY, false);
    }

     
    public static void stopForTransfer(ItemStack stack, ServerPlayer player) {
        if (stack.isEmpty()) return;
        String target = targetId(player, stack);
        if (isPlay(stack)) PLAY_REQUEST_GENERATIONS.computeIfAbsent(player.getUUID(), ignored -> new java.util.concurrent.atomic.AtomicLong())
                .set(com.mengsama.mod.mengsamanetmusic.network.PlaybackGenerations.next());
        setPlay(stack, false); setPaused(stack, false); setCurrentTime(stack, 0);
        ItemData.remove(stack, AUTO_ADVANCE_ARMED_KEY);
        com.mengsama.mod.mengsamanetmusic.network.PlaybackRefreshSessions.release(target);
        broadcastStop(stack, player);
    }

     
    public static void stopDroppedDevice(ItemStack stack, ServerPlayer player) {
        java.util.UUID instance = getInstanceId(stack);
        if (instance == null || !isPlay(stack)) return;
        stopForTransfer(stack, player);
        com.mengsama.mod.mengsamanetmusic.network.PlaybackRefreshSessions.releaseDevice(instance);
        var stop = new com.mengsama.mod.mengsamanetmusic.network.StopMusicPacketClient("device:" + instance);
        ModNetwork.sendToNearby(player.serverLevel(), player.blockPosition(), stop);
        com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSessions.sendPrivate(player, stack, stop);
    }

    private static boolean stillCarried(Player player, java.util.UUID instance) {
        return player.isAlive() && !player.isRemoved()
                && !com.mengsama.mod.mengsamanetmusic.compat.PortableDevices.find(player, instance, true).isEmpty();
    }

     
    private static void broadcastStop(ItemStack stack, Entity source) {
        if (!(source.level() instanceof ServerLevel serverLevel)) return;
        String targetId;
        if (source instanceof ServerPlayer player) {
            targetId = targetId(player, stack);
            if (isBroadcast(stack)) {
                ModNetwork.sendToNearby(serverLevel, player.blockPosition(),
                        new com.mengsama.mod.mengsamanetmusic.network.StopMusicPacketClient(targetId));
            } else {
                com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSessions.sendPrivate(player, stack, new com.mengsama.mod.mengsamanetmusic.network.StopMusicPacketClient(targetId));
            }
            return;
        }
        if (source instanceof net.minecraft.world.entity.LivingEntity living) {
            targetId = com.mengsama.mod.mengsamanetmusic.compat.EntityMusicDevice.targetId(living, stack);
            ModNetwork.sendToNearby(serverLevel, living.blockPosition(),
                    new com.mengsama.mod.mengsamanetmusic.network.StopMusicPacketClient(targetId));
            com.mengsama.mod.mengsamanetmusic.compat.MaidMusicAccess.stopLyrics(living);
        }
    }

    public static boolean isPaused(ItemStack stack) {
        return ItemData.has(stack) && ItemData.nullable(stack).getBoolean(IS_PAUSED_KEY);
    }

     
    public static void setPaused(ItemStack stack, boolean paused) {
        ItemData.putBoolean(stack, IS_PAUSED_KEY, paused);
    }

    public static boolean isBroadcast(ItemStack stack) {
        return !com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.installed(stack) && (!ItemData.has(stack) || !ItemData.nullable(stack).contains(BROADCAST_KEY) || ItemData.nullable(stack).getBoolean(BROADCAST_KEY));
    }

     
    public static void changeBroadcast(ItemStack stack, ServerPlayer player, boolean requested) {
        boolean broadcast = !com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.installed(stack) && (requested || !com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.canListen(player, stack));
        boolean previous = isBroadcast(stack);
        setBroadcast(stack, broadcast);
        if (previous == broadcast) return;
        if (!broadcast) {
            ModNetwork.sendToNearby(player.serverLevel(), player.blockPosition(),
                    new com.mengsama.mod.mengsamanetmusic.network.PlayerHudVisibilityPacket(targetId(player, stack), false));
        } else if (isPlay(stack)) {
            ItemStack cd = getCurrentCd(stack);
            SongInfo song = cd.getItem() instanceof MusicListItem ? MusicListItem.getSongInfo(cd) : null;
            if (song != null) {
                int remaining = getCurrentTime(stack);
                int elapsed = remaining <= 0 ? 0 : Math.max(0, song.songTime - Math.max(0, remaining - 64) / 20);
                seekToClient(stack, song, player, elapsed, isPaused(stack));
            }
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    public static void setBroadcast(ItemStack stack, boolean broadcast) {
        ItemData.putBoolean(stack, BROADCAST_KEY, broadcast);
    }

    public static int getCurrentTime(ItemStack stack) {
        if (ItemData.has(stack) && ItemData.nullable(stack).contains(CURRENT_TIME_KEY)) {
            return ItemData.nullable(stack).getInt(CURRENT_TIME_KEY);
        }
        return 0;
    }

    public static void setCurrentTime(ItemStack stack, int time) {
        ItemData.putInt(stack, CURRENT_TIME_KEY, time);
        ItemData.putBoolean(stack, AUTO_ADVANCE_ARMED_KEY, time > 0);
    }

    public static void tickTime(ItemStack stack) {
        int ct = getCurrentTime(stack);
        if (ct > 0) {
             
            ItemData.putInt(stack, CURRENT_TIME_KEY, ct - 1);
        }
    }

    public static int findInventorySlot(Player player, ItemStack target) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i) == target) return i;
        }
        return -1;
    }

     
    @Nullable
    public static java.util.UUID getInstanceId(ItemStack stack) {
        CompoundTag tag = ItemData.nullable(stack);
        return tag != null && tag.hasUUID(INSTANCE_ID_KEY) ? tag.getUUID(INSTANCE_ID_KEY) : null;
    }

    public static java.util.UUID getOrCreateInstanceId(ItemStack stack) {
        java.util.UUID existing = getInstanceId(stack);
        if (existing != null) return existing;
        java.util.UUID created = java.util.UUID.randomUUID();
        ItemData.putUUID(stack, INSTANCE_ID_KEY, created);
        return created;
    }

    public static String targetId(Player player, ItemStack stack) {
        return "item:" + player.getUUID() + ":" + findInventorySlot(player, stack) + ":" + getOrCreateInstanceId(stack);
    }

    @NotNull
    public static ItemStack findMusicPlayerItem(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof MusicPlayerItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static NonNullList<ItemStack> loadAllCds(ItemStack stack) {
        NonNullList<ItemStack> items = NonNullList.withSize(CD_SLOTS, ItemStack.EMPTY);
        CompoundTag nbt = ItemData.child(stack, "Item");
        if (nbt != null) {
            com.mengsama.mod.mengsamanetmusic.platform.StoredItems.loadAll(nbt, items, com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup());
        }
        return items;
    }

    public static ItemStack getCurrentCd(ItemStack stack) {
        NonNullList<ItemStack> cds = loadAllCds(stack);
        int index = getPlayIndex(stack);
        if (index >= 0 && index < cds.size()) {
            ItemStack cd = cds.get(index);
            if (!cd.isEmpty()) return cd;
        }
        for (int i = 0; i < cds.size(); i++) {
            if (!cds.get(i).isEmpty()) {
                setPlayIndex(stack, i);
                return cds.get(i);
            }
        }
        return ItemStack.EMPTY;
    }

    public static void advanceToNext(ItemStack stack) {
        NonNullList<ItemStack> cds = loadAllCds(stack);
        int currentSlot = getPlayIndex(stack);
        int currentSong = songIndex(cds, currentSlot);
        PlayMode.TrackPosition next = PlayMode.nextTrack(getPlayMode(stack), currentSlot, currentSong,
                songCounts(cds), bound -> java.util.concurrent.ThreadLocalRandom.current().nextInt(bound));
        setPlayIndex(stack, next.slotIndex());
        setSongIndex(cds, next);
        saveAllCdsPreservingSlots(stack, cds);
    }

    static int[] songCounts(NonNullList<ItemStack> cds) {
        int[] counts = new int[cds.size()];
        for (int i = 0; i < cds.size(); i++) {
            ItemStack cd = cds.get(i);
            if (cd.getItem() instanceof MusicListItem) counts[i] = MusicListItem.getSongCount(cd);
            else if (!cd.isEmpty()) counts[i] = 1;
        }
        return counts;
    }

    private static int songIndex(NonNullList<ItemStack> cds, int slot) {
        if (slot < 0 || slot >= cds.size()) return 0;
        ItemStack cd = cds.get(slot);
        return cd.getItem() instanceof MusicListItem ? MusicListItem.getSongIndex(cd) : 0;
    }

    private static void setSongIndex(NonNullList<ItemStack> cds, PlayMode.TrackPosition position) {
        if (position.slotIndex() < 0 || position.slotIndex() >= cds.size()) return;
        ItemStack cd = cds.get(position.slotIndex());
        if (cd.getItem() instanceof MusicListItem) MusicListItem.setSongIndex(cd, position.songIndex());
    }

    public static void saveAllCdsPreservingSlots(ItemStack stack, NonNullList<ItemStack> cds) {
        CompoundTag nbt = ItemData.childOrEmpty(stack, "Item");
        ContainerHelper.saveAllItems(nbt, cds, com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup());
        ItemData.put(stack, "Item", nbt);
    }

    private static int findFirstNonEmpty(NonNullList<ItemStack> cds) {
        for (int i = 0; i < CD_SLOTS; i++) {
            if (!cds.get(i).isEmpty()) return i;
        }
        return -1;
    }

    private static int findLastNonEmpty(NonNullList<ItemStack> cds) {
        for (int i = CD_SLOTS - 1; i >= 0; i--) {
            if (!cds.get(i).isEmpty()) return i;
        }
        return -1;
    }

}
