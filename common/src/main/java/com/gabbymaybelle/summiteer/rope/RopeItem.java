package com.gabbymaybelle.summiteer.rope;

import com.gabbymaybelle.summiteer.advancement.Advancements;
import com.gabbymaybelle.summiteer.gear.Gear;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Rope: right-click an anchor to hang it (or lengthen what already hangs there), sneak-right-click
 * the anchor to reel it all back in. It does nothing on other blocks; ropes only hang from anchors.
 */
public class RopeItem extends Gear.Described {
	public RopeItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Player player = context.getPlayer();
		if (player == null || !level.getBlockState(pos).is(Ropes.ANCHOR_BLOCK.get())) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive()) {
			Ropes.reel(level, pos, player);
			return InteractionResult.SUCCESS_SERVER;
		}
		int hanging = Ropes.hanging(level, pos, level.getBlockState(pos));
		int added = Ropes.hang(level, pos, context.getItemInHand(), player.hasInfiniteMaterials());
		if (added > 0) {
			level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
			Advancements.fire(player, Advancements.ROPE);
		} else {
			Ropes.tell(player, hanging >= Ropes.MAX_LENGTH ? "rope.full" : "rope.no_room");
		}
		return InteractionResult.SUCCESS_SERVER;
	}
}
