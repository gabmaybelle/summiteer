package com.gabbymaybelle.summiteer;

import static com.gabbymaybelle.summiteer.ClientTest.check;
import static com.gabbymaybelle.summiteer.ClientTest.createWorld;
import static com.gabbymaybelle.summiteer.ClientTest.run;
import static com.gabbymaybelle.summiteer.ClientTest.waitTicks;

import com.gabbymaybelle.summiteer.client.Whiteout;
import com.gabbymaybelle.summiteer.zones.Zone;
import com.gabbymaybelle.summiteer.zones.Zones;
import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

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
