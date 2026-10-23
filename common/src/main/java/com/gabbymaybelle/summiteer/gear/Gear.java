package com.gabbymaybelle.summiteer.gear;

import com.gabbymaybelle.summiteer.Summiteer;
import com.gabbymaybelle.summiteer.advancement.Advancements;
import com.gabbymaybelle.summiteer.clothes.Clothes;
import com.gabbymaybelle.summiteer.platform.Services;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import com.gabbymaybelle.summiteer.zones.Zone;
import com.gabbymaybelle.summiteer.zones.Zones;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The climbing gear: snow goggles and crampons for the head and feet slots of Curios or Trinkets
 * Updated, and the ice axe. (The climbing gloves are clothes too, so they live in {@link Clothes}.)
 *
 * <ul>
 * <li>Snow goggles: you see further in a whiteout.</li>
 * <li>Crampons: no slipping on ice, you walk on powder snow, and gusts push you half as hard.</li>
 * <li>Ice axe: climb snow and ice walls, and natural rock above the treeline (see {@link Climbing});
 * a pickaxe, and a fair weapon.</li>
 * </ul>
 *
 * <p>Right-clicking the goggles, crampons or gloves puts them on, swapping with what you wore, the
 * way armour does; dragging them into the slot works too.
 */
public final class Gear {
	/** The walls an ice axe bites into anywhere: snow and ice, and whatever a data pack adds. */
	public static final TagKey<Block> ICE_AXE_CLIMBABLE = TagKey.create(Registries.BLOCK, Summiteer.id("ice_axe_climbable"));
	/** The walls it bites into only above the treeline: natural rock. */
	public static final TagKey<Block> ICE_AXE_CLIMBABLE_HIGH = TagKey.create(Registries.BLOCK, Summiteer.id("ice_axe_climbable_high"));

	/** The most slippery a block can be under crampons: ordinary ground (ice is 0.98). */
	public static final float CRAMPON_FRICTION = 0.6F;

	public static final Supplier<Item> SNOW_GOGGLES = item("snow_goggles", properties -> properties.stacksTo(1),
			properties -> new Accessory(properties, SoundEvents.ARMOR_EQUIP_LEATHER));
	public static final Supplier<Item> CRAMPONS = item("crampons", properties -> properties.stacksTo(1),
			properties -> new Accessory(properties, SoundEvents.ARMOR_EQUIP_CHAIN));
	/** An iron pickaxe with a sharper edge: 5 damage at 1.4 attacks a second (the pickaxe does 4 at 1.2). */
	public static final Supplier<Item> ICE_AXE = item("ice_axe", properties -> properties.attributes(PickaxeItem.createAttributes(Tiers.IRON, 2.0F, -2.6F)),
			IceAxe::new);

	private static final ResourceKey<CreativeModeTab> COMBAT_TAB = vanillaTab("combat");
	private static final ResourceKey<CreativeModeTab> TOOLS_TAB = vanillaTab("tools_and_utilities");

	private Gear() {
	}

	/** Loads this class, so the items above are registered; called once from {@link Summiteer#init}. */
	public static void init() {
		Services.PLATFORM.addToCreativeTab(COMBAT_TAB, SNOW_GOGGLES);
		Services.PLATFORM.addToCreativeTab(COMBAT_TAB, CRAMPONS);
		Services.PLATFORM.addToCreativeTab(TOOLS_TAB, ICE_AXE);
	}

	public static boolean wearsGoggles(LivingEntity entity) {
		return wears(entity, SNOW_GOGGLES);
	}

	public static boolean wearsCrampons(LivingEntity entity) {
		return wears(entity, CRAMPONS);
	}

	public static boolean wearsClimbingGloves(LivingEntity entity) {
		return wears(entity, Clothes.CLIMBING_GLOVES);
	}

	/** Only players wear gear; asking about anything else costs nothing. */
	private static boolean wears(LivingEntity entity, Supplier<Item> item) {
		return entity instanceof Player && Services.PLATFORM.wearsAccessory(entity, stack -> stack.is(item.get()));
	}

	/** The hand holding an ice axe (the main hand first), or null. */
	public static InteractionHand iceAxeHand(LivingEntity entity) {
		if (entity.getMainHandItem().is(ICE_AXE.get())) {
			return InteractionHand.MAIN_HAND;
		}
		return entity.getOffhandItem().is(ICE_AXE.get()) ? InteractionHand.OFF_HAND : null;
	}

	/**
	 * Whether a player is on a wall with the ice axe: holding one, and touching a block it grips
	 * (see {@link Climbing#grips}) beside them at foot or body height. The block under their feet
	 * doesn't count. Rock asks which zone the player is in, the client by its own settings like the
	 * whiteout does, so a server with different heights could disagree near the treeline.
	 */
	public static boolean onIceWall(LivingEntity entity) {
		if (!(entity instanceof Player player) || player.isSpectator() || player.getAbilities().flying || iceAxeHand(player) == null) {
			return false;
		}
		AABB box = player.getBoundingBox();
		Level level = player.level();
		BlockPos min = BlockPos.containing(box.minX - Climbing.REACH, box.minY + 0.1, box.minZ - Climbing.REACH);
		BlockPos max = BlockPos.containing(box.maxX + Climbing.REACH, Math.min(box.maxY - 0.1, box.minY + 1.2), box.maxZ + Climbing.REACH);
		Zone zone = null;
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			BlockState state = level.getBlockState(pos);
			boolean snowOrIce = state.is(ICE_AXE_CLIMBABLE);
			boolean rock = !snowOrIce && state.is(ICE_AXE_CLIMBABLE_HIGH);
			if (!snowOrIce && !rock || state.getCollisionShape(level, pos).isEmpty()) {
				continue;
			}
			if (rock && zone == null) {
				zone = Zones.at(level, player.blockPosition());
			}
			if (Climbing.grips(snowOrIce, rock, zone)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether vanilla would let this entity climb without the axe: a ladder, vines, scaffolding.
	 * Those keep vanilla's speed.
	 */
	public static boolean onVanillaClimbable(LivingEntity entity) {
		return entity.getInBlockState().is(BlockTags.CLIMBABLE);
	}

	/**
	 * The server's part of climbing, every tick: a player on a wall takes no fall damage when they
	 * reach the ground (the client already stops it; this makes the server agree). Climbing doesn't
	 * wear the axe (Gabby, 2026-10-20); mining and fighting do, as with any pickaxe.
	 */
	public static void serverTick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!onIceWall(player)) {
				continue;
			}
			player.resetFallDistance();
			if (!player.onGround()) {
				Advancements.fire(player, Advancements.ICE_CLIMB);
			}
		}
	}

	private static ResourceKey<CreativeModeTab> vanillaTab(String name) {
		return ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace(name));
	}

	/** The properties are only built inside the registration (see {@code Clothes.item}). */
	private static Supplier<Item> item(String name, Function<Item.Properties, Item.Properties> properties, Function<Item.Properties, Item> kind) {
		return Services.PLATFORM.register(Registries.ITEM, Summiteer.id(name), () -> kind.apply(properties.apply(new Item.Properties())));
	}

	/** An item with a grey line under its name saying what it does ({@code item.summiteer.<name>.desc}). */
	public static class Described extends Item {
		public Described(Item.Properties properties) {
			super(properties);
		}

		@Override
		@SuppressWarnings("deprecation") // Mojang's marker for "override, don't call"; vanilla's items do the same.
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
			lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
		}
	}

	/** The ice axe: an iron pickaxe, with its line saying what it does. */
	public static class IceAxe extends PickaxeItem {
		public IceAxe(Item.Properties properties) {
			super(Tiers.IRON, properties);
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
			lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
		}
	}

	/**
	 * Something worn in an accessory slot: right-click puts it on, in the first empty slot it fits,
	 * else swapped with what is in the first one, and what came off goes to your hand.
	 */
	public static class Accessory extends Described {
		private final Holder<SoundEvent> sound;

		public Accessory(Item.Properties properties, Holder<SoundEvent> sound) {
			super(properties);
			this.sound = sound;
		}

		@Override
		public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
			ItemStack held = player.getItemInHand(hand);
			if (level.isClientSide()) {
				return InteractionResultHolder.success(held);
			}
			ItemStack previous = Services.PLATFORM.equipAccessory(player, held.copy());
			if (previous == null) {
				return InteractionResultHolder.pass(held);
			}
			player.setItemInHand(hand, previous);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, player.getSoundSource(), 1.0F, 1.0F);
			return InteractionResultHolder.success(previous);
		}
	}
}
