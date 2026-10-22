package com.gabbymaybelle.summiteer.rope;

import com.gabbymaybelle.summiteer.Summiteer;
import com.gabbymaybelle.summiteer.gear.Gear;
import com.gabbymaybelle.summiteer.platform.Services;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Prediction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Rope and anchor (decided with Gabby 2026-10-22): place an anchor at a cliff edge facing the drop,
 * right-click it with rope and the rope unrolls straight down, one block out from the edge, until
 * it meets the ground, water or any block, reaches {@link #MAX_LENGTH}, or the rope in hand runs
 * out. It climbs like a ladder (it is in {@code #minecraft:climbable}, so the hang, the slide and
 * the fall reset are vanilla's), faster in climbing gloves. Sneak-right-click the anchor, with an
 * empty hand or with rope, to reel the whole rope back in; breaking the anchor drops it all there.
 * Rope and anchors work anywhere, not only above the treeline.
 */
public final class Ropes {
	/** The longest rope one anchor holds, in blocks. */
	public static final int MAX_LENGTH = RopeLength.MAX;

	public static final Supplier<Block> ROPE_BLOCK = block("rope", properties -> properties
			.mapColor(MapColor.WOOD).noCollision().noOcclusion().strength(0.4F).sound(SoundType.WOOL)
			.pushReaction(PushReaction.POPPED), RopeBlock::new);
	public static final Supplier<Block> ANCHOR_BLOCK = block("anchor", properties -> properties
			.mapColor(MapColor.METAL).noCollision().noOcclusion().strength(1.0F).sound(SoundType.METAL)
			.pushReaction(PushReaction.POPPED), AnchorBlock::new);

	public static final Supplier<Item> ROPE = item("rope", properties -> properties, RopeItem::new);
	public static final Supplier<Item> ANCHOR = item("anchor", Item.Properties::useBlockDescriptionPrefix,
			properties -> new DescribedBlockItem(ANCHOR_BLOCK.get(), properties));

	private static final ResourceKey<CreativeModeTab> TOOLS_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities"));

	private Ropes() {
	}

	/** Loads this class, so the blocks and items above are registered; called once from {@link Summiteer#init}. */
	public static void init() {
		Services.PLATFORM.addToCreativeTab(TOOLS_TAB, ROPE);
		Services.PLATFORM.addToCreativeTab(TOOLS_TAB, ANCHOR);
	}

	/** Whether this entity is holding on to a rope (it climbs rope at the gloves' speed). */
	public static boolean onRope(LivingEntity entity) {
		return entity.getInBlockState().is(ROPE_BLOCK.get());
	}

	/** Where the rope from an anchor starts: the block in front of it, over the drop. */
	public static BlockPos ropeTop(BlockPos anchor, BlockState anchorState) {
		return anchor.relative(anchorState.getValue(AnchorBlock.FACING));
	}

	/** How many rope blocks hang from this anchor, counted down from the top. */
	public static int hanging(Level level, BlockPos anchor, BlockState anchorState) {
		Direction towardCliff = anchorState.getValue(AnchorBlock.FACING).getOpposite();
		BlockPos top = ropeTop(anchor, anchorState);
		int count = 0;
		while (count < MAX_LENGTH * 2) {
			BlockState state = level.getBlockState(top.below(count));
			if (!state.is(ROPE_BLOCK.get()) || state.getValue(RopeBlock.FACING) != towardCliff) {
				break;
			}
			count++;
		}
		return count;
	}

	/**
	 * Hangs rope from an anchor (extending any rope already there), taking it from {@code stack}
	 * unless {@code infinite}. Returns how many blocks were added. Server side only.
	 */
	public static int hang(Level level, BlockPos anchor, ItemStack stack, boolean infinite) {
		BlockState anchorState = level.getBlockState(anchor);
		if (!anchorState.is(ANCHOR_BLOCK.get())) {
			return 0;
		}
		BlockPos top = ropeTop(anchor, anchorState);
		int hanging = hanging(level, anchor, anchorState);
		BlockPos start = top.below(hanging);
		int length = RopeLength.drop(hanging, stack.getCount(), infinite, i -> level.getBlockState(start.below(i)).isAir());
		Direction towardCliff = anchorState.getValue(AnchorBlock.FACING).getOpposite();
		BlockState rope = ROPE_BLOCK.get().defaultBlockState().setValue(RopeBlock.FACING, towardCliff);
		for (int i = 0; i < length; i++) {
			level.setBlock(start.below(i), rope.setValue(RopeBlock.KNOT, i == length - 1).setValue(RopeBlock.TOP, hanging + i == 0), Block.UPDATE_ALL);
		}
		if (length > 0 && !infinite) {
			stack.shrink(length);
		}
		return length;
	}

	/**
	 * Takes down the whole rope hanging from an anchor, bottom first so nothing falls, and returns
	 * how many blocks it was. Server side only; the caller decides where the rope goes.
	 */
	public static int takeDown(Level level, BlockPos anchor, BlockState anchorState) {
		int hanging = hanging(level, anchor, anchorState);
		BlockPos top = ropeTop(anchor, anchorState);
		for (int i = hanging - 1; i >= 0; i--) {
			level.setBlock(top.below(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		return hanging;
	}

	/** Reels the rope up into the player's inventory (what doesn't fit drops at their feet). */
	public static int reel(Level level, BlockPos anchor, Player player) {
		BlockState anchorState = level.getBlockState(anchor);
		if (!anchorState.is(ANCHOR_BLOCK.get())) {
			return 0;
		}
		int count = takeDown(level, anchor, anchorState);
		if (count > 0) {
			if (!player.hasInfiniteMaterials()) {
				give(player, count);
			}
			level.playSound(null, anchor, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
		}
		return count;
	}

	private static void give(Player player, int count) {
		while (count > 0) {
			ItemStack stack = new ItemStack(ROPE.get(), Math.min(count, ROPE.get().getDefaultMaxStackSize()));
			count -= stack.getCount();
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false, Prediction.SERVER_ONLY);
			}
		}
	}

	/** Drops {@code count} rope as items at a place, in full stacks. */
	static void dropRope(Level level, BlockPos pos, int count) {
		while (count > 0) {
			ItemStack stack = new ItemStack(ROPE.get(), Math.min(count, ROPE.get().getDefaultMaxStackSize()));
			count -= stack.getCount();
			Block.popResource(level, pos, stack);
		}
	}

	/** A short message above the hotbar. */
	static void tell(Player player, String key) {
		player.sendOverlayMessage(Component.translatable("message.summiteer." + key));
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

	/** A block item with a grey line under its name, like {@link Gear.Described}. */
	public static class DescribedBlockItem extends BlockItem {
		public DescribedBlockItem(Block block, Item.Properties properties) {
			super(block, properties);
		}

		@Override
		@SuppressWarnings("deprecation") // Mojang's marker for "override, don't call"; vanilla's items do the same.
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
				Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
		}
	}
}
