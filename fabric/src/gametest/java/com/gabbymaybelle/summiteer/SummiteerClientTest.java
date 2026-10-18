package com.gabbymaybelle.summiteer;

import com.gabbymaybelle.summiteer.zones.Zone;
import com.gabbymaybelle.summiteer.zones.Zones;
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

	private void check(boolean ok, String what) {
		if (ok) {
			Summiteer.LOGGER.info("[test] ok: {}", what);
		} else {
			Summiteer.LOGGER.error("[test] FAILED: {}", what);
			failures.add(what);
		}
	}
}
