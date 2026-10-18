package com.gabbymaybelle.summiteer.mixin.client;

import com.gabbymaybelle.summiteer.client.Whiteout;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Thickens the fog in a whiteout, after vanilla has worked out its own (see {@link Whiteout}). */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void summiteer$whiteout(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount,
			ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		boolean wanted = camera.getFluidInCamera() == FogType.NONE && Whiteout.wanted(level, camera.blockPosition());
		Whiteout.apply(cir.getReturnValue(), wanted, deltaTracker.getRealtimeDeltaTicks());
	}
}
