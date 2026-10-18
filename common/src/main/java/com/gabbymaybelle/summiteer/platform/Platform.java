package com.gabbymaybelle.summiteer.platform;

import java.nio.file.Path;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
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
	<T> Supplier<T> register(ResourceKey<? extends Registry<T>> registry, Identifier id, Supplier<T> factory);

	/** Puts an item in one of the vanilla creative tabs. */
	void addToCreativeTab(ResourceKey<CreativeModeTab> tab, Supplier<? extends ItemLike> item);
}
