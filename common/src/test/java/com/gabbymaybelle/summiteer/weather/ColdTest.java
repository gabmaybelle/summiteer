package com.gabbymaybelle.summiteer.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gabbymaybelle.summiteer.zones.Zone;
import org.junit.jupiter.api.Test;

/** Where it's cold and how fast you freeze, without the game. */
class ColdTest {
	@Test
	void snowstormsAreColdAboveTheTreelineOnly() {
		assertFalse(Cold.isCold(Zone.LOW, true, false));
		assertTrue(Cold.isCold(Zone.HIGH, true, false));
		assertTrue(Cold.isCold(Zone.SUMMIT, true, false));
	}

	@Test
	void clearDaysAreNotCold() {
		assertFalse(Cold.isCold(Zone.HIGH, false, false));
		assertFalse(Cold.isCold(Zone.SUMMIT, false, false));
	}

	@Test
	void summitNightsAreColdInAnyWeatherButHighNightsAreNot() {
		assertTrue(Cold.isCold(Zone.SUMMIT, false, true));
		assertFalse(Cold.isCold(Zone.HIGH, false, true));
		assertFalse(Cold.isCold(Zone.LOW, false, true));
	}

	@Test
	void withNoWarmthYouFreezeInAboutHalfAMinute() {
		// Vanilla is fully frozen at 140.
		int seconds = (int) Math.ceil(140.0 / Cold.perSecond(0));
		assertTrue(seconds >= 20 && seconds <= 40, "took " + seconds + " s");
	}

	@Test
	void eachPointOfWarmthSlowsItAndEnoughStopsIt() {
		assertTrue(Cold.perSecond(1) < Cold.perSecond(0));
		assertEquals(0, Cold.perSecond(Cold.BASE_PER_SECOND));
		assertEquals(0, Cold.perSecond(Cold.BASE_PER_SECOND + 3));
	}
}
