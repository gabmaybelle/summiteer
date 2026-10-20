package com.gabbymaybelle.summiteer.clothes;

import com.gabbymaybelle.summiteer.Summiteer;
import com.gabbymaybelle.summiteer.gear.Gear;
import com.gabbymaybelle.summiteer.platform.Services;
import com.gabbymaybelle.summiteer.weather.Cold;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

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

	/** The look on the player: assets/summiteer/equipment/wool.json. */
	private static final ResourceKey<EquipmentAsset> WOOL_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Summiteer.id("wool"));

	/** Leather's numbers (durability, protection, enchanting), with wool to mend it. */
	public static final ArmorMaterial WOOL = new ArmorMaterial(5,
			Map.of(ArmorType.BOOTS, 1, ArmorType.LEGGINGS, 2, ArmorType.CHESTPLATE, 3, ArmorType.HELMET, 1, ArmorType.BODY, 3),
			15, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, REPAIRS_WOOL, WOOL_ASSET);

	public static final Supplier<Item> BEANIE = armour("beanie", ArmorType.HELMET);
	public static final Supplier<Item> PARKA = armour("parka", ArmorType.CHESTPLATE);
	public static final Supplier<Item> SNOW_PANTS = armour("snow_pants", ArmorType.LEGGINGS);
	public static final Supplier<Item> WOOL_SOCKS = armour("wool_socks", ArmorType.BOOTS);
	public static final Supplier<Item> CLIMBING_GLOVES = item("climbing_gloves", () -> new Item.Properties().stacksTo(1),
			properties -> new Gear.Accessory(properties, SoundEvents.ARMOR_EQUIP_LEATHER));

	/** Vanilla's Combat tab, beside the leather armour (its key is private in CreativeModeTabs). */
	private static final ResourceKey<CreativeModeTab> COMBAT_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));

	private static final List<EquipmentSlot> ARMOUR_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	private Clothes() {
	}

	/** Loads this class, so the items above are registered; called once from {@link Summiteer#init}. */
	public static void init() {
		for (Supplier<Item> item : List.of(BEANIE, PARKA, SNOW_PANTS, WOOL_SOCKS, CLIMBING_GLOVES)) {
			Services.PLATFORM.addToCreativeTab(COMBAT_TAB, item);
		}
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

	private static Supplier<Item> armour(String name, ArmorType type) {
		return item(name, () -> new Item.Properties().humanoidArmor(WOOL, type), Item::new);
	}

	/**
	 * The properties are only built inside the registration: on NeoForge, building armour
	 * properties any earlier (at class load) touches registries that are already frozen.
	 */
	private static Supplier<Item> item(String name, Supplier<Item.Properties> properties, Function<Item.Properties, Item> kind) {
		Identifier id = Summiteer.id(name);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		return Services.PLATFORM.register(Registries.ITEM, id, () -> kind.apply(properties.get().setId(key)));
	}
}
