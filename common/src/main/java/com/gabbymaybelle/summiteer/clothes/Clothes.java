package com.gabbymaybelle.summiteer.clothes;

import com.gabbymaybelle.summiteer.Summiteer;
import com.gabbymaybelle.summiteer.gear.Gear;
import com.gabbymaybelle.summiteer.platform.Services;
import com.gabbymaybelle.summiteer.weather.Cold;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The mountain clothes: a beanie, a parka, snow pants and wool socks (dyeable wool armour with
 * leather's protection), and climbing gloves for the hands slot of Curios or Trinkets Updated (they
 * also climb faster with the ice axe, see {@code gear.Climbing}). Each piece worn is one point of
 * warmth against the cold up high (see {@link Cold#warmth}).
 *
 * <p>What keeps you warm is the item tag {@code summiteer:keeps_warm}, so a data pack can add
 * other mods' clothes. Vanilla's leather doesn't count: up high only warmth keeps the cold off.
 */
public final class Clothes {
	/** Worn in an armour slot or an accessory slot, these give one point of warmth each. */
	public static final TagKey<Item> KEEPS_WARM = TagKey.create(Registries.ITEM, Summiteer.id("keeps_warm"));
	/** What mends wool clothes on an anvil: any wool. */
	public static final TagKey<Item> REPAIRS_WOOL = TagKey.create(Registries.ITEM, Summiteer.id("repairs_wool_armor"));

	/** The colour of undyed wool clothes, on the player and in the hand: a cream. */
	public static final int UNDYED = -1647157;

	/**
	 * Leather's numbers (protection, enchanting), with wool to mend it. The look on the player is one
	 * dyeable layer, textures/models/armor/wool_layer_1.png (and _2 for the snow pants). Held directly
	 * rather than registered: nothing looks it up by name.
	 */
	public static final Holder<ArmorMaterial> WOOL = Holder.direct(new ArmorMaterial(
			Map.of(Type.BOOTS, 1, Type.LEGGINGS, 2, Type.CHESTPLATE, 3, Type.HELMET, 1, Type.BODY, 3),
			15, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(REPAIRS_WOOL),
			List.of(new ArmorMaterial.Layer(Summiteer.id("wool"), "", true)), 0.0F, 0.0F));

	public static final Supplier<Item> BEANIE = armour("beanie", Type.HELMET);
	public static final Supplier<Item> PARKA = armour("parka", Type.CHESTPLATE);
	public static final Supplier<Item> SNOW_PANTS = armour("snow_pants", Type.LEGGINGS);
	public static final Supplier<Item> WOOL_SOCKS = armour("wool_socks", Type.BOOTS);
	public static final Supplier<Item> CLIMBING_GLOVES = item("climbing_gloves", () -> new Item.Properties().stacksTo(1),
			properties -> new Gear.Accessory(properties, SoundEvents.ARMOR_EQUIP_LEATHER));

	/** Vanilla's Combat tab, beside the leather armour (its key is private in CreativeModeTabs). */
	private static final ResourceKey<CreativeModeTab> COMBAT_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace("combat"));

	private static final List<EquipmentSlot> ARMOUR_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	private Clothes() {
	}

	/** Loads this class, so the items above are registered; called once from {@link Summiteer#init}. */
	public static void init() {
		for (Supplier<Item> item : List.of(BEANIE, PARKA, SNOW_PANTS, WOOL_SOCKS, CLIMBING_GLOVES)) {
			Services.PLATFORM.addToCreativeTab(COMBAT_TAB, item);
		}
	}

	/** Whether this is one of the wool clothes (or the gloves), which are cream when undyed. */
	public static boolean isWoolClothing(ItemStack stack) {
		return stack.is(BEANIE.get()) || stack.is(PARKA.get()) || stack.is(SNOW_PANTS.get()) || stack.is(WOOL_SOCKS.get()) || stack.is(CLIMBING_GLOVES.get());
	}

	/** The warmth someone is wearing: one per warm piece in an armour slot, one for warm gloves. */
	public static int warmth(LivingEntity entity) {
		int pieces = 0;
		for (EquipmentSlot slot : ARMOUR_SLOTS) {
			if (entity.getItemBySlot(slot).is(KEEPS_WARM)) {
				pieces++;
			}
		}
		boolean hands = Services.PLATFORM.wearsAccessory(entity, stack -> stack.is(KEEPS_WARM));
		return Cold.warmth(pieces, hands);
	}

	/** Leather's durability: 5 times each piece's own number. */
	private static Supplier<Item> armour(String name, Type type) {
		return item(name, () -> new Item.Properties().durability(type.getDurability(5)), properties -> new ArmorItem(WOOL, type, properties));
	}

	/**
	 * The properties are only built inside the registration: on NeoForge, building armour
	 * properties any earlier (at class load) touches registries that are already frozen.
	 */
	private static Supplier<Item> item(String name, Supplier<Item.Properties> properties, Function<Item.Properties, Item> kind) {
		return Services.PLATFORM.register(Registries.ITEM, Summiteer.id(name), () -> kind.apply(properties.get()));
	}
}
