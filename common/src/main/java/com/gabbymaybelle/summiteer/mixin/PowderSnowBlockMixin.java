package com.gabbymaybelle.summiteer.mixin;

import com.gabbymaybelle.summiteer.gear.Gear;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Crampons walk on powder snow, the way vanilla's leather boots do. */
@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowBlockMixin {
	@Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
	private static void summiteer$crampons(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof LivingEntity living && Gear.wearsCrampons(living)) {
			cir.setReturnValue(true);
		}
	}
}
