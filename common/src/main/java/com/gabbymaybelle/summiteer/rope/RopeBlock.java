package com.gabbymaybelle.summiteer.rope;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A piece of hanging rope, only ever placed by an anchor (see {@link Ropes}). It hugs the cliff
 * side ({@link #FACING} points at the cliff), has no collision, and climbs like a ladder through
 * {@code #minecraft:climbable}. It holds on while there is rope above it, or the anchor beside the
 * top piece; when that goes, the rope below comes down a piece a tick and drops as items. The
 * bottom piece shows a knot ({@link #KNOT}); the top one is tied to the anchor ({@link #TOP}).
 */
public class RopeBlock extends Block {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty KNOT = BooleanProperty.create("knot");
	/** The top piece, beside the anchor: drawn tied to the anchor's ring and over the edge. */
	public static final BooleanProperty TOP = BooleanProperty.create("top");
	/** Drawn hugging the north side; rotated for the others. */
	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 4.0));

	public RopeBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(KNOT, false).setValue(TOP, false));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction towardCliff = state.getValue(FACING);
		BlockState above = level.getBlockState(pos.above());
		if (above.is(this) && above.getValue(FACING) == towardCliff) {
			return true;
		}
		BlockState anchor = level.getBlockState(pos.relative(towardCliff));
		return anchor.is(Ropes.ANCHOR_BLOCK.get()) && anchor.getValue(AnchorBlock.FACING) == towardCliff.getOpposite();
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (!state.canSurvive(level, pos)) {
			ticks.scheduleTick(pos, this, 1);
		}
		if (directionToNeighbour == Direction.DOWN) {
			state = state.setValue(KNOT, !neighbourState.is(this));
		} else if (directionToNeighbour == Direction.UP) {
			state = state.setValue(TOP, !neighbourState.is(this));
		}
		return state;
	}

	/** Lost its hold: falls as an item, which takes the piece below with it next tick. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.canSurvive(level, pos)) {
			level.destroyBlock(pos, true);
		}
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(Ropes.ROPE.get());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, KNOT, TOP);
	}
}
