package com.gabbymaybelle.summiteer.neoforge.client;

import com.gabbymaybelle.summiteer.Summiteer;
import com.gabbymaybelle.summiteer.clothes.Clothes;
import com.gabbymaybelle.summiteer.gear.Gear;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/** The NeoForge client entrypoint: draws the worn gear through Curios. */
@Mod(value = Summiteer.MOD_ID, dist = Dist.CLIENT)
public class SummiteerNeoForgeClient {
	public SummiteerNeoForgeClient(IEventBus modBus) {
		modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
			ICurioRenderer.register(Gear.SNOW_GOGGLES.get(), () -> new WornGearRenderer(WornGearRenderer.HEAD));
			ICurioRenderer.register(Gear.CRAMPONS.get(), () -> new WornGearRenderer(WornGearRenderer.FEET));
			ICurioRenderer.register(Clothes.CLIMBING_GLOVES.get(), () -> new WornGearRenderer(WornGearRenderer.HANDS));
		}));
	}
}
