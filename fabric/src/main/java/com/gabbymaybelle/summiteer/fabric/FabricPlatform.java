package com.gabbymaybelle.summiteer.fabric;

import com.gabbymaybelle.summiteer.platform.Platform;
import eu.pb4.trinkets.api.TrinketInventory;
import eu.pb4.trinkets.api.TrinketsApi;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public class FabricPlatform implements Platform {
	@Override
	public String name() {
		return "Fabric";
	}

	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Supplier<T> register(ResourceKey<? extends Registry<T>> registry, Identifier id, Supplier<T> factory) {
		Registry<T> target = (Registry<T>) BuiltInRegistries.REGISTRY.getValue(registry.identifier());
		T value = Registry.register(target, id, factory.get());
		return () -> value;
	}

	@Override
	public void addToCreativeTab(ResourceKey<CreativeModeTab> tab, Supplier<? extends ItemLike> item) {
		CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> output.accept(item.get()));
	}

	@Override
	public boolean wearsAccessory(LivingEntity entity, Predicate<ItemStack> test) {
		return TrinketsApi.getAttachment(entity).isEquipped(test);
	}

	@Override
	public ItemStack equipAccessory(LivingEntity entity, ItemStack stack) {
		TrinketInventory first = null;
		int firstIndex = 0;
		for (TrinketInventory inventory : TrinketsApi.getAttachment(entity).getInventories().values()) {
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				if (!inventory.slotType().validatorCheck(stack, inventory.getOrCreateSlotAccess(i), entity)) {
					continue;
				}
				if (inventory.getItem(i).isEmpty()) {
					inventory.setItem(i, stack);
					return ItemStack.EMPTY;
				}
				if (first == null) {
					first = inventory;
					firstIndex = i;
				}
			}
		}
		if (first == null) {
			return null;
		}
		ItemStack previous = first.getItem(firstIndex);
		first.setItem(firstIndex, stack);
		return previous;
	}
}
