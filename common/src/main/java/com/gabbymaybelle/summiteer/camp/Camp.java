package com.gabbymaybelle.summiteer.camp;

import com.gabbymaybelle.summiteer.Summiteer;
import com.gabbymaybelle.summiteer.platform.Services;
import com.gabbymaybelle.summiteer.rope.Ropes;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Base camp: the trail cairn ({@link TrailCairnBlock}) and the tent in every wool colour
 * ({@link TentBlock}).
 */
public final class Camp {
	public static final Supplier<Block> TRAIL_CAIRN_BLOCK = block("trail_cairn", properties -> properties
			.mapColor(MapColor.STONE).strength(1.0F).sound(SoundType.STONE).noOcclusion().lightLevel(state -> 7)
			.pushReaction(PushReaction.POPPED), TrailCairnBlock::new);
	public static final Supplier<Item> TRAIL_CAIRN = item("trail_cairn", Item.Properties::useBlockDescriptionPrefix,
			properties -> new Ropes.DescribedBlockItem(TRAIL_CAIRN_BLOCK.get(), properties));

	/** The tents, one block (and item) per wool colour: {@code summiteer:red_tent} and so on. */
	public static final Map<DyeColor, Supplier<Block>> TENT_BLOCKS = new EnumMap<>(DyeColor.class);
	public static final Map<DyeColor, Supplier<Item>> TENTS = new EnumMap<>(DyeColor.class);

	static {
		for (DyeColor colour : DyeColor.values()) {
			String name = colour.getName() + "_tent";
			Supplier<Block> tent = block(name, properties -> properties
					.mapColor(colour.getMapColor()).strength(0.2F).sound(SoundType.WOOL).noOcclusion()
					.ignitedByLava().pushReaction(PushReaction.POPPED), properties -> new TentBlock(colour, properties));
			TENT_BLOCKS.put(colour, tent);
			TENTS.put(colour, item(name, properties -> properties.stacksTo(1).useBlockDescriptionPrefix(),
					properties -> new Ropes.DescribedBlockItem(tent.get(), properties)));
		}
	}

	private static final ResourceKey<CreativeModeTab> TOOLS_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities"));

	private Camp() {
	}

	/** Loads this class, so the blocks and items above are registered; called once from {@link Summiteer#init}. */
	public static void init() {
		Services.PLATFORM.addToCreativeTab(TOOLS_TAB, TRAIL_CAIRN);
		for (DyeColor colour : DyeColor.values()) {
			Services.PLATFORM.addToCreativeTab(TOOLS_TAB, TENTS.get(colour));
		}
	}

	private static Supplier<Block> block(String name, Function<BlockBehaviour.Properties, BlockBehaviour.Properties> properties, Function<BlockBehaviour.Properties, Block> kind) {
		Identifier id = Summiteer.id(name);
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
		return Services.PLATFORM.register(Registries.BLOCK, id, () -> kind.apply(properties.apply(BlockBehaviour.Properties.of()).setId(key)));
	}

	/** The properties are only built inside the registration (see {@code Clothes.item}). */
	private static Supplier<Item> item(String name, Function<Item.Properties, Item.Properties> properties, Function<Item.Properties, Item> kind) {
		Identifier id = Summiteer.id(name);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		return Services.PLATFORM.register(Registries.ITEM, id, () -> kind.apply(properties.apply(new Item.Properties()).setId(key)));
	}
}
