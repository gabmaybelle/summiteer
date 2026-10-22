package com.gabbymaybelle.summiteer.mixin;

import com.gabbymaybelle.summiteer.gear.Climbing;
import com.gabbymaybelle.summiteer.gear.Gear;
import com.gabbymaybelle.summiteer.rope.Ropes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The climbing gear's movement. These run on both sides: players move themselves on the client,
 * and the server asks the same questions (am I climbing? for fall damage and the flying check).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/** A snow or ice wall with an ice axe in hand is a ladder (see {@link Climbing}). */
	@Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
	private void summiteer$iceAxe(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && Gear.onIceWall((LivingEntity) (Object) this)) {
			cir.setReturnValue(true);
		}
	}

	/** Standing under a rope that ends just above a snow layer or tall grass still takes hold of it (see {@link Ropes#ropeJustAbove}). */
	@Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
	private void summiteer$ropeFoot(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && Ropes.ropeJustAbove((LivingEntity) (Object) this)) {
			cir.setReturnValue(true);
		}
	}

	/** Climbing the wall goes at the ice axe's speed, not a ladder's; a rope goes faster in climbing gloves. */
	@Inject(method = "handleRelativeFrictionAndCalculateMovement", at = @At("RETURN"), cancellable = true)
	private void summiteer$climbSpeed(Vec3 input, float friction, CallbackInfoReturnable<Vec3> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		Vec3 movement = cir.getReturnValue();
		// Vanilla sets 0.2 exactly when it climbs; anything else is ordinary moving.
		if (movement.y != 0.2) {
			return;
		}
		if (Ropes.onRope(self)) {
			if (Gear.wearsClimbingGloves(self)) {
				cir.setReturnValue(new Vec3(movement.x, Climbing.ropeGlovesVelocity(), movement.z));
			}
		} else if (!Gear.onVanillaClimbable(self) && Gear.onIceWall(self)) {
			cir.setReturnValue(new Vec3(movement.x, Climbing.climbVelocity(Gear.wearsClimbingGloves(self)), movement.z));
		}
	}

	/** Crampons grip: no block is more slippery than ordinary ground under them. */
	@ModifyArg(method = "travelInAir", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;computeModifiedFriction(FF)F"), index = 0)
	private float summiteer$crampons(float blockFriction) {
		if (blockFriction > Gear.CRAMPON_FRICTION && Gear.wearsCrampons((LivingEntity) (Object) this)) {
			return Gear.CRAMPON_FRICTION;
		}
		return blockFriction;
	}
}
