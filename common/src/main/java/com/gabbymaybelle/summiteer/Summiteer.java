package com.gabbymaybelle.summiteer;

import com.gabbymaybelle.summiteer.platform.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Summiteer: mountains worth climbing. The loader entrypoints ({@code fabric} and
 * {@code neoforge}) call {@link #init()} once; everything that doesn't care which loader it runs
 * on lives here in {@code common}.
 */
public final class Summiteer {
	public static final String MOD_ID = "summiteer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private Summiteer() {
	}

	public static void init() {
		Config.load();
		LOGGER.info("Summiteer: ready on {} (high ground from {} blocks above sea level, summits from {})",
				Services.PLATFORM.name(), Config.highAboveSeaLevel, Config.summitAboveSeaLevel);
	}
}
