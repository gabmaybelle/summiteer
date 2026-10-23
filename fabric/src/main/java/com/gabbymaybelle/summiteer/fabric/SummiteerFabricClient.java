package com.gabbymaybelle.summiteer.fabric;

import com.gabbymaybelle.summiteer.client.WornGear;
import com.gabbymaybelle.summiteer.clothes.Clothes;
import com.gabbymaybelle.summiteer.gear.Gear;
import dev.emi.trinkets.api.client.TrinketRenderer;
import dev.emi.trinkets.api.client.TrinketRendererRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.item.Item;

/** The Fabric client entrypoint: the dyed clothes' colour, and the worn gear drawn through Trinkets. */
public class SummiteerFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ColorProviderRegistry.ITEM.register(WornGear::tint, Clothes.BEANIE.get(), Clothes.PARKA.get(), Clothes.SNOW_PANTS.get(),
				Clothes.WOOL_SOCKS.get(), Clothes.CLIMBING_GLOVES.get());
		ModelLoadingPlugin.register(context -> context.addModels(WornGear.MODELS));
		TrinketRenderer worn = (stack, slot, model, poseStack, buffers, light, entity, limbAngle, limbDistance, tickDelta, age, headYaw, headPitch) -> {
			if (model instanceof HumanoidModel<?> body) {
				WornGear.render(stack, Minecraft.getInstance().getModelManager().getModel(WornGear.model(stack)), body, poseStack, buffers, light);
			}
		};
		for (Item item : new Item[] {Gear.SNOW_GOGGLES.get(), Gear.CRAMPONS.get(), Clothes.CLIMBING_GLOVES.get()}) {
			TrinketRendererRegistry.registerRenderer(item, worn);
		}
	}
}
