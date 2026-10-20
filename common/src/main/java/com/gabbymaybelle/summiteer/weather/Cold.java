package com.gabbymaybelle.summiteer.weather;

import com.gabbymaybelle.summiteer.zones.Zone;

/**
 * The rules for the cold, kept free of the game so they can be unit tested. {@link Weather}
 * applies them to players through vanilla's freezing: the frost on the screen, the shaking and the
 * freeze damage once fully frozen are all vanilla's own.
 *
 * <p>It is cold above the treeline in a snowstorm (unless you are under a roof), and on the summits
 * at night whatever the weather. A fire or a lit campfire nearby keeps the cold off, and vanilla
 * thaws you out again as usual.
 */
public final class Cold {
	/**
	 * Freezing added per second with no warmth at all. Vanilla needs 140 to be fully frozen, so this
	 * is about half a minute.
	 */
	static final int BASE_PER_SECOND = 5;
	/** Warmth that stops the cold altogether: the four clothes and the climbing gloves. */
	public static final int FULL_WARMTH = BASE_PER_SECOND;

	private Cold() {
	}

	/** Whether a place is cold enough to freeze you. */
	public static boolean isCold(Zone zone, boolean snowingOnYou, boolean night) {
		return (zone != Zone.LOW && snowingOnYou) || (zone == Zone.SUMMIT && night);
	}

	/**
	 * Warmth from what someone is wearing: one point per warm piece of clothing (beanie, parka,
	 * snow pants, wool socks) and one for climbing gloves, never more than {@link #FULL_WARMTH}.
	 */
	public static int warmth(int warmPieces, boolean gloves) {
		return Math.min(FULL_WARMTH, Math.max(0, warmPieces) + (gloves ? 1 : 0));
	}

	/**
	 * Freezing per second. Each point of warmth takes one off, so the full set of clothes and
	 * gloves stops it altogether.
	 */
	public static int perSecond(int warmth) {
		return Math.max(0, BASE_PER_SECOND - warmth);
	}
}
