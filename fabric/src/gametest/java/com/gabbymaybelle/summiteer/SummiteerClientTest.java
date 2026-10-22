package com.gabbymaybelle.summiteer;

import com.gabbymaybelle.summiteer.camp.Camp;
import com.gabbymaybelle.summiteer.camp.TrailCairnBlock;
import com.gabbymaybelle.summiteer.client.Whiteout;
import com.gabbymaybelle.summiteer.clothes.Clothes;
import com.gabbymaybelle.summiteer.gear.Gear;
import com.gabbymaybelle.summiteer.rope.AnchorBlock;
import com.gabbymaybelle.summiteer.rope.RopeBlock;
import com.gabbymaybelle.summiteer.rope.Ropes;
import com.gabbymaybelle.summiteer.weather.Wind;
import com.gabbymaybelle.summiteer.zones.Zone;
import com.gabbymaybelle.summiteer.zones.Zones;
import eu.pb4.trinkets.api.TrinketInventory;
import eu.pb4.trinkets.api.TrinketsApi;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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
			gear(ctx, world);
			rope(ctx, world);
			pearls(ctx, world);
			cairn(ctx, world);
			tent(ctx, world);
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
	 * The zones in the world: the test paints a column each of stony peaks, snowy slopes and plains
	 * beside the player with /fillbiome, then asks which zone points at each height are in, measured
	 * from the world's own sea level. Peaks are high at any height; slopes only above the treeline.
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
			String slopes = (at.getX() - 2) + " " + (sea) + " " + (at.getZ() + 8) + " " + (at.getX() + 2) + " " + (summit + 4) + " " + (at.getZ() + 12);
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "fillbiome " + plains + " minecraft:plains");
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "fillbiome " + slopes + " minecraft:snowy_slopes");
			BlockPos peaksAt = new BlockPos(at.getX() + 10, 0, at.getZ() + 2);
			BlockPos plainsAt = new BlockPos(at.getX() - 10, 0, at.getZ() + 2);
			BlockPos slopesAt = new BlockPos(at.getX(), 0, at.getZ() + 10);
			List<String> out = new ArrayList<>();
			out.add(Zones.at(level, slopesAt.atY(high - 2)) + " snowy slopes below the treeline");
			out.add(Zones.at(level, slopesAt.atY(high + 2)) + " snowy slopes above the treeline");
			out.add(Zones.at(level, peaksAt.atY(sea + 20)) + " stony peaks far below the treeline");
			out.add(Zones.at(level, peaksAt.atY(high + 2)) + " stony peaks above the treeline");
			out.add(Zones.at(level, plainsAt.atY(high + 2)) + " plains at the same height");
			out.add(Zones.at(level, plainsAt.atY(summit + 1)) + " plains at summit height");
			out.add(Zones.at(server.getLevel(net.minecraft.world.level.Level.NETHER), peaksAt.atY(high + 2)) + " the Nether");
			return out;
		});
		Zone[] expected = { Zone.LOW, Zone.HIGH, Zone.HIGH, Zone.HIGH, Zone.LOW, Zone.SUMMIT, Zone.LOW };
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
		float bare = ctx.computeOnClient(client -> Whiteout.lastEnd());
		world.getServer().runOnServer(server -> slot(player(server), "head/face").setItem(0, new ItemStack(Gear.SNOW_GOGGLES.get())));
		ctx.waitTicks(20);
		float goggles = ctx.computeOnClient(client -> Whiteout.lastEnd());
		ctx.takeScreenshot("summit-whiteout-goggles");
		check(bare <= 21 && goggles >= 40, "whiteout: snow goggles see further (fog ends at " + bare + " blocks, " + goggles + " with goggles)");
		world.getServer().runOnServer(server -> slot(player(server), "head/face").setItem(0, ItemStack.EMPTY));

		world.getServer().runOnServer(server -> command(server, "weather clear"));
		ctx.waitTicks(200);
		float out = ctx.computeOnClient(client -> Whiteout.amount());
		check(out <= 0.01F, "whiteout: the fog lifts when the storm ends (faded in " + out + ")");
	}

	/**
	 * Warmth: back in the snowstorm on the summit, the player dresses one piece at a time (beanie,
	 * parka, snow pants, wool socks, then climbing gloves in Trinkets Updated's glove slot) and freezes for
	 * 15 seconds in each: every piece freezes them slower, and the full set stops it. Then, in
	 * nothing but leather boots (vanilla's freeze-proof leather), being fully frozen still hurts.
	 */
	private void warmth(ClientGameTestContext ctx, TestSingleplayerContext world) {
		Config.wind = false;
		world.getServer().runOnServer(server -> command(server, "weather rain"));
		// The rain takes a moment to start.
		ctx.waitTicks(40);
		String[] dressed = { "nothing", "a beanie", "a beanie and parka", "a beanie, parka and snow pants",
				"all four clothes", "all four clothes and climbing gloves" };
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
		// Let the change of clothes count (warmth is looked at once a second), or the full set's
		// warmth would let you thaw for a moment first.
		ctx.waitTicks(30);
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
		int stillFrozen = frozen(world);
		check(health < 20.0F, "warmth: leather boots don't stop the cold hurting up high (health " + health
				+ " of 20 after 5 s fully frozen; frozen " + stillFrozen + " of 140 at the end)");

		world.getServer().runOnServer(server -> {
			dress(server.getPlayerList().getPlayers().getFirst(), 0);
			server.getPlayerList().getPlayers().getFirst().setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
			server.getPlayerList().getPlayers().getFirst().getFoodData().setFoodLevel(20);
			command(server, "weather clear");
		});
		Config.wind = true;
	}

	/** Dresses the player in the first {@code pieces} of: beanie, parka, snow pants, wool socks, climbing gloves. */
	private void dress(ServerPlayer player, int pieces) {
		EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
		Item[] clothes = { Clothes.BEANIE.get(), Clothes.PARKA.get(), Clothes.SNOW_PANTS.get(), Clothes.WOOL_SOCKS.get() };
		for (int i = 0; i < slots.length; i++) {
			player.setItemSlot(slots[i], i < pieces ? new ItemStack(clothes[i]) : ItemStack.EMPTY);
		}
		TrinketInventory gloves = slot(player, "hand/glove");
		ItemStack gloves0 = new ItemStack(Clothes.CLIMBING_GLOVES.get());
		if (pieces > 4) {
			check(gloves.slotType().validatorCheck(gloves0, gloves.getOrCreateSlotAccess(0), player), "climbing gloves: they fit Trinkets Updated's glove slot");
		}
		gloves.setItem(0, pieces > 4 ? gloves0 : ItemStack.EMPTY);
	}

	/** One of Trinkets Updated's slots on the player, by its group and name ("hand/glove"). */
	private TrinketInventory slot(ServerPlayer player, String name) {
		TrinketInventory slot = TrinketsApi.getAttachment(player).getInventories().get(name);
		if (slot != null) {
			return slot;
		}
		throw new AssertionError("Trinkets Updated gave the player no " + name + " slot: " + TrinketsApi.getAttachment(player).getInventories().keySet());
	}

	/**
	 * The climbing gear, on the summit site with the wind and cold off. An ice wall stands three
	 * blocks east of the middle, 14 high. Walking into it for three seconds climbs it with the ice
	 * axe, faster in climbing gloves, and not at all without the axe. Dropped from 18 blocks up
	 * beside it, the axe slides you down unhurt; without it the fall hurts. On packed ice a shove
	 * slides you less in crampons, and in crampons the game lets you walk on powder snow.
	 */
	private void gear(ClientGameTestContext ctx, TestSingleplayerContext world) {
		Config.wind = false;
		Config.cold = false;
		world.getServer().runOnServer(server -> {
			command(server, "weather clear");
			command(server, "fill " + (site[0] + 3) + " " + (site[1] + 1) + " " + (site[2] - 2) + " "
					+ (site[0] + 3) + " " + (site[1] + 14) + " " + (site[2] + 2) + " minecraft:packed_ice");
			// Hungry enough not to heal, so a hard landing shows.
			player(server).getFoodData().setFoodLevel(10);
		});
		double none = climb(ctx, world, false, false);
		double bare = climb(ctx, world, true, false);
		double gloves = climb(ctx, world, true, true);
		check(none < 0.6, "ice axe: without it you can't climb an ice wall (rose " + String.format("%.2f", none) + " blocks in 3 s)");
		check(bare > 1.5, "ice axe: walking into an ice wall climbs it (rose " + String.format("%.2f", bare) + " blocks in 3 s)");
		check(gloves > bare + 0.5, "ice axe: climbing gloves climb faster (rose " + String.format("%.2f", gloves) + " blocks against " + String.format("%.2f", bare) + ")");

		// Rock: the same wall in stone, on the summit and then below the treeline.
		world.getServer().runOnServer(server -> command(server, "fill " + (site[0] + 3) + " " + (site[1] + 1) + " " + (site[2] - 2) + " "
				+ (site[0] + 3) + " " + (site[1] + 14) + " " + (site[2] + 2) + " minecraft:stone"));
		double rockHigh = climb(ctx, world, true, false);
		int low = world.getServer().computeOnServer(server -> {
			int floor = player(server).level().getSeaLevel() + 20;
			command(server, "fill " + (site[0] - 1) + " " + floor + " " + (site[2] - 2) + " " + (site[0] + 3) + " " + (floor + 8) + " " + (site[2] + 2) + " minecraft:air");
			command(server, "fill " + (site[0] - 1) + " " + floor + " " + (site[2] - 2) + " " + (site[0] + 3) + " " + floor + " " + (site[2] + 2) + " minecraft:stone");
			command(server, "fill " + (site[0] + 3) + " " + (floor + 1) + " " + (site[2] - 2) + " " + (site[0] + 3) + " " + (floor + 8) + " " + (site[2] + 2) + " minecraft:stone");
			return floor;
		});
		double rockLow = climb(ctx, world, true, false, low);
		check(rockHigh > 1.5, "ice axe: it climbs rock on a summit (rose " + String.format("%.2f", rockHigh) + " blocks in 3 s)");
		check(rockLow < 0.6, "ice axe: it doesn't climb rock below the treeline (rose " + String.format("%.2f", rockLow) + " blocks in 3 s)");
		world.getServer().runOnServer(server -> command(server, "fill " + (site[0] + 3) + " " + (site[1] + 1) + " " + (site[2] - 2) + " "
				+ (site[0] + 3) + " " + (site[1] + 14) + " " + (site[2] + 2) + " minecraft:packed_ice"));

		rightClick(world);
		wornGear(ctx, world);

		float hurtWithout = drop(ctx, world, false);
		float hurtWith = drop(ctx, world, true);
		check(hurtWithout >= 4, "ice axe: the fall hurts without it (lost " + hurtWithout + " health)");
		check(hurtWith == 0, "ice axe: sliding down the wall with it, the fall doesn't hurt (lost " + hurtWith + " health)");

		double slide = slide(ctx, world, false);
		double grip = slide(ctx, world, true);
		check(grip < slide * 0.6, "crampons: no slipping on ice (slid " + String.format("%.2f", grip) + " blocks in them, " + String.format("%.2f", slide) + " without)");

		String without = powderSnow(ctx, world, false);
		String with = powderSnow(ctx, world, true);
		check(without.equals("server no, client no") && with.equals("server yes, client yes"),
				"crampons: you walk on powder snow (can walk on it in them: " + with + "; without: " + without + ")");

		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			slot(player, "hand/glove").setItem(0, ItemStack.EMPTY);
			slot(player, "feet/shoes").setItem(0, ItemStack.EMPTY);
			player.getFoodData().setFoodLevel(20);
		});
		Config.wind = true;
		Config.cold = true;
	}

	/**
	 * Pearls in the wind: an ender pearl thrown straight up over the open summit drifts downwind
	 * by the time it has been up a second; one thrown on the low ground beside it doesn't.
	 */
	private void pearls(ClientGameTestContext ctx, TestSingleplayerContext world) {
		ThrownEnderpearl[] pearls = new ThrownEnderpearl[2];
		world.getServer().runOnServer(server -> {
			ServerLevel level = player(server).level();
			command(server, "weather clear");
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, site[0] + 20, site[2] + 20);
			pearls[0] = throwUp(level, site[0] + 0.5, site[1] + 3, site[2] + 0.5);
			pearls[1] = throwUp(level, site[0] + 20.5, ground + 2, site[2] + 20.5);
		});
		ctx.waitTicks(20);
		double[] drift = world.getServer().computeOnServer(server -> {
			double angle = Wind.direction(player(server).level().getGameTime());
			double summit = (pearls[0].getX() - (site[0] + 0.5)) * Math.cos(angle) + (pearls[0].getZ() - (site[2] + 0.5)) * Math.sin(angle);
			double low = (pearls[1].getX() - (site[0] + 20.5)) * Math.cos(angle) + (pearls[1].getZ() - (site[2] + 20.5)) * Math.sin(angle);
			pearls[0].discard();
			pearls[1].discard();
			return new double[] { summit, low };
		});
		Summiteer.LOGGER.info("[test] pearl drift downwind in 1 s: summit {}, low ground {}", drift[0], drift[1]);
		check(drift[0] > 2.0, "pearls: the wind blows a pearl downwind over a summit (" + String.format("%.2f", drift[0]) + " blocks in 1 s)");
		check(Math.abs(drift[1]) < 0.1, "pearls: below the treeline a pearl flies straight (" + String.format("%.2f", drift[1]) + " blocks in 1 s)");
	}

	/**
	 * Trail cairns: a cairn on the summit site gives light 7; in clear weather the player hears no
	 * chime from it; in a snowstorm (the site is frozen peaks, so it is a whiteout) they hear it
	 * chime, every three seconds, from where it stands. A cairn on the low ground in the same storm
	 * isn't in a whiteout, so it stays quiet.
	 */
	private void cairn(ClientGameTestContext ctx, TestSingleplayerContext world) {
		Config.wind = false;
		Config.cold = false;
		BlockPos[] cairns = new BlockPos[2];
		world.getServer().runOnServer(server -> {
			ServerLevel level = player(server).level();
			command(server, "weather clear");
			cairns[0] = new BlockPos(site[0] + 4, site[1] + 1, site[2] + 4);
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, site[0] + 20, site[2] - 20);
			cairns[1] = new BlockPos(site[0] + 20, ground, site[2] - 20);
			for (BlockPos pos : cairns) {
				level.setBlock(pos, Camp.TRAIL_CAIRN_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
			}
		});
		// A look at the cairn from three blocks off, then back to the middle.
		world.getServer().runOnServer(server -> command(server, "tp @a " + (cairns[0].getX() + 0.5) + " " + cairns[0].getY() + " " + (cairns[0].getZ() - 2.5) + " 0 25"));
		ctx.waitTicks(30);
		ctx.takeScreenshot("cairn");
		standInTheMiddle(world);
		// Every chime the player's game plays, and where from; and every sound at all, since a game
		// with no sound device (CI's machines) plays none, and then the server is asked instead.
		List<Vec3> chimes = new CopyOnWriteArrayList<>();
		AtomicInteger sounds = new AtomicInteger();
		SoundEventListener listener = (sound, events, range) -> {
			sounds.incrementAndGet();
			if (sound.getIdentifier().equals(SoundEvents.NOTE_BLOCK_CHIME.value().location())) {
				chimes.add(new Vec3(sound.getX(), sound.getY(), sound.getZ()));
			}
		};
		ctx.runOnClient(client -> client.getSoundManager().addListener(listener));
		ctx.waitTicks(80);
		// Light is worked out a little after a block is placed, so it is read once things settle.
		int light = world.getServer().computeOnServer(server -> player(server).level().getBrightness(LightLayer.BLOCK, cairns[0]));
		long clear = chimes.stream().filter(at -> at.closerThan(Vec3.atCenterOf(cairns[0]), 1)).count();
		world.getServer().runOnServer(server -> command(server, "weather rain"));
		ctx.waitTicks(180);
		long storm = chimes.stream().filter(at -> at.closerThan(Vec3.atCenterOf(cairns[0]), 1)).count();
		boolean summitWhiteout = world.getServer().computeOnServer(server -> TrailCairnBlock.inWhiteout(player(server).level(), cairns[0]));
		boolean lowQuiet = world.getServer().computeOnServer(server -> {
			ServerLevel level = player(server).level();
			boolean quiet = !TrailCairnBlock.inWhiteout(level, cairns[1]);
			for (BlockPos pos : cairns) {
				level.destroyBlock(pos, false);
			}
			command(server, "weather clear");
			return quiet;
		});
		ctx.runOnClient(client -> client.getSoundManager().removeListener(listener));
		Summiteer.LOGGER.info("[test] cairn: light {}, chimes heard in clear weather {}, in the storm {}, low cairn quiet {}", light, clear, storm - clear, lowQuiet);
		check(light == 7, "cairn: it gives light 7 (" + light + ")");
		check(clear == 0, "cairn: no chime in clear weather (" + clear + " heard)");
		if (sounds.get() > 0) {
			check(storm - clear >= 2, "cairn: in a whiteout you hear it chime (" + (storm - clear) + " chimes in 9 s)");
		} else {
			check(summitWhiteout, "cairn: in a whiteout it chimes (no sound device, so asked the server: in a whiteout " + summitWhiteout + ")");
		}
		check(lowQuiet, "cairn: below the treeline it stays quiet in the same storm");
		Config.wind = true;
		Config.cold = true;
	}

	/**
	 * The tent, on the low ground 40 blocks from the site, in peaceful (so no monster keeps the
	 * player awake): placing it makes both halves; right-click sets the respawn there; at midnight
	 * the player sleeps in it and the night is skipped; sneak-right-click packs it into one tent
	 * and clears the respawn.
	 */
	private void tent(ClientGameTestContext ctx, TestSingleplayerContext world) {
		BlockPos[] at = new BlockPos[1];
		String pitched = world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			ServerLevel level = player.level();
			command(server, "difficulty peaceful");
			command(server, "weather clear");
			command(server, "time set noon");
			int x = site[0] + 40, z = site[2];
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			at[0] = new BlockPos(x, ground, z);
			command(server, "tp @a " + (x + 0.5) + " " + ground + " " + (z - 1.5) + " 0 30");
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Camp.TENTS.get(DyeColor.RED).get()));
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at[0].below()), Direction.UP, at[0].below(), false);
			player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
			BlockState foot = level.getBlockState(at[0]);
			BlockState head = level.getBlockState(at[0].south());
			return (foot.is(Camp.TENT_BLOCKS.get(DyeColor.RED).get()) ? "foot" : "no foot") + ", "
					+ (head.is(Camp.TENT_BLOCKS.get(DyeColor.RED).get()) ? "head" : "no head") + ", hand " + (player.getMainHandItem().isEmpty() ? "empty" : "full");
		});
		check(pitched.equals("foot, head, hand empty"), "tent: placing it pitches both halves (" + pitched + ")");
		// A look at the tent from the side and a little in front, then back beside it.
		world.getServer().runOnServer(server -> command(server, "tp @a " + (at[0].getX() + 4.5) + " " + at[0].getY() + " " + (at[0].getZ() - 1.5) + " 60 20"));
		ctx.waitTicks(30);
		ctx.takeScreenshot("tent");
		world.getServer().runOnServer(server -> command(server, "tp @a " + (at[0].getX() + 0.5) + " " + at[0].getY() + " " + (at[0].getZ() - 1.5) + " 0 30"));
		ctx.waitTicks(5);

		String respawn = world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			useTent(player, at[0], false);
			ServerPlayer.RespawnConfig config = player.getRespawnConfig();
			return config == null ? "none" : config.respawnData().pos().closerThan(at[0], 2) ? "at the tent" : "elsewhere " + config.respawnData().pos();
		});
		check(respawn.equals("at the tent"), "tent: right-clicking it sets your respawn there (" + respawn + ")");

		// The sky darkens on the next tick after the time changes, so wait before lying down. The
		// test world's clock is stopped, and a stopped clock doesn't skip the night, so it runs for this.
		boolean clockWasRunning = world.getServer().computeOnServer(server -> {
			boolean running = player(server).level().getGameRules().get(GameRules.ADVANCE_TIME);
			command(server, "gamerule advance_time true");
			command(server, "time set midnight");
			return running;
		});
		ctx.waitTicks(5);
		boolean slept = world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			useTent(player, at[0], false);
			return player.isSleeping();
		});
		ctx.waitTicks(130);
		boolean morning = world.getServer().computeOnServer(server -> player(server).level().isBrightOutside());
		check(slept, "tent: at night you sleep in it");
		check(morning, "tent: sleeping in it skips the night");

		String packed = world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			if (player.isSleeping()) {
				player.stopSleeping();
			}
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			useTent(player, at[0], true);
			ServerLevel level = player.level();
			boolean gone = level.getBlockState(at[0]).isAir() && level.getBlockState(at[0].south()).isAir();
			int carried = 0;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				if (player.getInventory().getItem(i).is(Camp.TENTS.get(DyeColor.RED).get())) {
					carried += player.getInventory().getItem(i).getCount();
				}
			}
			String result = (gone ? "taken down" : "still up") + ", " + carried + " carried, respawn " + (player.getRespawnConfig() == null ? "cleared" : "kept");
			player.getInventory().clearContent();
			command(server, "difficulty normal");
			command(server, "gamerule advance_time " + clockWasRunning);
			command(server, "time set noon");
			return result;
		});
		check(packed.equals("taken down, 1 carried, respawn cleared"), "tent: sneak-right-click packs it up (" + packed + ")");
	}

	/** Right-clicks the tent's head with an empty hand, sneaking or not, the way the game does. */
	private static void useTent(ServerPlayer player, BlockPos foot, boolean sneaking) {
		BlockPos head = foot.south();
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(head), Direction.UP, head, false);
		player.setShiftKeyDown(sneaking);
		player.level().getBlockState(head).useWithoutItem(player.level(), player, hit);
		player.setShiftKeyDown(false);
	}

	/** An ownerless ender pearl thrown straight up from a place (it lands without teleporting anyone). */
	private static ThrownEnderpearl throwUp(ServerLevel level, double x, double y, double z) {
		ThrownEnderpearl pearl = new ThrownEnderpearl(EntityTypes.ENDER_PEARL, level);
		pearl.setPos(x, y, z);
		pearl.setDeltaMovement(0, 1.0, 0);
		level.addFreshEntity(pearl);
		return pearl;
	}

	/** Where the rope test's anchor sits: on top of a 12-high stone cliff, facing the drop to the west. */
	private final int[] anchor = new int[3];

	/**
	 * Rope and anchor, on a stone cliff built on the ground 30 blocks east of the site (so below
	 * the treeline: rope works anywhere). Right-clicking the anchor with 64 rope hangs 13 (the
	 * anchor's level down to the ground) and leaves 51; you climb it at a ladder's pace, faster in
	 * climbing gloves; sneak-right-click reels it all back in; breaking the anchor drops the rope
	 * beside it; cutting the rope halfway drops the part below.
	 */
	private void rope(ClientGameTestContext ctx, TestSingleplayerContext world) {
		Config.wind = false;
		Config.cold = false;
		world.getServer().runOnServer(server -> {
			ServerLevel level = player(server).level();
			int x = site[0] + 30, z = site[2];
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x - 1, z) - 1;
			command(server, "fill " + (x - 4) + " " + (ground + 1) + " " + (z - 3) + " " + (x + 5) + " " + (ground + 20) + " " + (z + 3) + " minecraft:air");
			command(server, "fill " + x + " " + (ground + 1) + " " + (z - 2) + " " + (x + 5) + " " + (ground + 12) + " " + (z + 2) + " minecraft:stone");
			anchor[0] = x;
			anchor[1] = ground + 13;
			anchor[2] = z;
			placeAnchor(level);
		});
		String hung = world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Ropes.ROPE.get(), 64));
			useOnAnchor(player);
			ServerLevel level = player.level();
			BlockPos top = anchorPos().west();
			boolean knotAtBottom = level.getBlockState(top.below(12)).getValueOrElse(RopeBlock.KNOT, false) && !level.getBlockState(top).getValueOrElse(RopeBlock.KNOT, true)
				&& level.getBlockState(top).getValueOrElse(RopeBlock.TOP, false) && !level.getBlockState(top.below()).getValueOrElse(RopeBlock.TOP, true);
			return ropeBlocks(level) + " hung, " + player.getMainHandItem().getCount() + " left in hand, knot " + (knotAtBottom ? "at the bottom, tied at the top" : "misplaced");
		});
		check(hung.equals("13 hung, 51 left in hand, knot at the bottom, tied at the top"), "rope: right-clicking the anchor hangs rope to the ground (" + hung + ")");
		ropeLooks(ctx, world);

		double bare = climbRope(ctx, world, false);
		double gloves = climbRope(ctx, world, true);
		check(bare > 5.0, "rope: walking into the cliff climbs the rope at a ladder's pace (rose " + String.format("%.2f", bare) + " blocks in 3 s)");
		check(gloves > bare + 2.0, "rope: climbing gloves climb it faster (rose " + String.format("%.2f", gloves) + " blocks against " + String.format("%.2f", bare) + ")");

		// Off the rope and away from the cliff, so the player can't pick up the rope that falls.
		standInTheMiddle(world);
		String reeled = world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			slot(player, "hand/glove").setItem(0, ItemStack.EMPTY);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Ropes.ROPE.get(), 51));
			player.setShiftKeyDown(true);
			useOnAnchor(player);
			player.setShiftKeyDown(false);
			return ropeBlocks(player.level()) + " left hanging, " + ropeCarried(player) + " carried";
		});
		check(reeled.equals("0 left hanging, 64 carried"), "rope: sneak-right-click reels it all back in (" + reeled + ")");

		String broken = world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			useOnAnchor(player);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			command(server, "kill @e[type=item]");
			player.gameMode.destroyBlock(anchorPos());
			return ropeBlocks(player.level()) + " left hanging, " + ropeDropped(player.level()) + " dropped";
		});
		check(broken.equals("0 left hanging, 13 dropped"), "rope: breaking the anchor drops the whole rope by it (" + broken + ")");

		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			command(server, "kill @e[type=item]");
			placeAnchor(player.level());
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Ropes.ROPE.get(), 64));
			useOnAnchor(player);
			player.level().destroyBlock(anchorPos().west().below(6), false);
		});
		ctx.waitTicks(30);
		String cut = world.getServer().computeOnServer(server -> {
			ServerLevel level = player(server).level();
			String result = ropeBlocks(level) + " left hanging, " + ropeDropped(level) + " dropped";
			takeDownRope(server);
			return result;
		});
		check(cut.equals("6 left hanging, 6 dropped"), "rope: cutting it halfway drops the rope below the cut (" + cut + ")");

		Config.wind = true;
		Config.cold = true;
	}

	/**
	 * Screenshots for judging the rope and anchor by eye: the rope down the cliff from across the
	 * drop, the anchor from the cliff top, and a climb from behind in third person.
	 */
	private void ropeLooks(ClientGameTestContext ctx, TestSingleplayerContext world) {
		int ground = anchor[1] - 12;
		// From the ground 7 blocks west and 3 south, looking up at the rope (yaw -115: north-east).
		world.getServer().runOnServer(server -> command(server, "tp @a " + (anchor[0] - 7.5) + " " + ground + " " + (anchor[2] + 3.5) + " -115 -35"));
		ctx.waitTicks(20);
		ctx.takeScreenshot("rope-hanging");
		// On the cliff top two blocks behind the anchor, looking west and down over the edge.
		world.getServer().runOnServer(server -> command(server, "tp @a " + (anchor[0] + 2.5) + " " + anchor[1] + " " + (anchor[2] + 0.5) + " 90 50"));
		ctx.waitTicks(20);
		ctx.takeScreenshot("rope-anchor");
		// Halfway up the rope, from behind.
		ctx.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		world.getServer().runOnServer(server -> command(server, "tp @a " + (anchor[0] - 0.5) + " " + (ground + 5) + " " + (anchor[2] + 0.5) + " -90 10"));
		ctx.getInput().holdKeyFor(options -> options.keyUp, 20);
		ctx.takeScreenshot("rope-climb-third-person");
		ctx.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
	}

	private BlockPos anchorPos() {
		return new BlockPos(anchor[0], anchor[1], anchor[2]);
	}

	private void placeAnchor(ServerLevel level) {
		level.setBlock(anchorPos(), Ropes.ANCHOR_BLOCK.get().defaultBlockState().setValue(AnchorBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
	}

	/** Right-clicks the anchor's top with whatever is in the main hand, the way the game does. */
	private void useOnAnchor(ServerPlayer player) {
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(anchorPos()), Direction.UP, anchorPos(), false);
		player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
	}

	/** How many rope blocks hang in the column in front of the anchor, down to the ground. */
	private int ropeBlocks(ServerLevel level) {
		int count = 0;
		for (int dy = 0; dy <= 13; dy++) {
			if (level.getBlockState(anchorPos().west().below(dy)).is(Ropes.ROPE_BLOCK.get())) {
				count++;
			}
		}
		return count;
	}

	private static int ropeCarried(ServerPlayer player) {
		int count = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(Ropes.ROPE.get())) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/** Rope lying around the cliff as items. */
	private int ropeDropped(ServerLevel level) {
		AABB around = new AABB(anchorPos()).inflate(4, 16, 4);
		int count = 0;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, around, entity -> entity.getItem().is(Ropes.ROPE.get()))) {
			count += item.getItem().getCount();
		}
		return count;
	}

	private void takeDownRope(net.minecraft.server.MinecraftServer server) {
		ServerLevel level = player(server).level();
		Ropes.takeDown(level, anchorPos(), level.getBlockState(anchorPos()));
		command(server, "kill @e[type=item]");
	}

	/** Stands at the foot of the rope and walks east into the cliff for three seconds; how far the player rose. */
	private double climbRope(ClientGameTestContext ctx, TestSingleplayerContext world, boolean gloves) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			slot(player, "hand/glove").setItem(0, gloves ? new ItemStack(Clothes.CLIMBING_GLOVES.get()) : ItemStack.EMPTY);
			command(server, "tp @a " + (anchor[0] - 0.5) + " " + (anchor[1] - 12) + " " + (anchor[2] + 0.5) + " -90 0");
			player.setDeltaMovement(0, 0, 0);
		});
		// A full second: the game ignores the player's own movement until the teleport is confirmed, and the
		// climb straight after the last one (still on the rope, higher up) sometimes took longer than half that.
		ctx.waitTicks(20);
		ctx.getInput().holdKeyFor(options -> options.keyUp, 60);
		double rose = world.getServer().computeOnServer(server -> player(server).getY() - (anchor[1] - 12));
		if (gloves) {
			ctx.takeScreenshot("rope-climb");
		}
		Summiteer.LOGGER.info("[test] rope climb with gloves {}: rose {}", gloves, rose);
		return rose;
	}

	/** Walks east into the ice wall for three seconds; how many blocks the player rose. */
	private double climb(ClientGameTestContext ctx, TestSingleplayerContext world, boolean axe, boolean gloves) {
		return climb(ctx, world, axe, gloves, site[1]);
	}

	/** Walks east into the wall three blocks east of the middle, standing on {@code floor}. */
	private double climb(ClientGameTestContext ctx, TestSingleplayerContext world, boolean axe, boolean gloves, int floor) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, axe ? new ItemStack(Gear.ICE_AXE.get()) : ItemStack.EMPTY);
			slot(player, "hand/glove").setItem(0, gloves ? new ItemStack(Clothes.CLIMBING_GLOVES.get()) : ItemStack.EMPTY);
			// Facing east (yaw -90), towards the wall.
			command(server, "tp @a " + (site[0] + 0.5) + " " + (floor + 1) + " " + (site[2] + 0.5) + " -90 0");
			player.setDeltaMovement(0, 0, 0);
		});
		ctx.waitTicks(10);
		ctx.getInput().holdKeyFor(options -> options.keyUp, 60);
		double rose = world.getServer().computeOnServer(server -> player(server).getY() - (floor + 1));
		if (axe && gloves) {
			ctx.takeScreenshot("ice-axe-climb");
		}
		Summiteer.LOGGER.info("[test] climb with axe {} and gloves {}: rose {}", axe, gloves, rose);
		return rose;
	}

	/**
	 * Right-click: crampons in hand go on in the empty feet slot and leave the hand empty; a second
	 * pair of climbing gloves (named "new") swaps with the pair already worn, which ends up in hand.
	 */
	private void rightClick(TestSingleplayerContext world) {
		String result = world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			slot(player, "feet/shoes").setItem(0, ItemStack.EMPTY);
			slot(player, "hand/glove").setItem(0, new ItemStack(Clothes.CLIMBING_GLOVES.get()));
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Gear.CRAMPONS.get()));
			player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
			boolean cramponsOn = slot(player, "feet/shoes").getItem(0).is(Gear.CRAMPONS.get()) && player.getMainHandItem().isEmpty();
			ItemStack fresh = new ItemStack(Clothes.CLIMBING_GLOVES.get());
			fresh.set(DataComponents.CUSTOM_NAME, Component.literal("new"));
			player.setItemInHand(InteractionHand.MAIN_HAND, fresh);
			player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
			ItemStack worn = slot(player, "hand/glove").getItem(0);
			ItemStack hand = player.getMainHandItem();
			boolean swapped = worn.has(DataComponents.CUSTOM_NAME) && hand.is(Clothes.CLIMBING_GLOVES.get()) && !hand.has(DataComponents.CUSTOM_NAME);
			slot(player, "feet/shoes").setItem(0, ItemStack.EMPTY);
			slot(player, "hand/glove").setItem(0, ItemStack.EMPTY);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			return "crampons " + (cramponsOn ? "on" : "not on") + ", gloves " + (swapped ? "swapped" : "not swapped");
		});
		check(result.equals("crampons on, gloves swapped"), "right-click: gear goes on, swapping with what you wore (" + result + ")");
	}

	/**
	 * Worn gear on the player, for judging the look: the camera turns to face the player, who wears
	 * the goggles, crampons and gloves, first bare-headed and then with the beanie and wool socks
	 * (the gear should show over them).
	 */
	private void wornGear(ClientGameTestContext ctx, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Gear.ICE_AXE.get()));
			slot(player, "head/face").setItem(0, new ItemStack(Gear.SNOW_GOGGLES.get()));
			slot(player, "feet/shoes").setItem(0, new ItemStack(Gear.CRAMPONS.get()));
			slot(player, "hand/glove").setItem(0, new ItemStack(Clothes.CLIMBING_GLOVES.get()));
			command(server, "tp @a " + (site[0] - 5.5) + " " + (site[1] + 1) + " " + (site[2] + 0.5) + " 0 0");
		});
		// Facing the player, close up, with the hotbar and chat hidden.
		int fov = ctx.computeOnClient(client -> client.options.fov().get());
		ctx.runOnClient(client -> {
			client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			if (!client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
			client.options.fov().set(45);
		});
		ctx.waitTicks(20);
		ctx.takeScreenshot("worn-gear");
		ctx.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.waitTicks(10);
		ctx.takeScreenshot("worn-gear-back");
		ctx.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		world.getServer().runOnServer(server -> {
			player(server).setItemSlot(EquipmentSlot.HEAD, new ItemStack(Clothes.BEANIE.get()));
			player(server).setItemSlot(EquipmentSlot.FEET, new ItemStack(Clothes.WOOL_SOCKS.get()));
		});
		ctx.waitTicks(10);
		ctx.takeScreenshot("worn-gear-over-clothes");
		ctx.runOnClient(client -> {
			client.options.setCameraType(CameraType.FIRST_PERSON);
			if (client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
			client.options.fov().set(fov);
		});
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			slot(player, "head/face").setItem(0, ItemStack.EMPTY);
			slot(player, "feet/shoes").setItem(0, ItemStack.EMPTY);
			slot(player, "hand/glove").setItem(0, ItemStack.EMPTY);
		});
	}

	/** Drops the player from 18 blocks up, against the ice wall; how much health the landing cost. */
	private float drop(ClientGameTestContext ctx, TestSingleplayerContext world, boolean axe) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, axe ? new ItemStack(Gear.ICE_AXE.get()) : ItemStack.EMPTY);
			player.setHealth(player.getMaxHealth());
			// Just touching the wall: the player is 0.6 wide, so their east side is at x + 2.99.
			command(server, "tp @a " + (site[0] + 2.69) + " " + (site[1] + 18) + " " + (site[2] + 0.5) + " -90 0");
			player.setDeltaMovement(0, 0, 0);
		});
		ctx.waitTicks(120);
		float lost = world.getServer().computeOnServer(server -> player(server).getMaxHealth() - player(server).getHealth());
		Summiteer.LOGGER.info("[test] drop with axe {}: lost {} health", axe, lost);
		return lost;
	}

	/** Shoves the player west across packed ice; how far they slid. */
	private double slide(ClientGameTestContext ctx, TestSingleplayerContext world, boolean crampons) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			slot(player, "feet/shoes").setItem(0, crampons ? new ItemStack(Gear.CRAMPONS.get()) : ItemStack.EMPTY);
			command(server, "fill " + (site[0] - 9) + " " + site[1] + " " + (site[2] - 1) + " " + (site[0] - 1) + " " + site[1] + " " + (site[2] + 1) + " minecraft:packed_ice");
			command(server, "tp @a " + (site[0] - 0.5) + " " + (site[1] + 1) + " " + (site[2] + 0.5) + " 90 0");
		});
		ctx.waitTicks(10);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setDeltaMovement(-0.5, 0, 0);
			// Players move themselves, so the client must be told about the shove.
			player.syncVelocity = true;
		});
		ctx.waitTicks(40);
		double slid = world.getServer().computeOnServer(server -> (site[0] - 0.5) - player(server).getX());
		Summiteer.LOGGER.info("[test] shoved on ice with crampons {}: slid {}", crampons, slid);
		return slid;
	}

	/**
	 * Whether the game lets the player walk on powder snow, asked the way vanilla asks it, on the
	 * server and on the client (players move on the client, so both must agree). The crampons go on
	 * or off a second first, so the change reaches the client.
	 */
	private String powderSnow(ClientGameTestContext ctx, TestSingleplayerContext world, boolean crampons) {
		world.getServer().runOnServer(server ->
				slot(player(server), "feet/shoes").setItem(0, crampons ? new ItemStack(Gear.CRAMPONS.get()) : ItemStack.EMPTY));
		ctx.waitTicks(20);
		boolean server = world.getServer().computeOnServer(s -> PowderSnowBlock.canEntityWalkOnPowderSnow(player(s)));
		boolean client = ctx.computeOnClient(c -> PowderSnowBlock.canEntityWalkOnPowderSnow(c.player));
		String result = "server " + (server ? "yes" : "no") + ", client " + (client ? "yes" : "no");
		Summiteer.LOGGER.info("[test] can walk on powder snow with crampons {}: {}", crampons, result);
		return result;
	}

	private static ServerPlayer player(net.minecraft.server.MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
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
