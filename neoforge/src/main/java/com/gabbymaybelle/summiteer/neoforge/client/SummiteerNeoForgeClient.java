package com.gabbymaybelle.summiteer.neoforge.client;

import com.gabbymaybelle.summiteer.Summiteer;
import com.gabbymaybelle.summiteer.client.WornGear;
import com.gabbymaybelle.summiteer.clothes.Clothes;
import com.gabbymaybelle.summiteer.gear.Gear;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

/**
 * The NeoForge client entrypoint: the dyed clothes' colour (in the hand, and cream when undyed on
 * the player), and the worn gear drawn through Curios.
 */
@Mod(value = Summiteer.MOD_ID, dist = Dist.CLIENT)
public class SummiteerNeoForgeClient {
	public SummiteerNeoForgeClient(IEventBus modBus) {
		modBus.addListener(RegisterColorHandlersEvent.Item.class, event -> event.register(WornGear::tint, clothes()));
		modBus.addListener(ModelEvent.RegisterAdditional.class, event -> {
			for (ResourceLocation model : WornGear.MODELS) {
				event.register(ModelResourceLocation.standalone(model));
			}
		});
		modBus.addListener(RegisterClientExtensionsEvent.class, event -> event.registerItem(new IClientItemExtensions() {
			@Override
			public int getDefaultDyeColor(ItemStack stack) {
				return WornGear.tint(stack, 0) | 0xFF000000;
			}
		}, clothes()));
		modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
			for (Item item : new Item[] {Gear.SNOW_GOGGLES.get(), Gear.CRAMPONS.get(), Clothes.CLIMBING_GLOVES.get()}) {
				CuriosRendererRegistry.register(item, WornGearRenderer::new);
			}
		}));
	}

	private static Item[] clothes() {
		return new Item[] {Clothes.BEANIE.get(), Clothes.PARKA.get(), Clothes.SNOW_PANTS.get(), Clothes.WOOL_SOCKS.get(), Clothes.CLIMBING_GLOVES.get()};
	}
}
