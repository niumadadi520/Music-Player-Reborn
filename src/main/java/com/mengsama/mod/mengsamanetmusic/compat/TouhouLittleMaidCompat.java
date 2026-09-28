package com.mengsama.mod.mengsamanetmusic.compat;

import net.neoforged.fml.ModList;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

public class TouhouLittleMaidCompat {
    public static final String TLM = "touhou_little_maid";
    public static final VersionRange TLM_VERSION_RANGE;

    static {
        VersionRange range;
        try {
            range = VersionRange.createFromVersionSpec("[1.2.0,)");
        } catch (org.apache.maven.artifact.versioning.InvalidVersionSpecificationException e) {
            range = VersionRange.createFromVersion("1.2.0");
        }
        TLM_VERSION_RANGE = range;
    }

    public static void register() {
        ModList.get().getModContainerById(TLM).ifPresent(modContainer -> {
            ArtifactVersion version = modContainer.getModInfo().getVersion();
            if (TLM_VERSION_RANGE.containsVersion(version)) {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(ActiveMaidMusicTracker.class);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(MaidLyricSynchronizer.class);
                if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(TouhouLittleMaidClientEvents.class);
                }
            }
        });
    }
}
