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

	private Cold() {
	}

	/** Whether a place is cold enough to freeze you. */
	public static boolean isCold(Zone zone, boolean snowingOnYou, boolean night) {
		return (zone != Zone.LOW && snowingOnYou) || (zone == Zone.SUMMIT && night);
	}

	/**
	 * Freezing per second. Each point of warmth takes one off, so warm enough clothes stop it
	 * altogether. Warmth comes from the mountain clothes (planned); until then it is 0.
	 */
	public static int perSecond(int warmth) {
		return Math.max(0, BASE_PER_SECOND - warmth);
	}
}
