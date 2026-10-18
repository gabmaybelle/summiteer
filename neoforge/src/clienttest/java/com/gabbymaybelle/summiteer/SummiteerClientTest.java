package com.gabbymaybelle.summiteer;

import static com.gabbymaybelle.summiteer.ClientTest.check;
import static com.gabbymaybelle.summiteer.ClientTest.createWorld;
import static com.gabbymaybelle.summiteer.ClientTest.run;

import com.gabbymaybelle.summiteer.zones.Zone;
import com.gabbymaybelle.summiteer.zones.Zones;
import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * The NeoForge jar's in-game test: the same checks as the Fabric game test (fabric/src/gametest),
 * so both loaders are held to the same behaviour.
 */
final class SummiteerClientTest {
	private static final int[] BOX = new int[2];

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
