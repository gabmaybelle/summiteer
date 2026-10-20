package com.gabbymaybelle.summiteer.gear;

import com.gabbymaybelle.summiteer.zones.Zone;

/**
 * The rules for climbing with the ice axe, kept free of the game so they can be unit tested.
 * {@link Gear} and the {@code LivingEntity} mixin apply them.
 *
 * <p>An ice axe in either hand turns a snow or ice wall into a ladder, and natural rock too once
 * you are above the treeline (see {@link #grips}): walk into it to climb,
 * sneak to hang still, and falling past one you slide down at a ladder's pace and take no fall
 * damage (the self-arrest). The slide and the hang are vanilla's ladder rules; only the climbing
 * speed is Summiteer's: slower than a ladder, faster with climbing gloves.
 */
public final class Climbing {
	/** How fast you climb with the axe alone, and with climbing gloves (a ladder is about 2.35). */
	static final double BARE_BLOCKS_PER_SECOND = 1.0;
	static final double GLOVES_BLOCKS_PER_SECOND = 1.6;

	/** Vanilla's gravity and air drag on a player: what a climb has to beat each tick. */
	static final double GRAVITY = 0.08;
	static final double DRAG = 0.98;

	/** How far from the wall still counts as touching it, in blocks. */
	public static final double REACH = 0.05;

	private Climbing() {
	}

	/**
	 * Whether the axe bites into a wall: snow and ice anywhere, natural rock only above the treeline
	 * (high ground or a summit), so it stays a mountain tool.
	 */
	public static boolean grips(boolean snowOrIce, boolean rock, Zone zone) {
		return snowOrIce || rock && zone != Zone.LOW;
	}

	/** Blocks per second up a wall, with or without climbing gloves. */
	public static double blocksPerSecond(boolean gloves) {
		return gloves ? GLOVES_BLOCKS_PER_SECOND : BARE_BLOCKS_PER_SECOND;
	}

	/**
	 * The upward speed to set while climbing, in blocks per tick. Vanilla sets a ladder climber's
	 * speed to 0.2 and then takes gravity off and the drag, so this works backwards from the speed
	 * we want to rise at.
	 */
	public static double climbVelocity(boolean gloves) {
		return blocksPerSecond(gloves) / 20.0 / DRAG + GRAVITY;
	}

	/** What one tick of climbing at {@code velocity} actually rises, after gravity and drag. */
	static double risePerTick(double velocity) {
		return (velocity - GRAVITY) * DRAG;
	}
}
