package com.gabbymaybelle.summiteer.fabric;

import com.gabbymaybelle.summiteer.Summiteer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** The Fabric entrypoint: everything happens in {@code common}. */
public class SummiteerFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		Summiteer.init();
		ServerTickEvents.END_SERVER_TICK.register(Summiteer::serverTick);
	}
}
