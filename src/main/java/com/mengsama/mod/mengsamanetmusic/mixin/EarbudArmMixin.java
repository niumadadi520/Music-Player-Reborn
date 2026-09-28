package com.mengsama.mod.mengsamanetmusic.mixin;
import com.mengsama.mod.mengsamanetmusic.earbuds.client.EarbudClient;
import com.mengsama.mod.mengsamanetmusic.earbuds.client.handoff.VanillaArmRotation;
import com.mengsama.mod.mengsamanetmusic.earbuds.handoff.HandoffMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerModel.class)
public abstract class EarbudArmMixin {
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void earbuds$arm(LivingEntity entity,float swing,float amount,float age,float yaw,float pitch,CallbackInfo ci) {
        var state=EarbudClient.DEVICES.get(entity.getUUID());if(state==null||!state.hasUUID("Guest"))return;
        double seconds=(EarbudClient.tick(Minecraft.getInstance().getFrameTime())-state.getLong("Start"))/20;
        if(seconds<0||seconds>=3.6)return;
         
        VanillaArmRotation.apply((PlayerModel<?>)(Object)this,state.getInt("Side")==0?HandoffMotion.Side.LEFT:HandoffMotion.Side.RIGHT,seconds);
    }
}
