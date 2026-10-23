package com.gabbymaybelle.summiteer.neoforge;

import com.gabbymaybelle.summiteer.platform.Platform;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import top.theillusivec4.curios.api.CuriosApi;

import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * NeoForge only lets mods register during its registry events, so everything common code
 * registers is queued here and handed over when {@link #onRegister} fires for that registry.
 */
public class NeoForgePlatform implements Platform {
	private record Pending<T>(ResourceKey<? extends Registry<T>> registry, ResourceLocation id, Supplier<T> factory) {
		void offer(RegisterEvent event) {
			if (event.getRegistryKey().equals(registry)) {
				event.register(registry, id, factory);
			}
		}
	}

	private record TabEntry(ResourceKey<CreativeModeTab> tab, Supplier<? extends ItemLike> item) {
	}

	private final List<Pending<?>> pending = new ArrayList<>();
	private final List<TabEntry> tabEntries = new ArrayList<>();

	@Override
	public String name() {
		return "NeoForge";
	}

	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Supplier<T> register(ResourceKey<? extends Registry<T>> registry, ResourceLocation id, Supplier<T> factory) {
		pending.add(new Pending<>(registry, id, factory));
		ResourceKey<T> key = ResourceKey.create(registry, id);
		return new Supplier<>() {
			private T value;

			@Override
			public T get() {
				if (value == null) {
					Registry<T> target = (Registry<T>) BuiltInRegistries.REGISTRY.get(registry.location());
					value = target.get(key);
					if (value == null) {
						throw new IllegalStateException(id + " isn't registered yet");
					}
				}
				return value;
			}
		};
	}

	@Override
	public void addToCreativeTab(ResourceKey<CreativeModeTab> tab, Supplier<? extends ItemLike> item) {
		tabEntries.add(new TabEntry(tab, item));
	}

	@Override
	public boolean wearsAccessory(LivingEntity entity, Predicate<ItemStack> test) {
		return CuriosApi.getCuriosInventory(entity).map(curios -> curios.isEquipped(test)).orElse(false);
	}

	@Override
	public ItemStack equipAccessory(LivingEntity entity, ItemStack stack) {
		ICuriosItemHandler curios = CuriosApi.getCuriosInventory(entity).orElse(null);
		if (curios == null) {
			return null;
		}
		String firstSlot = null;
		for (String slot : CuriosApi.getItemStackSlots(stack, entity).keySet()) {
			ICurioStacksHandler handler = curios.getStacksHandler(slot).orElse(null);
			if (handler == null) {
				continue;
			}
			for (int i = 0; i < handler.getSlots(); i++) {
				if (handler.getStacks().getStackInSlot(i).isEmpty()) {
					curios.setEquippedCurio(slot, i, stack);
					return ItemStack.EMPTY;
				}
			}
			if (firstSlot == null && handler.getSlots() > 0) {
				firstSlot = slot;
			}
		}
		if (firstSlot == null) {
			return null;
		}
		ItemStack previous = curios.getStacksHandler(firstSlot).orElseThrow().getStacks().getStackInSlot(0).copy();
		curios.setEquippedCurio(firstSlot, 0, stack);
		return previous;
	}

	void onRegister(RegisterEvent event) {
		for (Pending<?> entry : pending) {
			entry.offer(event);
		}
	}

	void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
		for (TabEntry entry : tabEntries) {
			if (event.getTabKey().equals(entry.tab())) {
				event.accept(entry.item().get());
			}
		}
	}
}
