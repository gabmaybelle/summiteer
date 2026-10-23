package com.gabbymaybelle.summiteer.camp;

import com.gabbymaybelle.summiteer.advancement.Advancements;
import com.gabbymaybelle.summiteer.zones.Zone;
import com.gabbymaybelle.summiteer.zones.Zones;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The base camp tent: a bed you can carry (decided with Gabby 2026-10-22). It is a bed underneath,
 * built like vanilla's straw bed: right-click sets your respawn there, and at night you sleep in
 * it and skip the night, by vanilla's bed rule (so only in the Overworld; elsewhere it says so
 * instead of exploding). Sneak-right-click with an empty hand packs it into one item and clears
 * the respawn point if it was yours. Beds are left alone. One block per wool colour, like beds.
 */
public class TentBlock extends AbstractBedBlock {
	/** The sleeping mat's height, so you lie on it; the canvas over it is only drawn. */
	private static final Map<Direction, VoxelShape> SHAPES = Util.make(() -> Shapes.rotateHorizontal(Block.column(16.0, 0.0, 3.0)));

	private final DyeColor colour;

	public TentBlock(DyeColor colour, BlockBehaviour.Properties properties) {
		super(properties);
		this.colour = colour;
	}

	public DyeColor colour() {
		return colour;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(getConnectedDirection(state).getOpposite());
	}

	@Override
	protected EnvironmentAttribute<BedRule> getBedEnvironmentAttribute() {
		return EnvironmentAttributes.BED_RULE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!player.isSecondaryUseActive()) {
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
			player.sendOverlayMessage(Component.translatable("block.minecraft.bed.occupied"));
			return;
		}
		BlockPos other = pos.relative(getConnectedDirection(state));
		if (player instanceof ServerPlayer serverPlayer) {
			ServerPlayer.RespawnConfig respawn = serverPlayer.getRespawnConfig();
			if (respawn != null && respawn.respawnData().dimension() == level.dimension()
					&& (respawn.respawnData().pos().equals(pos) || respawn.respawnData().pos().equals(other))) {
				serverPlayer.setRespawnPosition(null, false);
			}
		}
		// The other half first, so taking this one down doesn't drop anything from it.
		if (level.getBlockState(other).is(state.getBlock())) {
			level.setBlock(other, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
		}
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
		ItemStack tent = new ItemStack(state.getBlock());
		if (!player.getInventory().add(tent)) {
			player.drop(tent, false, Prediction.SERVER_ONLY);
		}
		level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.0F, 0.9F);
		player.sendOverlayMessage(Component.translatable("message.summiteer.tent.packed"));
	}

	/** Outside the Overworld the bed rule says no; a tent just says so rather than exploding. */
	@Override
	protected InteractionResult destroyOnUse(BlockState state, Level level, BlockPos pos, Player player) {
		level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 0.6F);
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	protected void destroyOnLeave(Level level, BlockPos pos) {
	}
}
