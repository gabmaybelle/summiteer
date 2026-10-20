package com.gabbymaybelle.summiteer.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gabbymaybelle.summiteer.zones.Zone;
import org.junit.jupiter.api.Test;

/** The gust rules, without the game. */
class WindTest {
	private static final double EPSILON = 1e-9;

	@Test
	void noWindBelowTheTreeline() {
		assertEquals(0, Wind.strength(Zone.LOW, true, false, true, false), EPSILON);
	}

	@Test
	void summitsBlowHarderThanHighGroundAndStormsHarderStill() {
		double high = Wind.strength(Zone.HIGH, false, false, false, false);
		double summit = Wind.strength(Zone.SUMMIT, false, false, false, false);
		double summitStorm = Wind.strength(Zone.SUMMIT, true, false, false, false);
		assertTrue(high > 0);
		assertTrue(summit > high);
		assertTrue(summitStorm > summit);
	}

	@Test
	void crouchingBracesAgainstMostOfAGust() {
		double standing = Wind.strength(Zone.SUMMIT, true, false, false, false);
		double crouching = Wind.strength(Zone.SUMMIT, true, true, false, false);
		assertEquals(standing * Wind.BRACED, crouching, EPSILON);
	}

	@Test
	void glidersCantBraceAndGetBlownFurther() {
		double standing = Wind.strength(Zone.SUMMIT, false, false, false, false);
		assertEquals(standing * Wind.GLIDING, Wind.strength(Zone.SUMMIT, false, true, true, false), EPSILON);
	}

	@Test
	void cramponsHalveAGustAndStackWithCrouching() {
		double standing = Wind.strength(Zone.SUMMIT, true, false, false, false);
		assertEquals(standing * Wind.CRAMPONS, Wind.strength(Zone.SUMMIT, true, false, false, true), EPSILON);
		assertEquals(standing * Wind.BRACED * Wind.CRAMPONS, Wind.strength(Zone.SUMMIT, true, true, false, true), EPSILON);
	}

	@Test
	void cramponsDontHelpInTheAir() {
		assertEquals(Wind.strength(Zone.SUMMIT, false, false, true, false), Wind.strength(Zone.SUMMIT, false, false, true, true), EPSILON);
	}

	@Test
	void theWindTurnsOnceEveryFourDays() {
		assertEquals(0, Wind.direction(0), EPSILON);
		assertEquals(Math.PI, Wind.direction(Wind.TURN_TICKS / 2), EPSILON);
		assertEquals(Wind.direction(123), Wind.direction(123 + Wind.TURN_TICKS), EPSILON);
	}

	@Test
	void gustsAreSpacedOutAndCloserInAStorm() {
		assertEquals(160, Wind.ticksUntilNextGust(false, 0));
		assertEquals(320, Wind.ticksUntilNextGust(false, 1));
		assertEquals(100, Wind.ticksUntilNextGust(true, 0));
		assertEquals(200, Wind.ticksUntilNextGust(true, 1));
		// Every gust leaves room for its full warning.
		assertTrue(Wind.ticksUntilNextGust(true, 0) > Wind.WARNING_TICKS);
	}
}
