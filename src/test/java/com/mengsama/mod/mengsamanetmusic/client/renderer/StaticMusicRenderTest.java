package com.mengsama.mod.mengsamanetmusic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StaticMusicRenderTest {
    @BeforeAll static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var flag=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        flag.setAccessible(true);flag.setBoolean(null,true);
        Class.forName("net.minecraft.core.registries.BuiltInRegistries");
    }
    private static final class Probe extends BlockEntity implements GeoAnimatable {
        Probe(Direction facing){super(BlockEntityType.CHEST,BlockPos.ZERO,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,facing));}
        @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){throw new AssertionError("Far model evaluated controllers");}
        @Override public AnimatableInstanceCache getAnimatableInstanceCache(){throw new AssertionError("Far model touched animation state");}
        @Override public double getTick(Object o){throw new AssertionError("Far model read animation clock");}
    }
    @Test void staticPathSkipsAnimationStateAndRetainsBlockPlacement() {
        var renderer=new LodMusicBlockRenderer.StaticRenderer<Probe>(null);
         
        var geometry=new BakedGeoModel(List.of(),null);
        for(Direction facing:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST)) {
            var pose=new PoseStack();
            assertDoesNotThrow(()->renderer.actuallyRender(pose,new Probe(facing),geometry,null,null,null,false,0,0,0,1,1,1,1));
            assertEquals(.5,pose.last().pose().m30(),1e-6);
            assertEquals(.5,pose.last().pose().m32(),1e-6);
            double expected=switch(facing){case NORTH->1;case SOUTH->-1;default->0;};
            assertEquals(expected,pose.last().pose().m00(),1e-6);
        }
    }
}
