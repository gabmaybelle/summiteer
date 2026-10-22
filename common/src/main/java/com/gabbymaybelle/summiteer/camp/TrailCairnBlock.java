package com.gabbymaybelle.summiteer.camp;

import com.gabbymaybelle.summiteer.Config;
import com.gabbymaybelle.summiteer.weather.Weather;
import com.gabbymaybelle.summiteer.zones.Zone;
import com.gabbymaybelle.summiteer.zones.Zones;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A trail cairn: three stacked stones that give off a little light and a wisp of snow. In a
 * whiteout (summit height, a snowstorm, open sky: the same rule as the fog) it chimes softly every
 * few seconds, heard from about 32 blocks: sound isn't hidden by fog, so you walk towards it and a
 * line of cairns leads you down (Gabby, 2026-10-22; a glow through the fog was tried first, and
 * vanilla's glowing outline couldn't be shown to work). Each cairn checks once a second through a
 * scheduled tick, so nothing scans the world.
 */
public class TrailCairnBlock extends Block {
	/** Sounds are heard out to 16 blocks per unit of volume. */
	private static final float CHIME_VOLUME = 2.0F;
	private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 10.0, 12.0);

	public TrailCairnBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return Block.canSupportCenter(level, pos.below(), Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (directionToNeighbour == Direction.DOWN && !state.canSurvive(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return state;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!level.isClientSide() && !oldState.is(this)) {
			level.scheduleTick(pos, this, 1);
		}
	}

	/** The once-a-second check: in a whiteout, a chime every few seconds. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (CairnChime.now(level.getGameTime() / 20, pos.hashCode()) && inWhiteout(level, pos)) {
			level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, CHIME_VOLUME, 1.2F + random.nextFloat() * 0.2F);
		}
		level.scheduleTick(pos, this, 20);
	}

	/** The same rule as the whiteout fog, asked at the cairn: summit height, a snowstorm, open sky. */
	public static boolean inWhiteout(Level level, BlockPos pos) {
		return Config.whiteouts && level.canSeeSky(pos.above()) && Zones.at(level, pos) == Zone.SUMMIT && Weather.isSnowingAt(level, pos);
	}

	/** Now and then a snowflake lifts off the top. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(5) == 0) {
			level.addParticle(ParticleTypes.SNOWFLAKE, pos.getX() + 0.4 + random.nextDouble() * 0.2, pos.getY() + 0.7, pos.getZ() + 0.4 + random.nextDouble() * 0.2,
					(random.nextDouble() - 0.5) * 0.02, 0.03, (random.nextDouble() - 0.5) * 0.02);
		}
	}
}
