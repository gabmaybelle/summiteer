package com.gabbymaybelle.summiteer.platform;

import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * The few things that differ between loaders. {@code fabric} and {@code neoforge} each provide
 * one implementation through {@code META-INF/services}; common code reaches it as
 * {@link Services#PLATFORM}.
 */
public interface Platform {
	/** "Fabric" or "NeoForge", for the log. */
	String name();

	/** The game's config folder. */
	Path configDir();

	/** Whether another mod is installed, by its mod id. */
	boolean isModLoaded(String modId);

	/**
	 * Registers something (an item, a block, an entity type...). Fabric does it at once; NeoForge
	 * waits for its registry event, so the result is a supplier that is only valid once the game
	 * has finished loading.
	 */
	<T> Supplier<T> register(ResourceKey<? extends Registry<T>> registry, ResourceLocation id, Supplier<T> factory);

	/** Puts an item in one of the vanilla creative tabs. */
	void addToCreativeTab(ResourceKey<CreativeModeTab> tab, Supplier<? extends ItemLike> item);

	/**
	 * Whether something worn in an accessory slot passes the test: Trinkets Updated's slots on
	 * Fabric, Curios' on NeoForge (one of them is required, so this always has slots to look in).
	 */
	boolean wearsAccessory(LivingEntity entity, Predicate<ItemStack> test);

	/**
	 * Puts something on in an accessory slot it fits: the first empty one, else the first one,
	 * swapping. Returns what that slot held before (empty if it was empty), or null if the item
	 * fits no slot. Server side only.
	 */
	ItemStack equipAccessory(LivingEntity entity, ItemStack stack);
}
