package com.gabbymaybelle.summiteer.fabric;

import com.gabbymaybelle.summiteer.Summiteer;
import net.fabricmc.api.ModInitializer;

/** The Fabric entrypoint: everything happens in {@code common}. */
public class SummiteerFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		Summiteer.init();
	}
}
