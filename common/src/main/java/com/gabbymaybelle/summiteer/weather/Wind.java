package com.gabbymaybelle.summiteer.weather;

import com.gabbymaybelle.summiteer.zones.Zone;

/**
 * The rules for gusts, kept free of the game so they can be unit tested. {@link Weather} applies
 * them to players.
 *
 * <p>A gust is fair: it is announced {@link #WARNING_TICKS} ahead (a rush of snow and a whoosh from
 * the side it will come from), crouching braces you against most of it, and under a roof there is
 * none. The wind comes from one direction for everyone in the world and turns slowly, a full circle
 * every four days, so a ridge you know stays windy from the same side for a while.
 */
public final class Wind {
	/** How long before a gust its warning starts: two seconds. */
	public static final int WARNING_TICKS = 40;

	/** Push per gust, in blocks per tick, before the multipliers below. */
	static final double HIGH_PUSH = 0.35;
	static final double SUMMIT_PUSH = 0.55;
	static final double STORM = 1.5;
	static final double BRACED = 0.25;
	static final double GLIDING = 1.5;

	/** One full turn of the wind every four in-game days. */
	static final long TURN_TICKS = 4 * 24000L;

	private Wind() {
	}

	/** How hard a gust pushes, in blocks per tick; 0 means no gust here. */
	public static double strength(Zone zone, boolean storm, boolean crouching, boolean gliding) {
		double push = switch (zone) {
			case LOW -> 0;
			case HIGH -> HIGH_PUSH;
			case SUMMIT -> SUMMIT_PUSH;
		};
		if (storm) {
			push *= STORM;
		}
		if (gliding) {
			// Nothing to brace against in the air.
			push *= GLIDING;
		} else if (crouching) {
			push *= BRACED;
		}
		return push;
	}

	/** The way the wind blows towards, in radians (0 is towards +X, a quarter turn is towards +Z). */
	public static double direction(long gameTime) {
		return (Math.floorMod(gameTime, TURN_TICKS) / (double) TURN_TICKS) * 2 * Math.PI;
	}

	/** Ticks until the next gust: 8 to 16 seconds, or 5 to 10 in a storm. {@code roll} is 0 to 1. */
	public static int ticksUntilNextGust(boolean storm, double roll) {
		int min = storm ? 100 : 160;
		int max = storm ? 200 : 320;
		return min + (int) Math.round(roll * (max - min));
	}
}
