package com.mengsama.mod.mengsamanetmusic.compat;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.init.ModItems;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

@LittleMaidExtension
public final class TouhouLittleMaidExtension implements ILittleMaid {
    public static final ResourceLocation MUSIC_TASK_UID = new ResourceLocation(MengSamaNetMusic.MOD_ID, "music");

    @Override
    public void addMaidTask(TaskManager manager) {
        manager.add(new MusicTask());
    }

    private static final class MusicTask implements IMaidTask {
        @Override public ResourceLocation getUid() { return MUSIC_TASK_UID; }
        @Override public ItemStack getIcon() { return new ItemStack(ModItems.MUSIC_PLAYER.get()); }
        @Override public SoundEvent getAmbientSound(EntityMaid maid) { return null; }
        @Override public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
            return Collections.emptyList();
        }
        @Override public boolean isEnable(EntityMaid maid) { return !EntityMusicDevice.heldPlayer(maid).isEmpty(); }
        @Override public List<Pair<String, java.util.function.Predicate<EntityMaid>>> getEnableConditionDesc(EntityMaid maid) {
            return List.of(Pair.of("task.mengsamanetmusic.music.enable", m -> !EntityMusicDevice.heldPlayer(m).isEmpty()));
        }
         
         
        @Override public boolean isHidden(EntityMaid maid) { return false; }
        @Override public String getMaidActionSummary() { return "task.mengsamanetmusic.music.desc"; }
    }
}
