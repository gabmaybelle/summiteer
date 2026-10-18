package com.gabbymaybelle.summiteer.zones;

/** How high up a place is, as far as the mountains' weather is concerned. */
public enum Zone {
	/** Below the treeline, or not in the mountains: nothing changes. */
	LOW,
	/** Mountain biome above the treeline: the wind starts. */
	HIGH,
	/** Summit height, in any biome: the strongest wind and the summit storms. */
	SUMMIT
}
