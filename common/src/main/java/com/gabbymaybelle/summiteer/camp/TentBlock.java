package com.gabbymaybelle.summiteer.camp;

import com.gabbymaybelle.summiteer.advancement.Advancements;
import com.gabbymaybelle.summiteer.zones.Zone;
import com.gabbymaybelle.summiteer.zones.Zones;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The base camp tent: a bed you can carry (decided with Gabby 2026-10-22). It is a bed underneath:
 * right-click sets your respawn there, and at night you sleep in it and skip the night, by
 * vanilla's bed rule (so only in the Overworld; elsewhere it says so instead of exploding).
 * Sneak-right-click with an empty hand packs it into one item and clears the respawn point if it
 * was yours. Beds are left alone. One block per wool colour, like beds. Unlike a bed it has no
 * block entity: its look is plain block models.
 */
public class TentBlock extends BedBlock {
	/** The sleeping mat's height, so you lie on it; the canvas over it is only drawn. */
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0);

	private final DyeColor colour;

	public TentBlock(DyeColor colour, BlockBehaviour.Properties properties) {
		super(colour, properties);
		this.colour = colour;
	}

	public DyeColor colour() {
		return colour;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return null;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!player.isSecondaryUseActive()) {
			// Outside the Overworld the bed rule says no; a tent just says so rather than exploding.
			if (!canSetSpawn(level)) {
				if (!level.isClientSide()) {
					level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 0.6F);
				}
				return InteractionResult.SUCCESS;
			}
			InteractionResult result = super.useWithoutItem(state, level, pos, player, hitResult);
			// "Base camp": asleep in a tent above the treeline.
			if (!level.isClientSide() && player.isSleeping() && Zones.at(level, pos) != Zone.LOW) {
				Advancements.fire(player, Advancements.CAMP);
			}
			return result;
		}
		if (!level.isClientSide()) {
			pack(level, pos, state, player);
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * Takes the tent down into one item in the player's hands (or at their feet), and clears their
	 * respawn point if it was this tent. Server side only.
	 */
	public static void pack(Level level, BlockPos pos, BlockState state, Player player) {
		if (state.getValue(OCCUPIED)) {
			player.displayClientMessage(Component.translatable("block.minecraft.bed.occupied"), true);
			return;
		}
		BlockPos other = pos.relative(getConnectedDirection(state));
		if (player instanceof ServerPlayer serverPlayer) {
			BlockPos respawn = serverPlayer.getRespawnPosition();
			if (respawn != null && serverPlayer.getRespawnDimension() == level.dimension()
					&& (respawn.equals(pos) || respawn.equals(other))) {
				serverPlayer.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);
			}
		}
		// The other half first, so taking this one down doesn't drop anything from it.
		if (level.getBlockState(other).is(state.getBlock())) {
			level.setBlock(other, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
		}
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
		ItemStack tent = new ItemStack(state.getBlock());
		if (!player.getInventory().add(tent)) {
			player.drop(tent, false);
		}
		level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.0F, 0.9F);
		player.displayClientMessage(Component.translatable("message.summiteer.tent.packed"), true);
	}
}
