package com.gabbymaybelle.summiteer;

import static com.gabbymaybelle.summiteer.ClientTest.check;
import static com.gabbymaybelle.summiteer.ClientTest.createWorld;
import static com.gabbymaybelle.summiteer.ClientTest.run;
import static com.gabbymaybelle.summiteer.ClientTest.waitTicks;

import com.gabbymaybelle.summiteer.client.Whiteout;
import com.gabbymaybelle.summiteer.clothes.Clothes;
import com.gabbymaybelle.summiteer.gear.Gear;
import com.gabbymaybelle.summiteer.zones.Zone;
import com.gabbymaybelle.summiteer.zones.Zones;
import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosSlotTypes;

/**
 * The NeoForge jar's in-game test: the same checks as the Fabric game test (fabric/src/gametest),
 * so both loaders are held to the same behaviour.
 */
final class SummiteerClientTest {
	private static final int[] BOX = new int[2];
	/** The summit test site: a stone platform at summit height, 21 blocks across, open to the sky. */
	private static final int SITE = 10;
	private static final int[] SITE_AT = new int[3];

	private SummiteerClientTest() {
	}

	static void steps() {
		run(mc -> settingsFile());
		createWorld("summiteer-clienttest");
		run(mc -> check(ModList.get().isLoaded(Summiteer.MOD_ID), "The mod isn't loaded"));

		// Zones: a column of stony peaks and one of plains beside the player, then each height asked.
		run(mc -> {
			BlockPos at = mc.player.blockPosition();
			BOX[0] = at.getX();
			BOX[1] = at.getZ();
		});
		run(mc -> {
			ServerLevel level = server(mc).overworld();
			int sea = level.getSeaLevel();
			int top = sea + Config.summitAboveSeaLevel + 4;
			int x = BOX[0], z = BOX[1];
			command(mc, "fillbiome " + (x + 8) + " " + sea + " " + z + " " + (x + 12) + " " + top + " " + (z + 4) + " minecraft:stony_peaks");
			command(mc, "fillbiome " + (x - 12) + " " + sea + " " + z + " " + (x - 8) + " " + top + " " + (z + 4) + " minecraft:plains");
		});
		ClientTest.waitTicks(4);
		run(mc -> {
			IntegratedServer server = server(mc);
			server.executeBlocking(() -> {
				ServerLevel level = server.overworld();
				int sea = level.getSeaLevel();
				int high = sea + Config.highAboveSeaLevel;
				int summit = sea + Config.summitAboveSeaLevel;
				BlockPos peaks = new BlockPos(BOX[0] + 10, 0, BOX[1] + 2);
				BlockPos plains = new BlockPos(BOX[0] - 10, 0, BOX[1] + 2);
				expect(Zone.LOW, Zones.at(level, peaks.atY(high - 2)), "stony peaks below the treeline");
				expect(Zone.HIGH, Zones.at(level, peaks.atY(high + 2)), "stony peaks above the treeline");
				expect(Zone.LOW, Zones.at(level, plains.atY(high + 2)), "plains at the same height");
				expect(Zone.SUMMIT, Zones.at(level, plains.atY(summit + 1)), "plains at summit height");
				expect(Zone.LOW, Zones.at(server.getLevel(Level.NETHER), peaks.atY(high + 2)), "the Nether");
			});
		});

		wind();
		cold();
		warmth();
		gear();
	}

	/**
	 * Wind: standing still on the summit in the open, gusts (every 8 to 16 seconds) push the player
	 * off the spot; under a roof, nothing moves them. 20 seconds each.
	 */
	private static void wind() {
		run(mc -> onServer(mc, server -> {
			ServerPlayer player = player(server);
			BlockPos at = player.blockPosition();
			SITE_AT[0] = at.getX();
			SITE_AT[1] = player.level().getSeaLevel() + Config.summitAboveSeaLevel + 2;
			SITE_AT[2] = at.getZ();
			serverCommand(server, "fill " + box(0, 0) + " minecraft:stone");
			serverCommand(server, "gamemode survival @a");
			serverCommand(server, "time set noon");
			serverCommand(server, "weather clear");
		}));
		standInTheMiddle();
		waitTicks(20);
		ClientTest.screenshot();
		waitTicks(380);
		run(mc -> onServer(mc, server -> {
			double open = drift(server);
			check(open > 0.4, "Wind: gusts didn't push you on an open summit (moved " + String.format("%.2f", open) + " blocks)");
			serverCommand(server, "fill " + box(4, 4) + " minecraft:stone");
		}));
		standInTheMiddle();
		waitTicks(400);
		run(mc -> onServer(mc, server -> {
			double sheltered = drift(server);
			check(sheltered < 0.1, "Wind: a gust moved you under a roof (moved " + String.format("%.2f", sheltered) + " blocks)");
			serverCommand(server, "fill " + box(4, 4) + " minecraft:air");
		}));
	}

	/**
	 * Cold: in a snowstorm on a frozen summit the player starts to freeze; under a roof they don't.
	 * The wind is switched off meanwhile so it can't blow them off the site.
	 */
	private static void cold() {
		run(mc -> onServer(mc, server -> {
			Config.wind = false;
			serverCommand(server, "fillbiome " + (SITE_AT[0] - SITE) + " " + (SITE_AT[1] - 2) + " " + (SITE_AT[2] - SITE) + " "
					+ (SITE_AT[0] + SITE) + " " + (SITE_AT[1] + 12) + " " + (SITE_AT[2] + SITE) + " minecraft:frozen_peaks");
			serverCommand(server, "weather rain");
		}));
		standInTheMiddle();
		waitTicks(300);
		run(mc -> onServer(mc, server -> {
			int open = player(server).getTicksFrozen();
			check(open >= 40, "Cold: a snowstorm on the summit didn't freeze you (frozen " + open + " of 140 after 15 s)");
			serverCommand(server, "fill " + box(4, 4) + " minecraft:stone");
		}));
		standInTheMiddle();
		waitTicks(200);
		run(mc -> onServer(mc, server -> {
			int sheltered = player(server).getTicksFrozen();
			check(sheltered == 0, "Cold: the snowstorm froze you under a roof (frozen " + sheltered + ")");
			serverCommand(server, "fill " + box(4, 4) + " minecraft:air");
		}));
		whiteout();
		run(mc -> Config.wind = true);
	}

	/**
	 * Whiteout: still on the summit in the snowstorm, out from under the roof, the fog fades all the
	 * way in; once the weather clears it fades all the way out. The fade only moves while the game
	 * draws fog through the mixin, so this also proves the mixin is in place. The screenshots are for
	 * judging the look.
	 */
	private static void whiteout() {
		standInTheMiddle();
		waitTicks(100);
		ClientTest.screenshot();
		run(mc -> check(Whiteout.amount() >= 0.99F, "Whiteout: the fog didn't close in on a summit in a snowstorm (faded in " + Whiteout.amount() + ")"));
		float[] ends = new float[2];
		run(mc -> {
			ends[0] = Whiteout.lastEnd();
			onServer(mc, server -> equip(player(server), "head", new ItemStack(Gear.SNOW_GOGGLES.get())));
		});
		waitTicks(20);
		ClientTest.screenshot();
		run(mc -> {
			ends[1] = Whiteout.lastEnd();
			check(ends[0] <= 21 && ends[1] >= 40, "Whiteout: snow goggles didn't see further (fog ends at " + ends[0] + " blocks, " + ends[1] + " with goggles)");
			onServer(mc, server -> equip(player(server), "head", ItemStack.EMPTY));
		});
		run(mc -> onServer(mc, server -> serverCommand(server, "weather clear")));
		waitTicks(200);
		run(mc -> check(Whiteout.amount() <= 0.01F, "Whiteout: the fog didn't lift when the storm ended (faded in " + Whiteout.amount() + ")"));
	}

	private static final String[] DRESSED = { "nothing", "a beanie", "a beanie and parka", "a beanie, parka and snow pants",
			"all four clothes", "all four clothes and climbing gloves" };
	private static final int[] FROZEN = new int[DRESSED.length];

	/**
	 * Warmth: back in the snowstorm on the summit, the player dresses one piece at a time (beanie,
	 * parka, snow pants, wool socks, then climbing gloves in Curios' hands slot) and freezes for 15 seconds
	 * in each: every piece freezes them slower, and the full set stops it. Then, in nothing but
	 * leather boots (vanilla's freeze-proof leather), being fully frozen still hurts.
	 */
	private static void warmth() {
		run(mc -> onServer(mc, server -> {
			Config.wind = false;
			serverCommand(server, "weather rain");
		}));
		// The rain takes a moment to start.
		waitTicks(40);
		for (int step = 0; step < DRESSED.length; step++) {
			int pieces = step;
			run(mc -> onServer(mc, server -> dress(player(server), pieces)));
			// Warmth is looked at once a second: let the new clothes count before the clock starts.
			waitTicks(30);
			standInTheMiddle();
			waitTicks(300);
			run(mc -> onServer(mc, server -> {
				FROZEN[pieces] = player(server).getTicksFrozen();
				Summiteer.LOGGER.info("[test] wearing {}: frozen {} after 15 s", DRESSED[pieces], FROZEN[pieces]);
			}));
		}
		run(mc -> {
			check(FROZEN[0] >= 45, "Warmth: with nothing on you didn't freeze (frozen " + FROZEN[0] + " after 15 s)");
			for (int step = 1; step < DRESSED.length - 1; step++) {
				check(FROZEN[step] < FROZEN[step - 1], "Warmth: wearing " + DRESSED[step] + " didn't freeze you slower than " + DRESSED[step - 1]
						+ " (frozen " + FROZEN[step] + " against " + FROZEN[step - 1] + ")");
			}
			int last = DRESSED.length - 1;
			check(FROZEN[last] == 0, "Warmth: " + DRESSED[last] + " didn't keep the cold off (frozen " + FROZEN[last] + ")");
		});

		// Leather: vanilla makes it freeze-proof, but up high only warmth counts.
		run(mc -> onServer(mc, server -> {
			dress(player(server), 0);
			player(server).setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
		}));
		// Let the change of clothes count (warmth is looked at once a second), or the full set's
		// warmth would let you thaw for a moment first.
		waitTicks(30);
		standInTheMiddle();
		run(mc -> onServer(mc, server -> {
			ServerPlayer player = player(server);
			player.setTicksFrozen(player.getTicksRequiredToFreeze() + 10);
			player.setHealth(player.getMaxHealth());
			// Hungry enough not to heal, so the freeze damage shows.
			player.getFoodData().setFoodLevel(10);
			serverCommand(server, "difficulty normal");
		}));
		waitTicks(100);
		run(mc -> onServer(mc, server -> {
			ServerPlayer player = player(server);
			float health = player.getHealth();
			check(health < 20.0F, "Warmth: leather boots stopped the cold hurting up high (health " + health
					+ " of 20 after 5 s fully frozen; frozen " + player.getTicksFrozen() + " of 140 at the end)");
			player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
			player.getFoodData().setFoodLevel(20);
			serverCommand(server, "weather clear");
			Config.wind = true;
		}));
	}

	/** Dresses the player in the first {@code pieces} of: beanie, parka, snow pants, wool socks, climbing gloves. */
	private static void dress(ServerPlayer player, int pieces) {
		EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
		Item[] clothes = { Clothes.BEANIE.get(), Clothes.PARKA.get(), Clothes.SNOW_PANTS.get(), Clothes.WOOL_SOCKS.get() };
		for (int i = 0; i < slots.length; i++) {
			player.setItemSlot(slots[i], i < pieces ? new ItemStack(clothes[i]) : ItemStack.EMPTY);
		}
		equip(player, "hands", pieces > 4 ? new ItemStack(Clothes.CLIMBING_GLOVES.get()) : ItemStack.EMPTY);
	}

	/** What each part of the gear check measured, for the checks at the end. */
	private static final double[] CLIMB = new double[5];
	private static final int[] LOW_FLOOR = new int[1];
	private static final String[] RIGHT_CLICK = new String[1];
	private static final float[] LOST = new float[2];
	private static final double[] SLID = new double[2];
	private static final String[] WALKS = new String[2];

	/**
	 * The climbing gear, on the summit site with the wind and cold off. An ice wall stands three
	 * blocks east of the middle, 14 high. Walking into it for three seconds climbs it with the ice
	 * axe, faster in climbing gloves, and not at all without the axe. Dropped from 18 blocks up
	 * beside it, the axe slides you down unhurt; without it the fall hurts. On packed ice a shove
	 * slides you less in crampons, and in crampons the game lets you walk on powder snow.
	 */
	private static void gear() {
		run(mc -> onServer(mc, server -> {
			Config.wind = false;
			Config.cold = false;
			serverCommand(server, "weather clear");
			serverCommand(server, "fill " + (SITE_AT[0] + 3) + " " + (SITE_AT[1] + 1) + " " + (SITE_AT[2] - 2) + " "
					+ (SITE_AT[0] + 3) + " " + (SITE_AT[1] + 14) + " " + (SITE_AT[2] + 2) + " minecraft:packed_ice");
			// Hungry enough not to heal, so a hard landing shows.
			player(server).getFoodData().setFoodLevel(10);
		}));
		climb(0, false, false);
		climb(1, true, false);
		climb(2, true, true);
		// Rock: the same wall in stone, on the summit and then below the treeline.
		run(mc -> onServer(mc, server -> serverCommand(server, "fill " + (SITE_AT[0] + 3) + " " + (SITE_AT[1] + 1) + " " + (SITE_AT[2] - 2) + " "
				+ (SITE_AT[0] + 3) + " " + (SITE_AT[1] + 14) + " " + (SITE_AT[2] + 2) + " minecraft:stone")));
		climb(3, true, false);
		run(mc -> onServer(mc, server -> {
			int floor = player(server).level().getSeaLevel() + 20;
			LOW_FLOOR[0] = floor;
			serverCommand(server, "fill " + (SITE_AT[0] - 1) + " " + floor + " " + (SITE_AT[2] - 2) + " " + (SITE_AT[0] + 3) + " " + (floor + 8) + " " + (SITE_AT[2] + 2) + " minecraft:air");
			serverCommand(server, "fill " + (SITE_AT[0] - 1) + " " + floor + " " + (SITE_AT[2] - 2) + " " + (SITE_AT[0] + 3) + " " + floor + " " + (SITE_AT[2] + 2) + " minecraft:stone");
			serverCommand(server, "fill " + (SITE_AT[0] + 3) + " " + (floor + 1) + " " + (SITE_AT[2] - 2) + " " + (SITE_AT[0] + 3) + " " + (floor + 8) + " " + (SITE_AT[2] + 2) + " minecraft:stone");
		}));
		climb(4, true, false, () -> LOW_FLOOR[0]);
		run(mc -> onServer(mc, server -> serverCommand(server, "fill " + (SITE_AT[0] + 3) + " " + (SITE_AT[1] + 1) + " " + (SITE_AT[2] - 2) + " "
				+ (SITE_AT[0] + 3) + " " + (SITE_AT[1] + 14) + " " + (SITE_AT[2] + 2) + " minecraft:packed_ice")));
		run(mc -> {
			check(CLIMB[0] < 0.6, "Ice axe: you climbed an ice wall without it (rose " + String.format("%.2f", CLIMB[0]) + " blocks in 3 s)");
			check(CLIMB[1] > 1.5, "Ice axe: walking into an ice wall didn't climb it (rose " + String.format("%.2f", CLIMB[1]) + " blocks in 3 s)");
			check(CLIMB[2] > CLIMB[1] + 0.5, "Ice axe: climbing gloves didn't climb faster (rose " + String.format("%.2f", CLIMB[2])
					+ " blocks against " + String.format("%.2f", CLIMB[1]) + ")");
			check(CLIMB[3] > 1.5, "Ice axe: it didn't climb rock on a summit (rose " + String.format("%.2f", CLIMB[3]) + " blocks in 3 s)");
			check(CLIMB[4] < 0.6, "Ice axe: it climbed rock below the treeline (rose " + String.format("%.2f", CLIMB[4]) + " blocks in 3 s)");
		});

		rightClick();
		wornGear();

		drop(0, false);
		drop(1, true);
		run(mc -> {
			check(LOST[0] >= 4, "Ice axe: the fall didn't hurt without it (lost " + LOST[0] + " health)");
			check(LOST[1] == 0, "Ice axe: sliding down the wall with it, the fall still hurt (lost " + LOST[1] + " health)");
		});

		slide(0, false);
		slide(1, true);
		run(mc -> check(SLID[1] < SLID[0] * 0.6, "Crampons: you still slip on ice (slid " + String.format("%.2f", SLID[1]) + " blocks in them, "
				+ String.format("%.2f", SLID[0]) + " without)"));

		powderSnow(0, false);
		powderSnow(1, true);
		run(mc -> check(WALKS[0].equals("server no, client no") && WALKS[1].equals("server yes, client yes"),
				"Crampons: you can't walk on powder snow in them, or can without (in them: " + WALKS[1] + "; without: " + WALKS[0] + ")"));

		run(mc -> onServer(mc, server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			equip(player, "hands", ItemStack.EMPTY);
			equip(player, "feet", ItemStack.EMPTY);
			player.getFoodData().setFoodLevel(20);
			Config.wind = true;
			Config.cold = true;
		}));
	}

	/** Walks east into the ice wall for three seconds; stores how many blocks the player rose. */
	private static void climb(int index, boolean axe, boolean gloves) {
		climb(index, axe, gloves, () -> SITE_AT[1]);
	}

	/** Walks east into the wall three blocks east of the middle, standing on {@code floor}. */
	private static void climb(int index, boolean axe, boolean gloves, java.util.function.IntSupplier floor) {
		run(mc -> onServer(mc, server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, axe ? new ItemStack(Gear.ICE_AXE.get()) : ItemStack.EMPTY);
			equip(player, "hands", gloves ? new ItemStack(Clothes.CLIMBING_GLOVES.get()) : ItemStack.EMPTY);
			// Facing east (yaw -90), towards the wall.
			serverCommand(server, "tp @a " + (SITE_AT[0] + 0.5) + " " + (floor.getAsInt() + 1) + " " + (SITE_AT[2] + 0.5) + " -90 0");
			player.setDeltaMovement(0, 0, 0);
		}));
		waitTicks(10);
		run(mc -> mc.options.keyUp.setDown(true));
		waitTicks(60);
		run(mc -> {
			mc.options.keyUp.setDown(false);
			onServer(mc, server -> {
				CLIMB[index] = player(server).getY() - (floor.getAsInt() + 1);
				Summiteer.LOGGER.info("[test] climb with axe {} and gloves {}: rose {}", axe, gloves, CLIMB[index]);
			});
		});
		if (axe && gloves) {
			ClientTest.screenshot();
		}
	}

	/**
	 * Right-click: crampons in hand go on in the empty feet slot and leave the hand empty; a second
	 * pair of climbing gloves (named "new") swaps with the pair already worn, which ends up in hand.
	 */
	private static void rightClick() {
		run(mc -> onServer(mc, server -> {
			ServerPlayer player = player(server);
			equip(player, "feet", ItemStack.EMPTY);
			equip(player, "hands", new ItemStack(Clothes.CLIMBING_GLOVES.get()));
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Gear.CRAMPONS.get()));
			player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
			var curios = CuriosApi.getCuriosInventory(player).orElseThrow();
			boolean cramponsOn = curios.getStacksHandler("feet").orElseThrow().getStacks().getStackInSlot(0).is(Gear.CRAMPONS.get())
					&& player.getMainHandItem().isEmpty();
			ItemStack fresh = new ItemStack(Clothes.CLIMBING_GLOVES.get());
			fresh.set(DataComponents.CUSTOM_NAME, Component.literal("new"));
			player.setItemInHand(InteractionHand.MAIN_HAND, fresh);
			player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
			ItemStack worn = curios.getStacksHandler("hands").orElseThrow().getStacks().getStackInSlot(0);
			ItemStack hand = player.getMainHandItem();
			boolean swapped = worn.has(DataComponents.CUSTOM_NAME) && hand.is(Clothes.CLIMBING_GLOVES.get()) && !hand.has(DataComponents.CUSTOM_NAME);
			equip(player, "feet", ItemStack.EMPTY);
			equip(player, "hands", ItemStack.EMPTY);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			RIGHT_CLICK[0] = "crampons " + (cramponsOn ? "on" : "not on") + ", gloves " + (swapped ? "swapped" : "not swapped");
			Summiteer.LOGGER.info("[test] right-click: {}", RIGHT_CLICK[0]);
		}));
		run(mc -> check(RIGHT_CLICK[0].equals("crampons on, gloves swapped"), "Right-click: gear didn't go on, swapping with what you wore (" + RIGHT_CLICK[0] + ")"));
	}

	/**
	 * Worn gear on the player, for judging the look: the camera turns to face the player, who wears
	 * the goggles, crampons and gloves, first bare-headed and then with the beanie and wool socks
	 * (the gear should show over them).
	 */
	private static void wornGear() {
		run(mc -> onServer(mc, server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Gear.ICE_AXE.get()));
			equip(player, "head", new ItemStack(Gear.SNOW_GOGGLES.get()));
			equip(player, "feet", new ItemStack(Gear.CRAMPONS.get()));
			equip(player, "hands", new ItemStack(Clothes.CLIMBING_GLOVES.get()));
			serverCommand(server, "tp @a " + (SITE_AT[0] - 5.5) + " " + (SITE_AT[1] + 1) + " " + (SITE_AT[2] + 0.5) + " 0 0");
		}));
		// Facing the player, close up, with the hotbar and chat hidden.
		int[] fov = new int[1];
		run(mc -> {
			fov[0] = mc.options.fov().get();
			mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
			mc.options.fov().set(45);
		});
		waitTicks(20);
		ClientTest.screenshot();
		run(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		waitTicks(10);
		ClientTest.screenshot();
		run(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		run(mc -> onServer(mc, server -> {
			player(server).setItemSlot(EquipmentSlot.HEAD, new ItemStack(Clothes.BEANIE.get()));
			player(server).setItemSlot(EquipmentSlot.FEET, new ItemStack(Clothes.WOOL_SOCKS.get()));
		}));
		waitTicks(10);
		ClientTest.screenshot();
		run(mc -> {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
			mc.options.fov().set(fov[0]);
			onServer(mc, server -> {
				ServerPlayer player = player(server);
				player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
				player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				equip(player, "head", ItemStack.EMPTY);
				equip(player, "feet", ItemStack.EMPTY);
				equip(player, "hands", ItemStack.EMPTY);
			});
		});
	}

	/** Drops the player from 18 blocks up, against the ice wall; stores how much health the landing cost. */
	private static void drop(int index, boolean axe) {
		run(mc -> onServer(mc, server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, axe ? new ItemStack(Gear.ICE_AXE.get()) : ItemStack.EMPTY);
			player.setHealth(player.getMaxHealth());
			// Just touching the wall: the player is 0.6 wide, so their east side is at x + 2.99.
			serverCommand(server, "tp @a " + (SITE_AT[0] + 2.69) + " " + (SITE_AT[1] + 18) + " " + (SITE_AT[2] + 0.5) + " -90 0");
			player.setDeltaMovement(0, 0, 0);
		}));
		waitTicks(120);
		run(mc -> onServer(mc, server -> {
			LOST[index] = player(server).getMaxHealth() - player(server).getHealth();
			Summiteer.LOGGER.info("[test] drop with axe {}: lost {} health", axe, LOST[index]);
		}));
	}

	/** Shoves the player west across packed ice; stores how far they slid. */
	private static void slide(int index, boolean crampons) {
		run(mc -> onServer(mc, server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			equip(player, "feet", crampons ? new ItemStack(Gear.CRAMPONS.get()) : ItemStack.EMPTY);
			serverCommand(server, "fill " + (SITE_AT[0] - 9) + " " + SITE_AT[1] + " " + (SITE_AT[2] - 1) + " "
					+ (SITE_AT[0] - 1) + " " + SITE_AT[1] + " " + (SITE_AT[2] + 1) + " minecraft:packed_ice");
			serverCommand(server, "tp @a " + (SITE_AT[0] - 0.5) + " " + (SITE_AT[1] + 1) + " " + (SITE_AT[2] + 0.5) + " 90 0");
		}));
		waitTicks(10);
		run(mc -> onServer(mc, server -> {
			ServerPlayer player = player(server);
			player.setDeltaMovement(-0.5, 0, 0);
			// Players move themselves, so the client must be told about the shove.
			player.syncVelocity = true;
		}));
		waitTicks(40);
		run(mc -> onServer(mc, server -> {
			SLID[index] = (SITE_AT[0] - 0.5) - player(server).getX();
			Summiteer.LOGGER.info("[test] shoved on ice with crampons {}: slid {}", crampons, SLID[index]);
		}));
	}

	/**
	 * Whether the game lets the player walk on powder snow, asked the way vanilla asks it, on the
	 * server and on the client (players move on the client, so both must agree). The crampons go on
	 * or off a second first, so the change reaches the client.
	 */
	private static void powderSnow(int index, boolean crampons) {
		run(mc -> onServer(mc, server -> equip(player(server), "feet", crampons ? new ItemStack(Gear.CRAMPONS.get()) : ItemStack.EMPTY)));
		waitTicks(20);
		run(mc -> {
			boolean[] server = new boolean[1];
			onServer(mc, s -> server[0] = PowderSnowBlock.canEntityWalkOnPowderSnow(player(s)));
			boolean client = PowderSnowBlock.canEntityWalkOnPowderSnow(mc.player);
			WALKS[index] = "server " + (server[0] ? "yes" : "no") + ", client " + (client ? "yes" : "no");
			Summiteer.LOGGER.info("[test] can walk on powder snow with crampons {}: {}", crampons, WALKS[index]);
		});
	}

	/** Puts something in the first of one of Curios' slots ("hands", "head", "feet"); it must fit. */
	private static void equip(ServerPlayer player, String slot, ItemStack stack) {
		var curios = CuriosApi.getCuriosInventory(player).orElseThrow(() -> new AssertionError("Curios gave the player no slots"));
		check(curios.getStacksHandler(slot).isPresent(), "Curios gave the player no " + slot + " slot (slots: " + curios.getCurios().keySet() + ")");
		if (!stack.isEmpty()) {
			check(CuriosSlotTypes.getItemSlotTypes(stack, player).containsKey(slot), stack.getHoverName().getString() + " doesn't fit Curios' " + slot + " slot");
		}
		curios.setEquippedCurio(slot, 0, stack);
	}

	private static void standInTheMiddle() {
		run(mc -> onServer(mc, server -> {
			// Block centres as numbers: "-6" + ".5" would be -6.5, a block off from -6 + 0.5.
			serverCommand(server, "tp @a " + (SITE_AT[0] + 0.5) + " " + (SITE_AT[1] + 1) + " " + (SITE_AT[2] + 0.5));
			ServerPlayer player = player(server);
			player.setDeltaMovement(0, 0, 0);
			player.setTicksFrozen(0);
		}));
		waitTicks(4);
	}

	private static String box(int dyFrom, int dyTo) {
		return (SITE_AT[0] - SITE) + " " + (SITE_AT[1] + dyFrom) + " " + (SITE_AT[2] - SITE) + " "
				+ (SITE_AT[0] + SITE) + " " + (SITE_AT[1] + dyTo) + " " + (SITE_AT[2] + SITE);
	}

	/** How far the player has moved sideways from the middle of the site, in blocks. */
	private static double drift(IntegratedServer server) {
		ServerPlayer player = player(server);
		return Math.hypot(player.getX() - (SITE_AT[0] + 0.5), player.getZ() - (SITE_AT[2] + 0.5));
	}

	private static ServerPlayer player(IntegratedServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/** Runs on the server thread and waits for it, so a failed check fails this step. */
	private static void onServer(net.minecraft.client.Minecraft mc, java.util.function.Consumer<IntegratedServer> action) {
		IntegratedServer server = server(mc);
		server.executeBlocking(() -> action.accept(server));
	}

	private static void serverCommand(IntegratedServer server, String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
	}

	private static IntegratedServer server(net.minecraft.client.Minecraft mc) {
		IntegratedServer server = mc.getSingleplayerServer();
		check(server != null, "No singleplayer server");
		return server;
	}

	/** Runs a command on the server straight away (the caller is already one step). */
	private static void command(net.minecraft.client.Minecraft mc, String command) {
		IntegratedServer server = server(mc);
		server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
	}

	private static void expect(Zone expected, Zone actual, String where) {
		check(expected == actual, "Zone for " + where + ": expected " + expected + ", got " + actual);
	}

	/** The settings file is written with both height bands. */
	private static void settingsFile() {
		try {
			String text = Files.readString(Config.path());
			check(text.contains("highAboveSeaLevel=70") && text.contains("summitAboveSeaLevel=120"), "The settings file doesn't have both height bands at their defaults");
		} catch (IOException e) {
			throw new AssertionError("The settings file is missing", e);
		}
	}
}
