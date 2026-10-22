package com.gabbymaybelle.summiteer.rope;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** How far a rope unrolls: to the ground, at most 32, at most what you carry. */
class RopeLengthTest {
	@Test
	void unrollsToTheGround() {
		// A 12-block drop: blocks 0 to 11 below the top are air, the 12th is ground.
		assertEquals(12, RopeLength.drop(0, 64, false, i -> i < 12));
	}

	@Test
	void stopsAtThirtyTwo() {
		assertEquals(32, RopeLength.drop(0, 64, false, i -> true));
		assertEquals(32, RopeLength.drop(0, 0, true, i -> true));
	}

	@Test
	void stopsWhenTheRopeInHandRunsOut() {
		assertEquals(5, RopeLength.drop(0, 5, false, i -> true));
		assertEquals(0, RopeLength.drop(0, 0, false, i -> true));
	}

	@Test
	void creativeIgnoresWhatYouCarry() {
		assertEquals(12, RopeLength.drop(0, 0, true, i -> i < 12));
	}

	@Test
	void lengtheningARopeCountsWhatAlreadyHangs() {
		assertEquals(2, RopeLength.drop(30, 64, false, i -> true));
		assertEquals(0, RopeLength.drop(32, 64, false, i -> true));
		assertEquals(7, RopeLength.drop(5, 64, false, i -> i < 7));
	}

	@Test
	void noRoomHangsNothing() {
		assertEquals(0, RopeLength.drop(0, 64, false, i -> false));
	}
}
