package com.mengsama.mod.mengsamanetmusic.compat;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.block.IMusicPlayerBlockEntity;
import com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicPlayback;
import com.mengsama.mod.mengsamanetmusic.client.lyric.ClientLyricStore;
import com.mengsama.mod.mengsamanetmusic.block.MusicPlayerBlock;
import com.mengsama.mod.mengsamanetmusic.block.PortableMusicPlayerBlock;
import com.mengsama.mod.mengsamanetmusic.item.MusicListItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.Identifiers;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

 
@WailaPlugin
public final class JadeMusicPlayerPlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(Provider.INSTANCE,
                com.mengsama.mod.mengsamanetmusic.block.MusicPlayerBlockEntity.class);
        registration.registerBlockDataProvider(Provider.INSTANCE,
                com.mengsama.mod.mengsamanetmusic.block.PortableMusicPlayerBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Provider.INSTANCE, MusicPlayerBlock.class);
        registration.registerBlockComponent(Provider.INSTANCE, PortableMusicPlayerBlock.class);
        registration.registerBlockComponent(Provider.INSTANCE,
                com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeDeviceBlock.class);
         
        registration.addTooltipCollectedCallback((tooltip, accessor) -> {
            if (accessor instanceof BlockAccessor block && block.getBlockEntity() instanceof IMusicPlayerBlockEntity) {
                tooltip.remove(Identifiers.UNIVERSAL_ITEM_STORAGE);
            }
        });
    }

    enum Provider implements IBlockComponentProvider, snownee.jade.api.IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = new ResourceLocation(MengSamaNetMusic.MOD_ID, "music_player_info");
        private static final String DATA_KEY = "MengSamaMusic";

        @Override
        public void appendServerData(net.minecraft.nbt.CompoundTag tag, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof IMusicPlayerBlockEntity player)) return;
            MusicPlayerTooltipData data = snapshot(player);
            net.minecraft.nbt.CompoundTag publicData = new net.minecraft.nbt.CompoundTag();
            publicData.putBoolean("playing", data.playing());
            publicData.putBoolean("paused", data.paused());
            publicData.putString("title", data.title());
            publicData.putString("artists", data.artists());
            publicData.putString("album", data.album());
            publicData.putString("cover", data.coverUrl());
            publicData.putString("targetId", player.blockTargetId());
            net.minecraft.nbt.CompoundTag songTag = new net.minecraft.nbt.CompoundTag();
            ItemStack currentCd = player.getCurrentCd();
            SongInfo currentSong = currentCd.getItem() instanceof MusicListItem
                    ? MusicListItem.getSongInfo(currentCd) : null;
            if (currentSong != null) {
                SongInfo stableSong = currentSong.clone();
                stableSong.songUrl = stableSong.rawUrl == null ? "" : stableSong.rawUrl;
                stableSong.playbackHeaders.clear();
                SongInfo.serializeNBT(stableSong, songTag);
            }
            publicData.put("song", songTag);
            tag.put(DATA_KEY, publicData);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
             
             
            tooltip.remove(Identifiers.UNIVERSAL_ITEM_STORAGE);

            if (accessor.getBlockEntity() instanceof com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeBlockEntity device
                    && !device.isSpeaker()) return;

            MusicPlayerTooltipData data = accessor.getServerData().contains(DATA_KEY)
                    ? fromPublicData(accessor.getServerData().getCompound(DATA_KEY))
                    : MusicPlayerTooltipData.empty();
            if (!data.playing()) {
                tooltip.add(Component.translatable("jade.mengsamanetmusic.not_playing").withStyle(ChatFormatting.GRAY));
                return;
            }

            var elements = tooltip.getElementHelper();
            ITooltip text = elements.tooltip();
            Component title = Component.literal(data.title().isEmpty() ? "-" : data.title())
                    .withStyle(ChatFormatting.WHITE);
            if (data.paused()) {
                title = title.copy().append(Component.translatable("jade.mengsamanetmusic.paused")
                        .withStyle(ChatFormatting.YELLOW));
            }
            text.add(title);
            if (!data.artists().isEmpty()) {
                text.add(Component.translatable("jade.mengsamanetmusic.artist", data.artists())
                        .withStyle(ChatFormatting.GRAY));
            }
            if (!data.album().isEmpty()) {
                text.add(Component.translatable("jade.mengsamanetmusic.album", data.album())
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            text.add(Component.literal(currentLyric(data)).withStyle(ChatFormatting.AQUA));

            ITooltip row = elements.tooltip();
            if (!data.coverUrl().isEmpty()) row.append(new JadeSongCoverElement(data.coverUrl()));
            if (!data.coverUrl().isEmpty()) row.append(elements.spacer(5, 1));
            row.append(elements.box(text, snownee.jade.api.ui.IBoxStyle.Empty.INSTANCE));
            tooltip.add(elements.box(row, snownee.jade.api.ui.IBoxStyle.Empty.INSTANCE));
        }

        @Override
        public int getDefaultPriority() {
            return 2_000;
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        static MusicPlayerTooltipData snapshot(IMusicPlayerBlockEntity player) {
            if (!player.isPlay()) return MusicPlayerTooltipData.empty();
            ItemStack cd = player.getCurrentCd();
            SongInfo song = cd.getItem() instanceof MusicListItem ? MusicListItem.getSongInfo(cd) : null;
            return MusicPlayerTooltipData.of(true, player.isPaused(), song, player.blockTargetId());
        }

        static String currentLyric(MusicPlayerTooltipData data) {
            if (data.targetId().isEmpty() || data.song() == null) return "-";
            ClientLyricStore.Snapshot snapshot = ClientLyricStore.bind(data.targetId(), data.song());
            return switch (snapshot.state()) {
                case LOADING, IDLE -> Component.translatable("jade.mengsamanetmusic.lyric_loading").getString();
                case EMPTY -> Component.translatable("jade.mengsamanetmusic.lyric_empty").getString();
                case FAILED -> Component.translatable("jade.mengsamanetmusic.lyric_failed").getString();
                case READY -> {
                    int tick = ClientMusicPlayback.getTick(data.targetId());
                    String line = snapshot.data().lineAt(ClientLyricStore.playbackMillis(tick));
                    yield line.isBlank() ? Component.translatable("jade.mengsamanetmusic.lyric_waiting").getString() : line;
                }
            };
        }

        static MusicPlayerTooltipData fromPublicData(net.minecraft.nbt.CompoundTag tag) {
            if (tag == null || !tag.getBoolean("playing")) return MusicPlayerTooltipData.empty();
            SongInfo song = tag.contains("song") ? SongInfo.deserializeNBT(tag.getCompound("song")) : null;
            return new MusicPlayerTooltipData(true, tag.getBoolean("paused"), tag.getString("title"),
                    tag.getString("artists"), tag.getString("album"), tag.getString("cover"),
                    tag.getString("targetId"), song);
        }
    }
}
