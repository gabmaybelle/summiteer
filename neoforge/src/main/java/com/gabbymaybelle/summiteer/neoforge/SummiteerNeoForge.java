package com.gabbymaybelle.summiteer.neoforge;

import com.gabbymaybelle.summiteer.Summiteer;
import com.gabbymaybelle.summiteer.platform.Services;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** The NeoForge entrypoint: everything happens in {@code common}. */
@Mod(Summiteer.MOD_ID)
public class SummiteerNeoForge {
	public SummiteerNeoForge(IEventBus modBus) {
		NeoForgePlatform platform = (NeoForgePlatform) Services.PLATFORM;
		// init() queues the registrations; the registry events hand them to NeoForge.
		Summiteer.init();
		modBus.addListener(RegisterEvent.class, platform::onRegister);
		modBus.addListener(BuildCreativeModeTabContentsEvent.class, platform::onCreativeTab);
		NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> Summiteer.serverTick(event.getServer()));
	}
}
