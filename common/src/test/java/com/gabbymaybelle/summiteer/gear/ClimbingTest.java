package com.gabbymaybelle.summiteer.gear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The ice axe climbing rules, without the game. */
class ClimbingTest {
	private static final double EPSILON = 1e-9;
	/** Vanilla's ladder: 0.2 a tick before gravity and drag. */
	private static final double LADDER = 0.2;

	@Test
	void theClimbRisesAtTheChosenSpeed() {
		assertEquals(1.0, Climbing.risePerTick(Climbing.climbVelocity(false)) * 20, EPSILON);
		assertEquals(1.6, Climbing.risePerTick(Climbing.climbVelocity(true)) * 20, EPSILON);
	}

	@Test
	void climbingGlovesClimbFaster() {
		assertTrue(Climbing.climbVelocity(true) > Climbing.climbVelocity(false));
	}

	@Test
	void eitherWayItIsSlowerThanALadder() {
		assertTrue(Climbing.climbVelocity(true) < LADDER);
		assertTrue(Climbing.risePerTick(Climbing.climbVelocity(false)) > 0, "the bare axe still climbs");
	}
}
