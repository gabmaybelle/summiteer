package com.gabbymaybelle.summiteer.rope;

import java.util.function.IntPredicate;

/** How far a rope unrolls, kept free of the game so it can be unit tested; {@link Ropes} applies it. */
public final class RopeLength {
	/** The longest rope one anchor holds, in blocks. */
	public static final int MAX = 32;

	private RopeLength() {
	}

	/**
	 * One block at a time below the current bottom, while the next block is free (only air is), the
	 * rope already hanging plus the new piece stays within {@link #MAX}, and there is rope left to
	 * hang.
	 *
	 * @param hanging how much rope already hangs from the anchor (it is extended from its bottom)
	 * @param carried how much rope is in hand; ignored when {@code infinite} (creative)
	 * @param free whether the block {@code i} below the current bottom is free
	 */
	public static int drop(int hanging, int carried, boolean infinite, IntPredicate free) {
		int length = 0;
		while (hanging + length < MAX && (infinite || length < carried) && free.test(length)) {
			length++;
		}
		return length;
	}
}
