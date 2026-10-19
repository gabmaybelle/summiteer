package com.gabbymaybelle.summiteer;

import static com.gabbymaybelle.summiteer.ClientTest.check;
import static com.gabbymaybelle.summiteer.ClientTest.createWorld;
import static com.gabbymaybelle.summiteer.ClientTest.run;
import static com.gabbymaybelle.summiteer.ClientTest.waitTicks;

import com.gabbymaybelle.summiteer.client.Whiteout;
import com.gabbymaybelle.summiteer.clothes.Clothes;
import com.gabbymaybelle.summiteer.zones.Zone;
import com.gabbymaybelle.summiteer.zones.Zones;
import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
		run(mc -> onServer(mc, server -> serverCommand(server, "weather clear")));
		waitTicks(200);
		run(mc -> check(Whiteout.amount() <= 0.01F, "Whiteout: the fog didn't lift when the storm ended (faded in " + Whiteout.amount() + ")"));
	}

	private static final String[] DRESSED = { "nothing", "a beanie", "a beanie and parka", "a beanie, parka and snow pants",
			"all four clothes", "all four clothes and mittens" };
	private static final int[] FROZEN = new int[DRESSED.length];

	/**
	 * Warmth: back in the snowstorm on the summit, the player dresses one piece at a time (beanie,
	 * parka, snow pants, wool socks, then mittens in Curios' hands slot) and freezes for 15 seconds
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

	/** Dresses the player in the first {@code pieces} of: beanie, parka, snow pants, wool socks, mittens. */
	private static void dress(ServerPlayer player, int pieces) {
		EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
		Item[] clothes = { Clothes.BEANIE.get(), Clothes.PARKA.get(), Clothes.SNOW_PANTS.get(), Clothes.WOOL_SOCKS.get() };
		for (int i = 0; i < slots.length; i++) {
			player.setItemSlot(slots[i], i < pieces ? new ItemStack(clothes[i]) : ItemStack.EMPTY);
		}
		ItemStack mittens = new ItemStack(Clothes.MITTENS.get());
		if (pieces > 4) {
			check(CuriosSlotTypes.getItemSlotTypes(mittens, player).containsKey("hands"), "Mittens: they don't fit Curios' hands slot");
		}
		var curios = CuriosApi.getCuriosInventory(player).orElseThrow(() -> new AssertionError("Curios gave the player no slots"));
		check(curios.getStacksHandler("hands").isPresent(), "Mittens: Curios gave the player no hands slot (slots: " + curios.getCurios().keySet() + ")");
		curios.setEquippedCurio("hands", 0, pieces > 4 ? mittens : ItemStack.EMPTY);
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
