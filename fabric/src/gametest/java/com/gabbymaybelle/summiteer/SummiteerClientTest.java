package com.gabbymaybelle.summiteer;

import com.gabbymaybelle.summiteer.client.Whiteout;
import com.gabbymaybelle.summiteer.clothes.Clothes;
import com.gabbymaybelle.summiteer.zones.Zone;
import com.gabbymaybelle.summiteer.zones.Zones;
import eu.pb4.trinkets.api.TrinketInventory;
import eu.pb4.trinkets.api.TrinketsApi;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The mod in a real game. Run with {@code ./gradlew :fabric:runClientGameTest}; screenshots land
 * in fabric/build/run/clientGameTest/screenshots. Each feature adds its own checks here as it
 * lands, and each check is proven by running it once with the feature switched off. The NeoForge
 * jar runs the same checks (neoforge/src/clienttest).
 */
public class SummiteerClientTest implements FabricClientGameTest {
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext ctx) {
		settingsFile();
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			boolean loaded = world.getServer().computeOnServer(server -> FabricLoader.getInstance().isModLoaded(Summiteer.MOD_ID));
			check(loaded, "the mod is loaded in a new world");
			zones(world);
			wind(ctx, world);
			cold(ctx, world);
			warmth(ctx, world);
		}
		if (!failures.isEmpty()) {
			throw new AssertionError(failures.size() + " check(s) failed:\n - " + String.join("\n - ", failures));
		}
	}

	/** The settings file is written with both height bands. */
	private void settingsFile() {
		try {
			String text = Files.readString(Config.path());
			check(text.contains("highAboveSeaLevel=70") && text.contains("summitAboveSeaLevel=120"), "the settings file has both height bands at their defaults");
		} catch (IOException e) {
			check(false, "the settings file exists (" + e + ")");
		}
	}

	/**
	 * The zones in the world: the test paints a column of stony peaks and one of plains beside the
	 * player with /fillbiome, then asks which zone points at each height are in, measured from the
	 * world's own sea level.
	 */
	private void zones(TestSingleplayerContext world) {
		List<String> results = world.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			ServerLevel level = player.level();
			BlockPos at = player.blockPosition();
			int sea = level.getSeaLevel();
			int high = sea + Config.highAboveSeaLevel;
			int summit = sea + Config.summitAboveSeaLevel;
			String peaks = (at.getX() + 8) + " " + (sea) + " " + at.getZ() + " " + (at.getX() + 12) + " " + (summit + 4) + " " + (at.getZ() + 4);
			String plains = (at.getX() - 12) + " " + (sea) + " " + at.getZ() + " " + (at.getX() - 8) + " " + (summit + 4) + " " + (at.getZ() + 4);
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "fillbiome " + peaks + " minecraft:stony_peaks");
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "fillbiome " + plains + " minecraft:plains");
			BlockPos peaksAt = new BlockPos(at.getX() + 10, 0, at.getZ() + 2);
			BlockPos plainsAt = new BlockPos(at.getX() - 10, 0, at.getZ() + 2);
			List<String> out = new ArrayList<>();
			out.add(Zones.at(level, peaksAt.atY(high - 2)) + " stony peaks below the treeline");
			out.add(Zones.at(level, peaksAt.atY(high + 2)) + " stony peaks above the treeline");
			out.add(Zones.at(level, plainsAt.atY(high + 2)) + " plains at the same height");
			out.add(Zones.at(level, plainsAt.atY(summit + 1)) + " plains at summit height");
			out.add(Zones.at(server.getLevel(net.minecraft.world.level.Level.NETHER), peaksAt.atY(high + 2)) + " the Nether");
			return out;
		});
		Zone[] expected = { Zone.LOW, Zone.HIGH, Zone.LOW, Zone.SUMMIT, Zone.LOW };
		for (int i = 0; i < expected.length; i++) {
			String result = results.get(i);
			check(result.startsWith(expected[i].name() + " "), "zone: expected " + expected[i] + " for" + result.substring(result.indexOf(' ')) + ", got " + result.substring(0, result.indexOf(' ')));
		}
	}

	/** The summit test site: a stone platform at summit height, 21 blocks across, open to the sky. */
	private static final int SITE = 10;
	private final int[] site = new int[3];

	/**
	 * Builds the test site at summit height above the player and puts them in the middle of it in
	 * survival (the weather leaves creative players alone), at noon in clear weather.
	 */
	private void buildSite(TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			BlockPos at = player.blockPosition();
			site[0] = at.getX();
			site[1] = player.level().getSeaLevel() + Config.summitAboveSeaLevel + 2;
			site[2] = at.getZ();
			command(server, "fill " + box(0, 0) + " minecraft:stone");
			command(server, "gamemode survival @a");
			command(server, "time set noon");
			command(server, "weather clear");
		});
		standInTheMiddle(world);
	}

	private void standInTheMiddle(TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			// Block centres as numbers: "-6" + ".5" would be -6.5, a block off from -6 + 0.5.
			command(server, "tp @a " + (site[0] + 0.5) + " " + (site[1] + 1) + " " + (site[2] + 0.5));
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.setDeltaMovement(0, 0, 0);
			player.setTicksFrozen(0);
		});
	}

	/** Puts a stone roof over the site (dy 4) or takes it off. */
	private void roof(TestSingleplayerContext world, boolean on) {
		world.getServer().runOnServer(server -> command(server, "fill " + box(4, 4) + (on ? " minecraft:stone" : " minecraft:air")));
	}

	private String box(int dyFrom, int dyTo) {
		return (site[0] - SITE) + " " + (site[1] + dyFrom) + " " + (site[2] - SITE) + " " + (site[0] + SITE) + " " + (site[1] + dyTo) + " " + (site[2] + SITE);
	}

	/** How far the player has moved sideways from the middle of the site, in blocks. */
	private double drift(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			return Math.hypot(player.getX() - (site[0] + 0.5), player.getZ() - (site[2] + 0.5));
		});
	}

	private int frozen(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().getFirst().getTicksFrozen());
	}

	/**
	 * Wind: standing still on the summit in the open, gusts (every 8 to 16 seconds) push the player
	 * off the spot; under a roof, nothing moves them. 20 seconds each.
	 */
	private void wind(ClientGameTestContext ctx, TestSingleplayerContext world) {
		buildSite(world);
		ctx.waitTicks(20);
		ctx.takeScreenshot("summit-clear");
		ctx.waitTicks(380);
		double open = drift(world);
		check(open > 0.4, "wind: gusts push you on an open summit (moved " + String.format("%.2f", open) + " blocks)");

		roof(world, true);
		standInTheMiddle(world);
		ctx.waitTicks(400);
		double sheltered = drift(world);
		check(sheltered < 0.1, "wind: no gusts under a roof (moved " + String.format("%.2f", sheltered) + " blocks)");
		roof(world, false);
	}

	/**
	 * Cold: in a snowstorm on a frozen summit the player starts to freeze; under a roof they don't.
	 * The wind is switched off meanwhile so it can't blow them off the site.
	 */
	private void cold(ClientGameTestContext ctx, TestSingleplayerContext world) {
		Config.wind = false;
		world.getServer().runOnServer(server -> {
			command(server, "fillbiome " + (site[0] - SITE) + " " + (site[1] - 2) + " " + (site[2] - SITE) + " "
					+ (site[0] + SITE) + " " + (site[1] + 12) + " " + (site[2] + SITE) + " minecraft:frozen_peaks");
			command(server, "weather rain");
		});
		standInTheMiddle(world);
		ctx.waitTicks(300);
		int open = frozen(world);
		check(open >= 40, "cold: a snowstorm on the summit freezes you (frozen " + open + " of 140 after 15 s)");

		roof(world, true);
		standInTheMiddle(world);
		ctx.waitTicks(200);
		int sheltered = frozen(world);
		check(sheltered == 0, "cold: under a roof the snowstorm doesn't freeze you (frozen " + sheltered + ")");
		roof(world, false);
		whiteout(ctx, world);
		Config.wind = true;
	}

	/**
	 * Whiteout: still on the summit in the snowstorm, out from under the roof, the fog fades all the
	 * way in; once the weather clears it fades all the way out. The fade only moves while the game
	 * draws fog through the mixin, so this also proves the mixin is in place. The screenshots are for
	 * judging the look.
	 */
	private void whiteout(ClientGameTestContext ctx, TestSingleplayerContext world) {
		standInTheMiddle(world);
		ctx.waitTicks(100);
		float in = ctx.computeOnClient(client -> Whiteout.amount());
		ctx.takeScreenshot("summit-whiteout");
		check(in >= 0.99F, "whiteout: the fog closes in on a summit in a snowstorm (faded in " + in + ")");

		world.getServer().runOnServer(server -> command(server, "weather clear"));
		ctx.waitTicks(200);
		float out = ctx.computeOnClient(client -> Whiteout.amount());
		check(out <= 0.01F, "whiteout: the fog lifts when the storm ends (faded in " + out + ")");
	}

	/**
	 * Warmth: back in the snowstorm on the summit, the player dresses one piece at a time (beanie,
	 * parka, snow pants, wool socks, then mittens in Trinkets Updated's glove slot) and freezes for
	 * 15 seconds in each: every piece freezes them slower, and the full set stops it. Then, in
	 * nothing but leather boots (vanilla's freeze-proof leather), being fully frozen still hurts.
	 */
	private void warmth(ClientGameTestContext ctx, TestSingleplayerContext world) {
		Config.wind = false;
		world.getServer().runOnServer(server -> command(server, "weather rain"));
		// The rain takes a moment to start.
		ctx.waitTicks(40);
		String[] dressed = { "nothing", "a beanie", "a beanie and parka", "a beanie, parka and snow pants",
				"all four clothes", "all four clothes and mittens" };
		int[] frozen = new int[dressed.length];
		for (int step = 0; step < dressed.length; step++) {
			int pieces = step;
			world.getServer().runOnServer(server -> dress(server.getPlayerList().getPlayers().getFirst(), pieces));
			// Warmth is looked at once a second: let the new clothes count before the clock starts.
			ctx.waitTicks(30);
			standInTheMiddle(world);
			ctx.waitTicks(300);
			frozen[step] = frozen(world);
			Summiteer.LOGGER.info("[test] wearing {}: frozen {} after 15 s", dressed[step], frozen[step]);
		}
		check(frozen[0] >= 45, "warmth: with nothing on you freeze (frozen " + frozen[0] + " after 15 s)");
		for (int step = 1; step < dressed.length - 1; step++) {
			check(frozen[step] < frozen[step - 1], "warmth: wearing " + dressed[step] + " freezes you slower than " + dressed[step - 1]
					+ " (frozen " + frozen[step] + " against " + frozen[step - 1] + ")");
		}
		int last = dressed.length - 1;
		check(frozen[last] == 0, "warmth: " + dressed[last] + " keep the cold off (frozen " + frozen[last] + ")");

		// Leather: vanilla makes it freeze-proof, but up high only warmth counts.
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			dress(player, 0);
			player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
		});
		standInTheMiddle(world);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.setTicksFrozen(player.getTicksRequiredToFreeze() + 10);
			player.setHealth(player.getMaxHealth());
			// Hungry enough not to heal, so the freeze damage shows.
			player.getFoodData().setFoodLevel(10);
			command(server, "difficulty normal");
		});
		ctx.waitTicks(100);
		float health = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().getFirst().getHealth());
		check(health < 20.0F, "warmth: leather boots don't stop the cold hurting up high (health " + health + " of 20 after 5 s fully frozen)");

		world.getServer().runOnServer(server -> {
			dress(server.getPlayerList().getPlayers().getFirst(), 0);
			server.getPlayerList().getPlayers().getFirst().setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
			server.getPlayerList().getPlayers().getFirst().getFoodData().setFoodLevel(20);
			command(server, "weather clear");
		});
		Config.wind = true;
	}

	/** Dresses the player in the first {@code pieces} of: beanie, parka, snow pants, wool socks, mittens. */
	private void dress(ServerPlayer player, int pieces) {
		EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
		Item[] clothes = { Clothes.BEANIE.get(), Clothes.PARKA.get(), Clothes.SNOW_PANTS.get(), Clothes.WOOL_SOCKS.get() };
		for (int i = 0; i < slots.length; i++) {
			player.setItemSlot(slots[i], i < pieces ? new ItemStack(clothes[i]) : ItemStack.EMPTY);
		}
		TrinketInventory gloves = gloveSlot(player);
		ItemStack mittens = new ItemStack(Clothes.MITTENS.get());
		if (pieces > 4) {
			check(gloves.slotType().validatorCheck(mittens, gloves.getOrCreateSlotAccess(0), player), "mittens: they fit Trinkets Updated's glove slot");
		}
		gloves.setItem(0, pieces > 4 ? mittens : ItemStack.EMPTY);
	}

	/** Trinkets Updated's glove slot on the main hand. */
	private TrinketInventory gloveSlot(ServerPlayer player) {
		for (TrinketInventory inventory : TrinketsApi.getAttachment(player).getInventories().values()) {
			if (inventory.slotType().group().equals("hand") && inventory.slotType().getId().equals("glove")) {
				return inventory;
			}
		}
		throw new AssertionError("Trinkets Updated gave the player no hand/glove slot: " + TrinketsApi.getAttachment(player).getInventories().keySet());
	}

	private static void command(net.minecraft.server.MinecraftServer server, String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
	}

	private void check(boolean ok, String what) {
		if (ok) {
			Summiteer.LOGGER.info("[test] ok: {}", what);
		} else {
			Summiteer.LOGGER.error("[test] FAILED: {}", what);
			failures.add(what);
		}
	}
}
