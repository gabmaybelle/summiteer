package com.gabbymaybelle.summiteer;

import com.gabbymaybelle.summiteer.client.Whiteout;
import com.gabbymaybelle.summiteer.clothes.Clothes;
import com.gabbymaybelle.summiteer.gear.Gear;
import com.gabbymaybelle.summiteer.weather.Weather;
import com.gabbymaybelle.summiteer.weather.Wind;
import com.gabbymaybelle.summiteer.zones.Zone;
import com.gabbymaybelle.summiteer.zones.Zones;
import eu.pb4.trinkets.api.TrinketsApi;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Films the YouTube Short with {@link Film}: a screenshot every tick, in a tall 1080x1920 window.
 * Only runs with `./gradlew --no-configuration-cache :fabric:runClientGameTest -PvideoTour`.
 * Frames go in build/run/clientGameTest/video_tour.
 *
 * <p>Third person, in survival, in the full kit: beanie, a red parka (it reads against snow on a
 * phone), snow pants, wool socks, climbing gloves, snow goggles and crampons, the ice axe in hand.
 * Light snow falls (the game's rain, which is snow up here). The scenes: climbing a natural cliff
 * with the axe, a gust shoving the player and then a braced crouch, letting go of the wall and
 * sliding down unhurt, topping out on the highest peak nearby, and the climb again for the loop.
 *
 * <p>The stage finds the cliff and the peak by the mod's own rules (the axe's grip tags and
 * {@link Zones}), in a new world from the seed the release hand play used, and keeps them.
 * Nothing about the mod's timing is changed: gusts come when the mod sends them, and the wind
 * scene waits for one (its log marks the warning and the hit for the sound pass).
 */
public class VideoTour implements FabricClientGameTest {
	/** Names the system properties: summiteer.videoTour, summiteer.videoScenes... */
	private static final String PREFIX = "summiteer";
	/** The world the release hand play used: a frozen-peaks range around (-1600, 1330). */
	private static final String SEED = "4769928995815751347";
	/** Vanilla red dye. */
	private static final int PARKA = 0xB02E26;

	private Film film;
	/** Where the player stands at the foot of the cliff, and which way the wall is (dx, dz). */
	private BlockPos foot;
	private int wallDx, wallDz, wallHeight;
	/** A flat spot near the cliff top for the wind; the peak, and where the walk up to it starts. */
	private BlockPos windSpot, peak, approach;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Film.on(PREFIX)) {
			return;
		}
		film = new Film(context, PREFIX, "video_tour");
		film.take(() -> film.newWorld(SEED), this::stage, this::scenes);
	}

	// ------------------------------------------------------------------ the set

	/** Finds the cliff, a flat spot above it and the peak, and keeps them. */
	private void stage() {
		int[] cliff = film.server.computeOnServer(s -> findCliff(s.overworld(), -1590, 1330, 80));
		Film.check(cliff != null, "a climbable cliff above the treeline near -1590 1330");
		film.keep("cliff", cliff[0] + " " + cliff[1] + " " + cliff[2] + " " + cliff[3] + " " + cliff[4] + " " + cliff[5]);
		int[] flat = film.server.computeOnServer(s -> findFlat(s.overworld(), cliff[0] + cliff[3] * 3, cliff[2] + cliff[4] * 3, 14));
		Film.check(flat != null, "a flat spot above the treeline near the cliff top");
		film.keep("wind", flat[0] + " " + flat[1] + " " + flat[2]);
		int[] top = film.server.computeOnServer(s -> findPeak(s.overworld(), -1804, 1488, 64));
		Film.check(top != null, "a peak with a walkable way up near -1804 1488");
		film.keep("peak", top[0] + " " + top[1] + " " + top[2] + " " + top[3] + " " + top[4] + " " + top[5]);
	}

	private void readSet() {
		int[] c = ints(film.kept("cliff"));
		foot = new BlockPos(c[0], c[1], c[2]);
		wallDx = c[3];
		wallDz = c[4];
		wallHeight = c[5];
		int[] w = ints(film.kept("wind"));
		windSpot = new BlockPos(w[0], w[1], w[2]);
		int[] p = ints(film.kept("peak"));
		peak = new BlockPos(p[0], p[1], p[2]);
		approach = new BlockPos(p[3], p[4], p[5]);
		film.note("cliff", "foot " + foot.toShortString() + ", wall " + wallDx + "," + wallDz + ", " + wallHeight + " high");
	}

	private static int[] ints(String s) {
		String[] parts = s.trim().split(" ");
		int[] out = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			out[i] = Integer.parseInt(parts[i]);
		}
		return out;
	}

	/** The y a player stands at on this column (the first air above the ground). */
	private static int standY(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	/** Whether the ice axe grips this block for a player standing at {@code feet}: the mod's own rule. */
	private static boolean grips(ServerLevel level, BlockPos block, BlockPos feet) {
		BlockState state = level.getBlockState(block);
		boolean snowOrIce = state.is(Gear.ICE_AXE_CLIMBABLE);
		boolean rock = !snowOrIce && state.is(Gear.ICE_AXE_CLIMBABLE_HIGH);
		return com.gabbymaybelle.summiteer.gear.Climbing.grips(snowOrIce, rock, Zones.at(level, feet));
	}

	/**
	 * The best cliff within {@code r} of (cx, cz): a wall the axe grips from the ground up, with the
	 * player's spot at its foot above the treeline, open ground behind it for the camera, and walls
	 * either side so it reads as a cliff, not a pillar. Returns {foot x, y, z, wall dx, dz, height}.
	 */
	private static int[] findCliff(ServerLevel level, int cx, int cz, int r) {
		int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
		int[] best = null;
		int bestScore = 0;
		for (int x = cx - r; x <= cx + r; x++) {
			for (int z = cz - r; z <= cz + r; z++) {
				int fy = standY(level, x, z);
				BlockPos feet = new BlockPos(x, fy, z);
				if (Zones.at(level, feet) == Zone.LOW) {
					continue;
				}
				for (int[] d : dirs) {
					int wx = x + d[0], wz = z + d[1];
					int h = 0;
					while (h < 40 && grips(level, new BlockPos(wx, fy + h, wz), feet)) {
						h++;
					}
					if (h < 8) {
						continue;
					}
					// Open ground behind the player for a camera six blocks back.
					boolean open = true;
					for (int k = 1; k <= 6 && open; k++) {
						open = standY(level, x - d[0] * k, z - d[1] * k) <= fy + 2;
					}
					if (!open) {
						continue;
					}
					// Walls beside it, so it reads as a cliff face.
					int side = 0;
					for (int s = -2; s <= 2; s++) {
						if (s != 0 && standY(level, wx + d[1] * s, wz + d[0] * s) >= fy + 6) {
							side++;
						}
					}
					int score = h * 2 + side * 3;
					if (score > bestScore) {
						bestScore = score;
						best = new int[] { x, fy, z, d[0], d[1], h };
					}
				}
			}
		}
		return best;
	}

	/** The flattest open 5x5 above the treeline within {@code r} of (cx, cz): {x, y, z}. */
	private static int[] findFlat(ServerLevel level, int cx, int cz, int r) {
		int[] best = null;
		int bestSpread = 3;
		for (int x = cx - r; x <= cx + r; x++) {
			for (int z = cz - r; z <= cz + r; z++) {
				int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						int y = standY(level, x + dx, z + dz);
						lo = Math.min(lo, y);
						hi = Math.max(hi, y);
					}
				}
				int y = standY(level, x, z);
				if (hi - lo < bestSpread && Zones.at(level, new BlockPos(x, y, z)) != Zone.LOW) {
					bestSpread = hi - lo;
					best = new int[] { x, y, z };
				}
			}
		}
		return best;
	}

	/**
	 * The highest column within {@code r} of (cx, cz), and a start six blocks out from which every
	 * step up to it is at most one block (walkable with auto-jump): {top x, y, z, start x, y, z}.
	 */
	private static int[] findPeak(ServerLevel level, int cx, int cz, int r) {
		int px = cx, pz = cz, py = Integer.MIN_VALUE;
		for (int x = cx - r; x <= cx + r; x++) {
			for (int z = cz - r; z <= cz + r; z++) {
				int y = standY(level, x, z);
				if (y > py) {
					py = y;
					px = x;
					pz = z;
				}
			}
		}
		int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };
		for (int[] d : dirs) {
			boolean walkable = true;
			int prev = py;
			for (int k = 1; k <= 6 && walkable; k++) {
				int y = standY(level, px + d[0] * k, pz + d[1] * k);
				walkable = prev - y <= 1 && prev - y >= 0;
				prev = y;
			}
			if (walkable) {
				int sx = px + d[0] * 6, sz = pz + d[1] * 6;
				return new int[] { px, py, pz, sx, standY(level, sx, sz), sz };
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ the kit

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** The full kit on, the ice axe in hand, full health and food. Waits off camera for the item name to fade. */
	private void kit() {
		film.server.runOnServer(server -> {
			ServerPlayer p = player(server);
			p.getInventory().clearContent();
			p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Clothes.BEANIE.get()));
			ItemStack parka = new ItemStack(Clothes.PARKA.get());
			parka.set(DataComponents.DYED_COLOR, new DyedItemColor(PARKA));
			p.setItemSlot(EquipmentSlot.CHEST, parka);
			p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Clothes.SNOW_PANTS.get()));
			p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Clothes.WOOL_SOCKS.get()));
			var slots = TrinketsApi.getAttachment(p).getInventories();
			slots.get("head/face").setItem(0, new ItemStack(Gear.SNOW_GOGGLES.get()));
			slots.get("feet/shoes").setItem(0, new ItemStack(Gear.CRAMPONS.get()));
			slots.get("hand/glove").setItem(0, new ItemStack(Clothes.CLIMBING_GLOVES.get()));
			p.getInventory().setSelectedSlot(0);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Gear.ICE_AXE.get()));
			p.setHealth(p.getMaxHealth());
			p.getFoodData().setFoodLevel(20);
			p.setTicksFrozen(0);
		});
		film.ctx.waitTicks(45);
	}

	/** Survival, HUD on, third person from six blocks behind, the kit on. */
	private void ready() {
		film.hud(true);
		film.survival();
		film.thirdPerson(true, 6);
		film.ctx.runOnClient(mc -> mc.options.autoJump().set(false));
		kit();
	}

	/** Puts the player at {@code feet}, still, looking at {@code look}. */
	private void place(Vec3 feet, Vec3 look) {
		film.camera(feet, look);
		film.server.runOnServer(server -> player(server).setDeltaMovement(Vec3.ZERO));
		film.ctx.waitTicks(3);
		film.turnHead(look);
	}

	private double y() {
		return film.server.computeOnServer(server -> player(server).getY());
	}

	private float health() {
		return film.server.computeOnServer(server -> player(server).getHealth());
	}

	/** Against the wall at the cliff foot, {@code up} blocks above the ground, facing the wall. */
	private Vec3 onWall(double up) {
		// The wall's face is half a block from the foot column's middle; the player is 0.6 wide.
		return new Vec3(foot.getX() + 0.5 + wallDx * 0.19, foot.getY() + up, foot.getZ() + 0.5 + wallDz * 0.19);
	}

	/** A point on the wall, {@code up} blocks above the foot: where the player looks while on it. */
	private Vec3 wallPoint(double up) {
		return new Vec3(foot.getX() + 0.5 + wallDx * 0.5, foot.getY() + up, foot.getZ() + 0.5 + wallDz * 0.5);
	}

	// ------------------------------------------------------------------ the scenes

	private void scenes() {
		readSet();
		// Light snow: the game's rain falls as snow up here. Wait off camera until it is all the way in.
		film.server.runCommand("weather rain");
		film.until0(mc -> mc.level.getRainLevel(1) > 0.99f);
		film.window(1080, 1920, 3);
		film.scene("s_climb", () -> climb(5, 70));
		film.scene("s_wind", this::wind);
		film.scene("s_fall", this::fall);
		film.scene("s_summit", this::summit);
		film.scene("s_loop", () -> climb(0, 60));
	}

	/**
	 * Already on the wall {@code from} blocks up, climbing from the first frame. The loop scene
	 * starts at the ground and ends about where the first one starts; the log notes the heights so
	 * the cut can match them.
	 */
	private void climb(double from, int frames) {
		ready();
		place(onWall(from), wallPoint(from + 6));
		double start = y();
		film.ctx.getInput().holdKey(o -> o.keyUp);
		try {
			film.record(frames);
		} finally {
			film.ctx.getInput().releaseKey(o -> o.keyUp);
		}
		double rose = y() - start;
		film.log("climbed", String.format(Locale.ROOT, "%.2f to %.2f above the foot", start - foot.getY(), y() - foot.getY()));
		Film.check(rose > 2, "the ice axe climbs the cliff (rose " + String.format(Locale.ROOT, "%.2f", rose) + " blocks)");
	}

	/**
	 * On the flat spot, side-on to the wind (it blows from the left of the frame). A gust is
	 * announced and shoves the player; at the next warning they crouch and hold. Waits for the
	 * mod's own gusts, up to twenty seconds each; the cut picks the moments from the log.
	 */
	private void wind() {
		ready();
		long now = film.server.computeOnServer(server -> server.overworld().getGameTime());
		double a = Wind.direction(now);
		double dx = Math.cos(a), dz = Math.sin(a);
		// Facing (dz, -dx) puts the wind's direction on the player's right: it comes from the left.
		Vec3 feet = new Vec3(windSpot.getX() + 0.5, windSpot.getY(), windSpot.getZ() + 0.5);
		place(feet, feet.add(dz * 10, 1.4, -dx * 10));
		Film.check(film.server.computeOnServer(server -> Zones.at(server.overworld(), player(server).blockPosition())) != Zone.LOW,
				"the wind spot is above the treeline");
		film.rolling();
		Vec3 before = feet;
		waitForGust("gust");
		film.record(30);
		double pushed = film.server.computeOnServer(server -> player(server).position()).subtract(before).horizontalDistance();
		film.log("pushed", String.format(Locale.ROOT, "%.2f", pushed));
		Film.check(pushed > 0.3, "the gust pushed the player (" + String.format(Locale.ROOT, "%.2f", pushed) + " blocks)");
		// The next one: crouch at its warning and hold.
		waitForWarning();
		film.ctx.getInput().holdKey(o -> o.keyShift);
		try {
			Vec3 braced = film.server.computeOnServer(server -> player(server).position());
			waitForGust("braced_gust");
			film.record(30);
			double moved = film.server.computeOnServer(server -> player(server).position()).subtract(braced).horizontalDistance();
			film.log("braced_moved", String.format(Locale.ROOT, "%.2f", moved));
			Film.check(moved < pushed, "crouching, the gust moves the player less (" + String.format(Locale.ROOT, "%.2f against %.2f", moved, pushed) + ")");
		} finally {
			film.ctx.getInput().releaseKey(o -> o.keyShift);
		}
	}

	/** The gust due for the player, as {warnAt, hitAt}, or null. Read from the mod, which keeps it private. */
	private long[] gust() {
		return film.server.computeOnServer(server -> {
			try {
				Field f = Weather.class.getDeclaredField("GUSTS");
				f.setAccessible(true);
				Object g = ((Map<?, ?>) f.get(null)).get(player(server).getUUID());
				if (g == null) {
					return null;
				}
				Method warn = g.getClass().getDeclaredMethod("warnAt");
				Method hit = g.getClass().getDeclaredMethod("hitAt");
				warn.setAccessible(true);
				hit.setAccessible(true);
				return new long[] { (long) warn.invoke(g), (long) hit.invoke(g) };
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("couldn't read the gusts", e);
			}
		});
	}

	private long gameTime() {
		return film.server.computeOnServer(server -> server.overworld().getGameTime());
	}

	/** Films until a gust's warning starts (logged), up to twenty seconds. */
	private void waitForWarning() {
		for (int i = 0; i < 400; i++) {
			long[] g = gust();
			if (g != null && gameTime() >= g[0]) {
				film.log("gust_warning", "");
				return;
			}
			film.record(1);
		}
		throw new AssertionError("no gust warning in twenty seconds");
	}

	/** Films through the next gust's warning until it hits (both logged), up to twenty seconds. */
	private void waitForGust(String name) {
		boolean warned = false;
		for (int i = 0; i < 400; i++) {
			long[] g = gust();
			if (g != null && !warned && gameTime() >= g[0]) {
				warned = true;
				film.log(name + "_warning", "");
			}
			if (warned && g == null) {
				film.log(name, "");
				return;
			}
			film.record(1);
		}
		throw new AssertionError("no gust in twenty seconds");
	}

	/**
	 * Ten blocks up the wall, the player lets go and slides down it with the axe, landing unhurt:
	 * the hearts stay full.
	 */
	private void fall() {
		ready();
		place(onWall(Math.min(10, wallHeight - 1)), wallPoint(4));
		float before = health();
		film.rolling();
		film.log("let_go", "");
		for (int i = 0; i < 200 && !film.server.computeOnServer(server -> player(server).onGround()); i++) {
			film.record(1);
		}
		film.log("landed", "");
		film.record(25);
		float lost = before - health();
		Film.check(lost == 0, "sliding down the wall with the axe, the landing doesn't hurt (lost " + lost + ")");
	}

	/**
	 * The last steps up to the peak, then the view turns out over the range. Logs the zone and the
	 * whiteout: the summit beat only has the whiteout if the peak reaches summit height.
	 */
	private void summit() {
		ready();
		film.ctx.runOnClient(mc -> mc.options.autoJump().set(true));
		Vec3 top = new Vec3(peak.getX() + 0.5, peak.getY(), peak.getZ() + 0.5);
		Vec3 start = new Vec3(approach.getX() + 0.5, approach.getY(), approach.getZ() + 0.5);
		place(start, top.add(0, 1.2, 0));
		film.rolling();
		film.ctx.getInput().holdKey(o -> o.keyUp);
		try {
			for (int i = 0; i < 80; i++) {
				Vec3 at = film.server.computeOnServer(server -> player(server).position());
				if (at.horizontalDistance() > 0 && Math.hypot(at.x - top.x, at.z - top.z) < 0.6) {
					break;
				}
				film.turnHead(top.add(0, 1.2, 0));
				film.record(1);
			}
		} finally {
			film.ctx.getInput().releaseKey(o -> o.keyUp);
			film.ctx.runOnClient(mc -> mc.options.autoJump().set(false));
		}
		film.log("on_top", String.format(Locale.ROOT, "y %.1f", y()));
		// Turn to look out past where the climb came from, over the range, slightly down.
		Vec3 out = top.add(top.subtract(start).normalize().scale(-1).yRot((float) Math.toRadians(70)).scale(30)).add(0, -6, 0);
		Vec3 from = top.add(top.subtract(start).normalize().scale(30)).add(0, -2, 0);
		for (int i = 1; i <= 40; i++) {
			film.turnHead(from.lerp(out, Film.ease(i / 40.0)));
			film.record(1);
		}
		film.record(20);
		String zone = film.server.computeOnServer(server -> Zones.at(server.overworld(), player(server).blockPosition()).toString());
		float whiteout = film.ctx.computeOnClient(mc -> Whiteout.amount());
		film.log("summit_zone", zone + ", whiteout " + String.format(Locale.ROOT, "%.2f", whiteout));
		Film.check(!zone.equals("LOW"), "the peak is above the treeline (" + zone + ")");
	}
}
