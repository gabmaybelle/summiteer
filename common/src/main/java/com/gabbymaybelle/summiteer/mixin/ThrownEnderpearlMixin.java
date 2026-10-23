package com.gabbymaybelle.summiteer.mixin;

import com.gabbymaybelle.summiteer.weather.Weather;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Thrown ender pearls drift downwind above the treeline (see {@link Weather#driftPearl}). */
@Mixin(ThrownEnderpearl.class)
public abstract class ThrownEnderpearlMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void summiteer$wind(CallbackInfo ci) {
		Weather.driftPearl((ThrownEnderpearl) (Object) this);
	}
}
