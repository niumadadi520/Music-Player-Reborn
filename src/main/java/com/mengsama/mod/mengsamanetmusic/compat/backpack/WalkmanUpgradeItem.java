package com.mengsama.mod.mengsamanetmusic.compat.backpack;

import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import net.minecraft.world.level.block.Block;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeItem;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;
import java.util.List;

 
public final class WalkmanUpgradeItem extends MusicPlayerItem implements IUpgradeItem<WalkmanUpgradeWrapper> {
    public static final UpgradeType<WalkmanUpgradeWrapper> TYPE = new UpgradeType<>(WalkmanUpgradeWrapper::new);
    public WalkmanUpgradeItem(Block block, Properties properties) { super(block, properties); }
    @Override public UpgradeType<WalkmanUpgradeWrapper> getType() { return TYPE; }
    @Override public List<UpgradeConflictDefinition> getUpgradeConflicts() { return List.of(); }
    @Override public int getUpgradesPerStorage(String type) { return "backpack".equals(type) ? 1 : 0; }
    @Override public int getUpgradesInGroupPerStorage(String type) { return Integer.MAX_VALUE; }
    @Override public net.minecraft.network.chat.Component getName() { return net.minecraft.network.chat.Component.translatable(getDescriptionId()); }
}
