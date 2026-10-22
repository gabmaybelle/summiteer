package com.gabbymaybelle.summiteer;

import com.gabbymaybelle.summiteer.clothes.Clothes;
import com.gabbymaybelle.summiteer.gear.Gear;
import com.gabbymaybelle.summiteer.platform.Services;
import com.gabbymaybelle.summiteer.rope.Ropes;
import com.gabbymaybelle.summiteer.weather.Weather;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
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
		Clothes.init();
		Gear.init();
		Ropes.init();
		LOGGER.info("Summiteer: ready on {} (high ground from {} blocks above sea level, summits from {})",
				Services.PLATFORM.name(), Config.highAboveSeaLevel, Config.summitAboveSeaLevel);
	}

	/** An id in Summiteer's own namespace. */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/** Called by the loaders at the end of every server tick. */
	public static void serverTick(MinecraftServer server) {
		Weather.serverTick(server);
		Gear.serverTick(server);
	}
}
