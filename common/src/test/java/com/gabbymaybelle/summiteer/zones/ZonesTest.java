package com.gabbymaybelle.summiteer.zones;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The height bands, without the game: vanilla's sea level 63, the default bands 70 and 120. */
class ZonesTest {
	private static final int SEA = 63;
	private static final int HIGH = 70;
	private static final int SUMMIT = 120;

	@Test
	void belowTheTreelineIsLowEvenInTheMountains() {
		assertEquals(Zone.LOW, Zones.band(true, SEA + HIGH - 1, SEA, HIGH, SUMMIT));
	}

	@Test
	void mountainsAboveTheTreelineAreHigh() {
		assertEquals(Zone.HIGH, Zones.band(true, SEA + HIGH, SEA, HIGH, SUMMIT));
		assertEquals(Zone.HIGH, Zones.band(true, SEA + SUMMIT - 1, SEA, HIGH, SUMMIT));
	}

	@Test
	void otherBiomesAtTheSameHeightStayLow() {
		assertEquals(Zone.LOW, Zones.band(false, SEA + HIGH + 10, SEA, HIGH, SUMMIT));
	}

	@Test
	void summitHeightCountsInAnyBiome() {
		assertEquals(Zone.SUMMIT, Zones.band(true, SEA + SUMMIT, SEA, HIGH, SUMMIT));
		assertEquals(Zone.SUMMIT, Zones.band(false, SEA + SUMMIT, SEA, HIGH, SUMMIT));
	}

	@Test
	void bandsFollowTheSeaLevelNotAFixedHeight() {
		// A world whose sea level is 100 (some terrain mods) moves every band up with it.
		assertEquals(Zone.LOW, Zones.band(true, 63 + HIGH, 100, HIGH, SUMMIT));
		assertEquals(Zone.HIGH, Zones.band(true, 100 + HIGH, 100, HIGH, SUMMIT));
	}
}
