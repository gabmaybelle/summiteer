package com.gabbymaybelle.summiteer.client;

import com.gabbymaybelle.summiteer.Config;
import com.gabbymaybelle.summiteer.zones.Zone;
import com.gabbymaybelle.summiteer.zones.Zones;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;

/**
 * Whiteouts: on a summit in a snowstorm the fog closes in to a few blocks, a pale grey-white.
 * Only what you see changes. The client works it out from what it already knows (the weather and
 * the biome are synced by vanilla), so there is nothing to send; on a server with different height
 * settings the fog follows this game's own settings.
 *
 * <p>The fog fades in and out over about three seconds instead of snapping, and it only ever
 * makes the fog thicker, so being underwater, in lava or blind still looks as it should.
 */
public final class Whiteout {
	/** How near the fog ends in a full whiteout, in blocks. */
	static final float END = 20.0F;
	private static final float START = 0.0F;
	/** Fade speed: the share of a full whiteout per tick (about three seconds from clear to white). */
	private static final float FADE_PER_TICK = 1.0F / 60.0F;
	private static final float GREY = 0.82F;

	private static float amount;

	private Whiteout() {
	}

	/** Whether a whiteout should be on where the camera is. */
	public static boolean wanted(ClientLevel level, BlockPos camera) {
		return Config.whiteouts
				&& level.isRaining()
				&& Zones.at(level, camera) == Zone.SUMMIT
				&& level.getBiome(camera).value().getPrecipitationAt(camera, level.getSeaLevel()) == Biome.Precipitation.SNOW;
	}

	/** Moves the fade a step and thickens the fog by however far it has faded in. */
	public static void apply(FogData fog, boolean wanted, float elapsedTicks) {
		float step = FADE_PER_TICK * elapsedTicks;
		amount = wanted ? Math.min(1.0F, amount + step) : Math.max(0.0F, amount - step);
		if (amount <= 0.0F) {
			return;
		}
		fog.environmentalStart = Math.min(fog.environmentalStart, lerp(fog.environmentalStart, START));
		fog.environmentalEnd = Math.min(fog.environmentalEnd, lerp(fog.environmentalEnd, END));
		fog.skyEnd = Math.min(fog.skyEnd, lerp(fog.skyEnd, END));
		fog.cloudEnd = Math.min(fog.cloudEnd, lerp(fog.cloudEnd, END));
		fog.color.set(
				lerp(fog.color.x(), GREY), lerp(fog.color.y(), GREY), lerp(fog.color.z(), GREY + 0.04F), fog.color.w());
	}

	private static float lerp(float from, float to) {
		return from + (to - from) * amount;
	}
}
