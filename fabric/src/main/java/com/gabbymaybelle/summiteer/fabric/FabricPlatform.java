package com.gabbymaybelle.summiteer.fabric;

import com.gabbymaybelle.summiteer.platform.Platform;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketsApi;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
	public <T> Supplier<T> register(ResourceKey<? extends Registry<T>> registry, ResourceLocation id, Supplier<T> factory) {
		Registry<T> target = (Registry<T>) BuiltInRegistries.REGISTRY.get(registry.location());
		T value = Registry.register(target, id, factory.get());
		return () -> value;
	}

	@Override
	public void addToCreativeTab(ResourceKey<CreativeModeTab> tab, Supplier<? extends ItemLike> item) {
		ItemGroupEvents.modifyEntriesEvent(tab).register(entries -> entries.accept(item.get()));
	}

	@Override
	public boolean wearsAccessory(LivingEntity entity, Predicate<ItemStack> test) {
		return TrinketsApi.getTrinketComponent(entity).map(trinkets -> trinkets.isEquipped(test)).orElse(false);
	}

	@Override
	public ItemStack equipAccessory(LivingEntity entity, ItemStack stack) {
		TrinketInventory first = null;
		int firstIndex = 0;
		var trinkets = TrinketsApi.getTrinketComponent(entity);
		if (trinkets.isEmpty()) {
			return null;
		}
		for (TrinketInventory inventory : trinkets.get().getInventory().values().stream().flatMap(group -> group.values().stream()).toList()) {
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				SlotReference slot = new SlotReference(inventory, i);
				if (!TrinketsApi.evaluatePredicateSet(inventory.getSlotType().getValidatorPredicates(), stack, slot, entity)
						|| !TrinketsApi.getTrinket(stack.getItem()).canEquip(stack, slot, entity)) {
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
