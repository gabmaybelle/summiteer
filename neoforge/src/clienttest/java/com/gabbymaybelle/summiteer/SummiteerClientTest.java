package com.gabbymaybelle.summiteer;

import static com.gabbymaybelle.summiteer.ClientTest.check;
import static com.gabbymaybelle.summiteer.ClientTest.createWorld;
import static com.gabbymaybelle.summiteer.ClientTest.run;
import static com.gabbymaybelle.summiteer.ClientTest.waitTicks;
import static com.gabbymaybelle.summiteer.ClientTest.waitUntil;

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
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.CameraType;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.advancements.AdvancementHolder;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
		// A flat grass world comes with sheep, cows and pigs, and one wandering into a climb shoves the
		// player off the rope (seen on CI): no animals, and none spawning.
		run(mc -> onServer(mc, server -> {
			serverCommand(server, "gamerule spawn_mobs false");
			serverCommand(server, "kill @e[type=!minecraft:player]");
		}));

		// Zones: a column each of stony peaks, snowy slopes and plains beside the player, then each
		// height asked. Peaks are high at any height; slopes only above the treeline.
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
			command(mc, "fillbiome " + (x - 2) + " " + sea + " " + (z + 8) + " " + (x + 2) + " " + top + " " + (z + 12) + " minecraft:snowy_slopes");
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
				BlockPos slopes = new BlockPos(BOX[0], 0, BOX[1] + 10);
				expect(Zone.LOW, Zones.at(level, slopes.atY(high - 2)), "snowy slopes below the treeline");
				expect(Zone.HIGH, Zones.at(level, slopes.atY(high + 2)), "snowy slopes above the treeline");
				expect(Zone.HIGH, Zones.at(level, peaks.atY(sea + 20)), "stony peaks far below the treeline");
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
		rope();
		pearls();
		cairn();
		tent();
		advancements();
	}

	private static final BlockPos[] CAIRNS = new BlockPos[2];
	private static final List<Vec3> CHIMES = new CopyOnWriteArrayList<>();
	/** Every sound at all: a game with no sound device (CI's machines) plays none, and then the server is asked instead. */
	private static final AtomicInteger SOUNDS = new AtomicInteger();
	private static final SoundEventListener CHIME_LISTENER = (sound, events, range) -> {
		SOUNDS.incrementAndGet();
		if (sound.getIdentifier().equals(SoundEvents.NOTE_BLOCK_CHIME.value().location())) {
			CHIMES.add(new Vec3(sound.getX(), sound.getY(), sound.getZ()));
		}
	};
	private static final long[] CAIRN_RESULT = new long[5];

	/**
	 * Trail cairns: a cairn on the summit site gives light 7; in clear weather the player hears no
	 * chime from it; in a snowstorm (the site is frozen peaks, so it is a whiteout) they hear it
	 * chime, every three seconds, from where it stands. A cairn on the low ground in the same storm
	 * isn't in a whiteout, so it stays quiet.
	 */
	private static void cairn() {
		run(mc -> onServer(mc, server -> {
			Config.wind = false;
			Config.cold = false;
			ServerLevel level = player(server).level();
			serverCommand(server, "weather clear");
			CAIRNS[0] = new BlockPos(SITE_AT[0] + 4, SITE_AT[1] + 1, SITE_AT[2] + 4);
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, SITE_AT[0] + 20, SITE_AT[2] - 20);
			CAIRNS[1] = new BlockPos(SITE_AT[0] + 20, ground, SITE_AT[2] - 20);
			for (BlockPos pos : CAIRNS) {
				level.setBlock(pos, Camp.TRAIL_CAIRN_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
			}
			// One more placed the way a player does, by right-click ("Cairn by cairn"), then taken away.
			ServerPlayer player = player(server);
			BlockPos under = new BlockPos(SITE_AT[0] - 4, SITE_AT[1], SITE_AT[2] - 4);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Camp.TRAIL_CAIRN.get()));
			player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(under), Direction.UP, under, false)));
			level.destroyBlock(under.above(), false);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}));
		standInTheMiddle();
		run(mc -> mc.getSoundManager().addListener(CHIME_LISTENER));
		waitTicks(80);
		run(mc -> onServer(mc, server -> {
			// Light is worked out a little after a block is placed, so it is read once things settle.
			CAIRN_RESULT[0] = player(server).level().getBrightness(LightLayer.BLOCK, CAIRNS[0]);
			CAIRN_RESULT[1] = chimesFrom(CAIRNS[0]);
			serverCommand(server, "weather rain");
		}));
		waitTicks(180);
		run(mc -> onServer(mc, server -> {
			ServerLevel level = player(server).level();
			CAIRN_RESULT[2] = chimesFrom(CAIRNS[0]) - CAIRN_RESULT[1];
			CAIRN_RESULT[3] = TrailCairnBlock.inWhiteout(level, CAIRNS[1]) ? 0 : 1;
			CAIRN_RESULT[4] = TrailCairnBlock.inWhiteout(level, CAIRNS[0]) ? 1 : 0;
			for (BlockPos pos : CAIRNS) {
				level.destroyBlock(pos, false);
			}
			serverCommand(server, "weather clear");
			Config.wind = true;
			Config.cold = true;
		}));
		run(mc -> {
			mc.getSoundManager().removeListener(CHIME_LISTENER);
			Summiteer.LOGGER.info("[test] cairn: light {}, chimes heard in clear weather {}, in the storm {}, low cairn quiet {}",
					CAIRN_RESULT[0], CAIRN_RESULT[1], CAIRN_RESULT[2], CAIRN_RESULT[3] == 1);
			check(CAIRN_RESULT[0] == 7, "Cairn: it doesn't give light 7 (" + CAIRN_RESULT[0] + ")");
			check(CAIRN_RESULT[1] == 0, "Cairn: it chimed in clear weather (" + CAIRN_RESULT[1] + " heard)");
			if (SOUNDS.get() > 0) {
				check(CAIRN_RESULT[2] >= 2, "Cairn: in a whiteout you don't hear it chime (" + CAIRN_RESULT[2] + " chimes in 9 s)");
			} else {
				check(CAIRN_RESULT[4] == 1, "Cairn: in a whiteout it wouldn't chime (no sound device, so asked the server: not in a whiteout)");
			}
			check(CAIRN_RESULT[3] == 1, "Cairn: below the treeline it counts as a whiteout");
		});
	}

	private static long chimesFrom(BlockPos cairn) {
		return CHIMES.stream().filter(at -> at.closerThan(Vec3.atCenterOf(cairn), 1)).count();
	}

	private static final BlockPos[] TENT_AT = new BlockPos[1];
	private static final String[] TENT_RESULT = new String[4];
	private static final boolean[] CLOCK_WAS_RUNNING = new boolean[1];

	/**
	 * The tent, on the summit site (so sleeping in it is "Base camp": above the treeline), in
	 * peaceful (so no monster keeps the player awake), with the wind and cold off: placing it makes
	 * both halves; right-click sets the respawn there; at midnight the player sleeps in it and the
	 * night is skipped; sneak-right-click packs it into one tent and clears the respawn.
	 */
	private static void tent() {
		run(mc -> onServer(mc, server -> {
			ServerPlayer player = player(server);
			ServerLevel level = player.level();
			Config.wind = false;
			Config.cold = false;
			serverCommand(server, "kill @e[type=!minecraft:player]");
			serverCommand(server, "difficulty peaceful");
			serverCommand(server, "weather clear");
			serverCommand(server, "time set noon");
			int x = SITE_AT[0] - 6, z = SITE_AT[2] - 6;
			int ground = SITE_AT[1] + 1;
			TENT_AT[0] = new BlockPos(x, ground, z);
			serverCommand(server, "tp @a " + (x + 0.5) + " " + ground + " " + (z - 1.5) + " 0 30");
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Camp.TENTS.get(DyeColor.RED).get()));
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(TENT_AT[0].below()), Direction.UP, TENT_AT[0].below(), false);
			player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
			Block red = Camp.TENT_BLOCKS.get(DyeColor.RED).get();
			TENT_RESULT[0] = (level.getBlockState(TENT_AT[0]).is(red) ? "foot" : "no foot") + ", "
					+ (level.getBlockState(TENT_AT[0].south()).is(red) ? "head" : "no head") + ", hand " + (player.getMainHandItem().isEmpty() ? "empty" : "full");
			useTent(player, TENT_AT[0], false);
			ServerPlayer.RespawnConfig config = player.getRespawnConfig();
			TENT_RESULT[1] = config == null ? "none" : config.respawnData().pos().closerThan(TENT_AT[0], 2) ? "at the tent" : "elsewhere " + config.respawnData().pos();
			// The test world's clock is stopped, and a stopped clock doesn't skip the night, so it runs for this.
			CLOCK_WAS_RUNNING[0] = level.getGameRules().get(GameRules.ADVANCE_TIME);
			serverCommand(server, "gamerule advance_time true");
			serverCommand(server, "time set midnight");
		}));
		// The sky darkens on the next tick after the time changes, so wait before lying down.
		waitTicks(5);
		run(mc -> onServer(mc, server -> {
			ServerPlayer player = player(server);
			useTent(player, TENT_AT[0], false);
			TENT_RESULT[2] = player.isSleeping() ? "slept" : "awake";
		}));
		waitTicks(130);
		run(mc -> onServer(mc, server -> {
			ServerPlayer player = player(server);
			ServerLevel level = player.level();
			TENT_RESULT[2] += level.isBrightOutside() ? ", morning" : ", still night";
			if (player.isSleeping()) {
				player.stopSleeping();
			}
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			useTent(player, TENT_AT[0], true);
			boolean gone = level.getBlockState(TENT_AT[0]).isAir() && level.getBlockState(TENT_AT[0].south()).isAir();
			int carried = 0;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				if (player.getInventory().getItem(i).is(Camp.TENTS.get(DyeColor.RED).get())) {
					carried += player.getInventory().getItem(i).getCount();
				}
			}
			TENT_RESULT[3] = (gone ? "taken down" : "still up") + ", " + carried + " carried, respawn " + (player.getRespawnConfig() == null ? "cleared" : "kept");
			player.getInventory().clearContent();
			serverCommand(server, "difficulty normal");
			serverCommand(server, "gamerule advance_time " + CLOCK_WAS_RUNNING[0]);
			serverCommand(server, "time set noon");
			Config.wind = true;
			Config.cold = true;
		}));
		run(mc -> {
			check(TENT_RESULT[0].equals("foot, head, hand empty"), "Tent: placing it didn't pitch both halves (" + TENT_RESULT[0] + ")");
			check(TENT_RESULT[1].equals("at the tent"), "Tent: right-clicking it didn't set your respawn there (" + TENT_RESULT[1] + ")");
			check(TENT_RESULT[2].equals("slept, morning"), "Tent: you didn't sleep the night away in it (" + TENT_RESULT[2] + ")");
			check(TENT_RESULT[3].equals("taken down, 1 carried, respawn cleared"), "Tent: sneak-right-click didn't pack it up (" + TENT_RESULT[3] + ")");
		});
	}

	private static final String[] MISSING_ADVANCEMENTS = new String[1];

	/**
	 * The advancements tab, checked at the end, when the test has done everything each one waits
	 * for: been up high and on a summit in a snowstorm, worn the full outfit, climbed with the ice
	 * axe, hung a rope, placed a cairn by hand, slept in the tent on the summit, thrown a pearl the
	 * wind blew.
	 */
	private static void advancements() {
		String[] names = { "root", "summit", "into_the_white", "dressed_for_it", "ice_climber", "the_way_down", "cairn_by_cairn", "base_camp", "gone_with_the_wind" };
		run(mc -> onServer(mc, server -> {
			ServerPlayer player = player(server);
			StringBuilder out = new StringBuilder();
			for (String name : names) {
				AdvancementHolder advancement = server.getAdvancements().get(Summiteer.id("mountain/" + name));
				if (advancement == null) {
					out.append(name).append(" (not loaded) ");
				} else if (!player.getAdvancements().getOrStartProgress(advancement).isDone()) {
					out.append(name).append(' ');
				}
			}
			MISSING_ADVANCEMENTS[0] = out.toString().trim();
		}));
		run(mc -> {
			String missing = MISSING_ADVANCEMENTS[0].isEmpty() ? "none" : MISSING_ADVANCEMENTS[0];
			Summiteer.LOGGER.info("[test] advancements not earned: {}", missing);
			check(MISSING_ADVANCEMENTS[0].isEmpty(), "Advancements: not all nine earned by the end (missing: " + missing + ")");
		});
	}

	/** Right-clicks the tent's head with an empty hand, sneaking or not, the way the game does. */
	private static void useTent(ServerPlayer player, BlockPos foot, boolean sneaking) {
		BlockPos head = foot.south();
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(head), Direction.UP, head, false);
		player.setShiftKeyDown(sneaking);
		player.level().getBlockState(head).useWithoutItem(player.level(), player, hit);
		player.setShiftKeyDown(false);
	}

	private static final ThrownEnderpearl[] PEARLS = new ThrownEnderpearl[2];
	private static final double[] PEARL_DRIFT = new double[2];

	/**
	 * Pearls in the wind: an ender pearl thrown straight up over the open summit drifts downwind
	 * by the time it has been up a second; one thrown on the low ground beside it doesn't.
	 */
	private static void pearls() {
		run(mc -> onServer(mc, server -> {
			ServerLevel level = player(server).level();
			serverCommand(server, "weather clear");
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, SITE_AT[0] + 20, SITE_AT[2] + 20);
			PEARLS[0] = throwUp(level, SITE_AT[0] + 0.5, SITE_AT[1] + 3, SITE_AT[2] + 0.5);
			// Thrown by the player, so "Gone with the wind" is theirs (it is gone before it lands).
			PEARLS[0].setOwner(player(server));
			PEARLS[1] = throwUp(level, SITE_AT[0] + 20.5, ground + 2, SITE_AT[2] + 20.5);
		}));
		waitTicks(20);
		run(mc -> onServer(mc, server -> {
			double angle = Wind.direction(player(server).level().getGameTime());
			PEARL_DRIFT[0] = (PEARLS[0].getX() - (SITE_AT[0] + 0.5)) * Math.cos(angle) + (PEARLS[0].getZ() - (SITE_AT[2] + 0.5)) * Math.sin(angle);
			PEARL_DRIFT[1] = (PEARLS[1].getX() - (SITE_AT[0] + 20.5)) * Math.cos(angle) + (PEARLS[1].getZ() - (SITE_AT[2] + 20.5)) * Math.sin(angle);
			PEARLS[0].discard();
			PEARLS[1].discard();
			Summiteer.LOGGER.info("[test] pearl drift downwind in 1 s: summit {}, low ground {}", PEARL_DRIFT[0], PEARL_DRIFT[1]);
		}));
		run(mc -> {
			check(PEARL_DRIFT[0] > 2.0, "Pearls: the wind didn't blow a pearl downwind over a summit (" + String.format("%.2f", PEARL_DRIFT[0]) + " blocks in 1 s)");
			check(Math.abs(PEARL_DRIFT[1]) < 0.1, "Pearls: below the treeline a pearl still drifted (" + String.format("%.2f", PEARL_DRIFT[1]) + " blocks in 1 s)");
		});
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
	private static final int[] ANCHOR = new int[3];
	private static final String[] ROPE_RESULT = new String[4];
	private static final double[] ROPE_CLIMB = new double[3];

	/**
	 * Rope and anchor, on a stone cliff built on the ground 30 blocks east of the site (so below
	 * the treeline: rope works anywhere). Right-clicking the anchor with 64 rope hangs 13 (the
	 * anchor's level down to the ground) and leaves 51; you climb it at a ladder's pace, faster in
	 * climbing gloves; sneak-right-click reels it all back in; breaking the anchor drops the rope
	 * beside it; cutting the rope halfway drops the part below.
	 */
	private static void rope() {
		run(mc -> onServer(mc, server -> {
			serverCommand(server, "kill @e[type=!minecraft:player]");
			Config.wind = false;
			Config.cold = false;
			ServerLevel level = player(server).level();
			int x = SITE_AT[0] + 30, z = SITE_AT[2];
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x - 1, z) - 1;
			serverCommand(server, "fill " + (x - 4) + " " + (ground + 1) + " " + (z - 3) + " " + (x + 5) + " " + (ground + 20) + " " + (z + 3) + " minecraft:air");
			serverCommand(server, "fill " + x + " " + (ground + 1) + " " + (z - 2) + " " + (x + 5) + " " + (ground + 12) + " " + (z + 2) + " minecraft:stone");
			ANCHOR[0] = x;
			ANCHOR[1] = ground + 13;
			ANCHOR[2] = z;
			placeAnchor(level);
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Ropes.ROPE.get(), 64));
			useOnAnchor(player);
			BlockPos top = anchorPos().west();
			boolean knotAtBottom = level.getBlockState(top.below(12)).getValueOrElse(RopeBlock.KNOT, false) && !level.getBlockState(top).getValueOrElse(RopeBlock.KNOT, true)
				&& level.getBlockState(top).getValueOrElse(RopeBlock.TOP, false) && !level.getBlockState(top.below()).getValueOrElse(RopeBlock.TOP, true);
			ROPE_RESULT[0] = ropeBlocks(level) + " hung, " + player.getMainHandItem().getCount() + " left in hand, knot " + (knotAtBottom ? "at the bottom, tied at the top" : "misplaced");
		}));
		run(mc -> check(ROPE_RESULT[0].equals("13 hung, 51 left in hand, knot at the bottom, tied at the top"), "Rope: right-clicking the anchor didn't hang rope to the ground (" + ROPE_RESULT[0] + ")"));

		climbRope(0, false);
		climbRope(1, true);
		run(mc -> {
			check(ROPE_CLIMB[0] > 5.0, "Rope: walking into the cliff didn't climb the rope at a ladder's pace (rose " + String.format("%.2f", ROPE_CLIMB[0]) + " blocks in 3 s)");
			check(ROPE_CLIMB[1] > ROPE_CLIMB[0] + 2.0, "Rope: climbing gloves didn't climb it faster (rose " + String.format("%.2f", ROPE_CLIMB[1])
					+ " blocks against " + String.format("%.2f", ROPE_CLIMB[0]) + ")");
		});

		// Off the rope and away from the cliff, so the player can't pick up the rope that falls.
		standInTheMiddle();
		run(mc -> onServer(mc, server -> {
			ServerPlayer player = player(server);
			equip(player, "hands", ItemStack.EMPTY);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Ropes.ROPE.get(), 51));
			player.setShiftKeyDown(true);
			useOnAnchor(player);
			player.setShiftKeyDown(false);
			ROPE_RESULT[1] = ropeBlocks(player.level()) + " left hanging, " + ropeCarried(player) + " carried";

			useOnAnchor(player);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			serverCommand(server, "kill @e[type=item]");
			player.gameMode.destroyBlock(anchorPos());
			ROPE_RESULT[2] = ropeBlocks(player.level()) + " left hanging, " + ropeDropped(player.level()) + " dropped";

			serverCommand(server, "kill @e[type=item]");
			placeAnchor(player.level());
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Ropes.ROPE.get(), 64));
			useOnAnchor(player);
			player.level().destroyBlock(anchorPos().west().below(6), false);
		}));
		waitTicks(30);
		run(mc -> onServer(mc, server -> {
			ServerLevel level = player(server).level();
			ROPE_RESULT[3] = ropeBlocks(level) + " left hanging, " + ropeDropped(level) + " dropped";
			Ropes.takeDown(level, anchorPos(), level.getBlockState(anchorPos()));
			serverCommand(server, "kill @e[type=item]");
			player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			Config.wind = true;
			Config.cold = true;
		}));
		// A snow layer at the cliff's foot: the rope only hangs through air, so it stops a block
		// higher, and a player standing on the snow (feet in the snow's block) must still take hold.
		run(mc -> onServer(mc, server -> {
			ServerPlayer player = player(server);
			serverCommand(server, "setblock " + (ANCHOR[0] - 1) + " " + (ANCHOR[1] - 12) + " " + ANCHOR[2] + " minecraft:snow");
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Ropes.ROPE.get(), 64));
			useOnAnchor(player);
		}));
		// A second for the snow and the rope to reach the player's own game before they stand there.
		waitTicks(20);
		climbRope(2, false);
		run(mc -> onServer(mc, server -> {
			ServerLevel level = player(server).level();
			Ropes.takeDown(level, anchorPos(), level.getBlockState(anchorPos()));
			serverCommand(server, "setblock " + (ANCHOR[0] - 1) + " " + (ANCHOR[1] - 12) + " " + ANCHOR[2] + " minecraft:air");
			player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}));
		run(mc -> {
			check(ROPE_CLIMB[2] > 3.0, "Rope: standing on snow under its last piece, you couldn't climb it (rose " + String.format("%.2f", ROPE_CLIMB[2]) + " blocks in 3 s)");
			check(ROPE_RESULT[1].equals("0 left hanging, 64 carried"), "Rope: sneak-right-click didn't reel it all back in (" + ROPE_RESULT[1] + ")");
			check(ROPE_RESULT[2].equals("0 left hanging, 13 dropped"), "Rope: breaking the anchor didn't drop the whole rope by it (" + ROPE_RESULT[2] + ")");
			check(ROPE_RESULT[3].equals("6 left hanging, 6 dropped"), "Rope: cutting it halfway didn't drop just the rope below the cut (" + ROPE_RESULT[3] + ")");
		});
	}

	private static BlockPos anchorPos() {
		return new BlockPos(ANCHOR[0], ANCHOR[1], ANCHOR[2]);
	}

	private static void placeAnchor(ServerLevel level) {
		level.setBlock(anchorPos(), Ropes.ANCHOR_BLOCK.get().defaultBlockState().setValue(AnchorBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
	}

	/** Right-clicks the anchor's top with whatever is in the main hand, the way the game does. */
	private static void useOnAnchor(ServerPlayer player) {
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(anchorPos()), Direction.UP, anchorPos(), false);
		player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
	}

	/** How many rope blocks hang in the column in front of the anchor, down to the ground. */
	private static int ropeBlocks(ServerLevel level) {
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
	private static int ropeDropped(ServerLevel level) {
		AABB around = new AABB(anchorPos()).inflate(4, 16, 4);
		int count = 0;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, around, entity -> entity.getItem().is(Ropes.ROPE.get()))) {
			count += item.getItem().getCount();
		}
		return count;
	}

	/** Stands at the foot of the rope and walks east into the cliff for three seconds; stores how far the player rose. */
	private static void climbRope(int index, boolean gloves) {
		run(mc -> onServer(mc, server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			equip(player, "hands", gloves ? new ItemStack(Clothes.CLIMBING_GLOVES.get()) : ItemStack.EMPTY);
			serverCommand(server, "tp @a " + (ANCHOR[0] - 0.5) + " " + (ANCHOR[1] - 12) + " " + (ANCHOR[2] + 0.5) + " -90 0");
			player.setDeltaMovement(0, 0, 0);
		}));
		// The game ignores the player's own movement until the teleport is confirmed, and on CI's
		// machines a fixed wait wasn't always long enough: wait until the player's own game has them
		// standing at the rope's foot, then a moment more.
		waitUntil("the player standing at the rope's foot", 200, mc -> mc.player != null && mc.player.onGround()
				&& Math.abs(mc.player.getX() - (ANCHOR[0] - 0.5)) < 0.3 && Math.abs(mc.player.getZ() - (ANCHOR[2] + 0.5)) < 0.3
				&& mc.player.getY() < ANCHOR[1] - 10);
		waitTicks(10);
		run(mc -> mc.options.keyUp.setDown(true));
		waitTicks(60);
		run(mc -> {
			mc.options.keyUp.setDown(false);
			onServer(mc, server -> {
				ROPE_CLIMB[index] = player(server).getY() - (ANCHOR[1] - 12);
				Summiteer.LOGGER.info("[test] rope climb with gloves {}: rose {}", gloves, ROPE_CLIMB[index]);
			});
		});
		if (gloves) {
			ClientTest.screenshot();
		}
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
