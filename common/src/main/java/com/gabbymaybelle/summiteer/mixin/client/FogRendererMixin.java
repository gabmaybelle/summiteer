package com.gabbymaybelle.summiteer.mixin.client;

import com.gabbymaybelle.summiteer.client.Whiteout;
import com.gabbymaybelle.summiteer.gear.Gear;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thickens the fog in a whiteout, after vanilla (and any loader event) has worked out its own (see
 * {@link Whiteout}): the distances once vanilla has set them, and the colour once it has picked it.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Shadow
	private static float fogRed;
	@Shadow
	private static float fogGreen;
	@Shadow
	private static float fogBlue;

	/** The terrain fog is set up once a frame, so the fade moves there; the sky fog follows it. */
	@Inject(method = "setupFog", at = @At("TAIL"))
	private static void summiteer$whiteout(Camera camera, FogRenderer.FogMode mode, float renderDistance, boolean thickFog, float partialTick, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mode == FogRenderer.FogMode.FOG_TERRAIN) {
			boolean wanted = camera.getFluidInCamera() == FogType.NONE && mc.level != null && Whiteout.wanted(mc.level, camera.getBlockPosition());
			Whiteout.advance(wanted, mc.getTimer().getRealtimeDeltaTicks());
		}
		if (Whiteout.amount() <= 0.0F) {
			return;
		}
		boolean goggles = mc.player != null && Gear.wearsGoggles(mc.player);
		RenderSystem.setShaderFogStart(Whiteout.start(RenderSystem.getShaderFogStart()));
		RenderSystem.setShaderFogEnd(Whiteout.end(RenderSystem.getShaderFogEnd(), goggles));
	}

	@Inject(method = "setupColor", at = @At("TAIL"))
	private static void summiteer$whiteoutColour(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, CallbackInfo ci) {
		if (Whiteout.amount() <= 0.0F) {
			return;
		}
		fogRed = Whiteout.colour(fogRed, 0);
		fogGreen = Whiteout.colour(fogGreen, 1);
		fogBlue = Whiteout.colour(fogBlue, 2);
		RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
	}
}
