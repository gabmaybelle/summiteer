package com.gabbymaybelle.summiteer.camp;

/** When a trail cairn chimes in a whiteout, kept free of the game so it can be unit tested. */
public final class CairnChime {
	/** A chime every three seconds. */
	public static final int EVERY_SECONDS = 3;

	private CairnChime() {
	}

	/**
	 * Whether this second is the cairn's turn to chime: every {@link #EVERY_SECONDS}, each cairn on
	 * its own beat (from where it stands), so a line of them doesn't ring all at once.
	 */
	public static boolean now(long second, int place) {
		return Math.floorMod(second + place, EVERY_SECONDS) == 0;
	}
}
