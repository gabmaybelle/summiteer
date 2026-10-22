package com.gabbymaybelle.summiteer.camp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The cairn chime beat: once every three seconds, each cairn on its own beat. */
class CairnChimeTest {
	@Test
	void aCairnChimesOnceEveryThreeSeconds() {
		int chimes = 0;
		for (long second = 0; second < 30; second++) {
			if (CairnChime.now(second, 12345)) {
				chimes++;
			}
		}
		assertEquals(10, chimes);
	}

	@Test
	void neighboursChimeOnDifferentBeats() {
		int together = 0;
		for (long second = 0; second < 30; second++) {
			if (CairnChime.now(second, 0) && CairnChime.now(second, 1)) {
				together++;
			}
		}
		assertEquals(0, together);
	}

	@Test
	void negativePlacesStillChime() {
		int chimes = 0;
		for (long second = 0; second < 3; second++) {
			if (CairnChime.now(second, -7)) {
				chimes++;
			}
		}
		assertEquals(1, chimes);
	}
}
