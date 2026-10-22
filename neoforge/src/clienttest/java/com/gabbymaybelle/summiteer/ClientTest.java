package com.gabbymaybelle.summiteer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A small step-by-step in-game test for the NeoForge jar, since NeoForge has no client game test
 * framework like Fabric's. It only runs in the `clientTest` run (`./gradlew :neoforge:runClientTest`),
 * which sets the system property below; this source set is never in the jar players get.
 *
 * <p>Each step runs on the game's own thread, one or more ticks at a time. A failed check is noted
 * and the run carries on; at the end, if any failed, it throws with every failed check listed,
 * which crashes the game with a crash report and a non-zero exit code, so Gradle (and CI) fail.
 * When every check has passed, the game closes normally. A watchdog ends a run that hangs.
 */
@EventBusSubscriber(modid = Summiteer.MOD_ID, value = Dist.CLIENT)
final class ClientTest {
	static final boolean ENABLED = Boolean.getBoolean(Summiteer.MOD_ID + ".clienttest");
	/** A run that takes longer than this has hung (the game thread may be stuck, so a separate thread checks). */
	private static final long WATCHDOG_MILLIS = 10 * 60 * 1000;

	/** One step: returns true once it's done, and is called again next tick until then. */
	interface Step {
		boolean tick(Minecraft mc);
	}

	private static final Deque<Step> STEPS = new ArrayDeque<>();
	private static boolean started;
	private static boolean finished;
	/** Failed checks so far; checks run on both the game and the server thread. */
	private static final List<String> FAILURES = new ArrayList<>();

	private ClientTest() {
	}

	/** Runs once. */
	static void run(Consumer<Minecraft> action) {
		STEPS.add(mc -> {
			action.accept(mc);
			return true;
		});
	}

	/** Waits this many ticks. */
	static void waitTicks(int ticks) {
		int[] left = { ticks };
		STEPS.add(mc -> --left[0] <= 0);
	}

	/** Waits until the condition holds, failing after this many ticks. */
	static void waitUntil(String what, int timeoutTicks, Predicate<Minecraft> condition) {
		int[] waited = { 0 };
		STEPS.add(mc -> {
			if (condition.test(mc)) {
				return true;
			}
			if (++waited[0] > timeoutTicks) {
				throw new AssertionError("Timed out after " + timeoutTicks + " ticks waiting for: " + what);
			}
			return false;
		});
	}

	/** Runs a command on the singleplayer server as an operator, then gives it a few ticks to reach the client. */
	static void command(String command) {
		run(mc -> {
			IntegratedServer server = mc.getSingleplayerServer();
			if (server == null) {
				throw new AssertionError("No singleplayer server for the command: " + command);
			}
			server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
		});
		waitTicks(4);
	}

	/** Saves a screenshot in the run's screenshots folder (named by the time it was taken). */
	static void screenshot() {
		run(mc -> Screenshot.grab(mc, false));
		waitTicks(2);
	}

	/**
	 * Records a failed check and carries on, so one run reports every check that fails (the Fabric
	 * game test does the same). The run fails at the end with the whole list. Setup problems (a
	 * timeout, no server) still stop it at once: nothing after them could be trusted.
	 */
	static void check(boolean condition, String message) {
		if (condition) {
			return;
		}
		Summiteer.LOGGER.error("[test] FAILED: {}", message);
		synchronized (FAILURES) {
			FAILURES.add(message);
		}
	}

	/**
	 * Creates a fresh flat creative world with commands on, and waits until the player is in it. The
	 * folder gets the time in its name, so a run never reopens a world an earlier run left behind
	 * (in the same game folder, perhaps on another Minecraft version).
	 */
	static void createWorld(String baseName) {
		String name = baseName + "-" + System.currentTimeMillis();
		waitUntil("the title screen", 2400, mc -> mc.gui.overlay() == null && mc.gui.screen() != null);
		// The game runs in the background and loses focus when it is sent behind other windows;
		// a singleplayer game that pauses then stops the whole world (Fabric's test does the same).
		run(mc -> mc.options.pauseOnLostFocus = false);
		run(mc -> mc.createWorldOpenFlows().createFreshLevel(name,
				new LevelSettings(name, GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true,
						WorldDataConfiguration.DEFAULT),
				new WorldOptions(1L, false, false),
				registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
				mc.gui.screen()));
		waitUntil("the player in the new world", 2400, mc -> mc.player != null && mc.level != null && mc.gui.screen() == null);
		// Time for the chunks around the player to arrive and draw.
		waitTicks(60);
	}

	@SubscribeEvent
	static void onTick(ClientTickEvent.Post event) {
		if (!ENABLED || finished) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (!started) {
			started = true;
			Summiteer.LOGGER.info("Summiteer: client test starting");
			Thread watchdog = new Thread(() -> {
				try {
					Thread.sleep(WATCHDOG_MILLIS);
				} catch (InterruptedException e) {
					return;
				}
				Summiteer.LOGGER.error("Summiteer: client test hung for {} minutes; stopping", WATCHDOG_MILLIS / 60000);
				Runtime.getRuntime().halt(3);
			}, "Summiteer client test watchdog");
			watchdog.setDaemon(true);
			watchdog.start();
			SummiteerClientTest.steps();
		}
		Step step = STEPS.peek();
		if (step == null) {
			finished = true;
			synchronized (FAILURES) {
				if (!FAILURES.isEmpty()) {
					throw new AssertionError(FAILURES.size() + " check(s) failed:\n - " + String.join("\n - ", FAILURES));
				}
			}
			Summiteer.LOGGER.info("Summiteer: client test passed, every check");
			mc.stop();
			return;
		}
		if (step.tick(mc)) {
			STEPS.poll();
		}
	}
}
