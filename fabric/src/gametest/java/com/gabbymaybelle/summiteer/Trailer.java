package com.gabbymaybelle.summiteer;

import com.gabbymaybelle.summiteer.client.Whiteout;
import com.gabbymaybelle.summiteer.clothes.Clothes;
import com.gabbymaybelle.summiteer.gear.Gear;
import com.gabbymaybelle.summiteer.weather.Weather;
import com.gabbymaybelle.summiteer.weather.Wind;
import com.gabbymaybelle.summiteer.zones.Zone;
import com.gabbymaybelle.summiteer.zones.Zones;
import com.mojang.blaze3d.platform.InputConstants;
import eu.pb4.trinkets.api.TrinketsApi;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Films the wide trailer (about 74 s) with {@link Film}: a screenshot every tick in a 1920x1080
 * window. Only runs with
 * `./gradlew --no-configuration-cache :fabric:runClientGameTest -PvideoTour -PvideoTrailer`;
 * frames go in build/run/clientGameTest/video_trailer. {@link VideoTour} (the Short) skips itself
 * in a trailer run.
 *
 * <p>One climb, foot to summit, in a vanilla world from a seed search (-576765449: a jagged range
 * whose top is y 232 at about (300, 860), well above summit height). The stage finds four spots by
 * the mod's own rules and keeps them: base camp (flat snowy ground below the treeline), the ridge
 * (high ground open to the wind), the cliff (a face the ice axe grips, ten blocks or more, above the
 * treeline) and the peak (summit height, with a walkable last few steps). The scenes, in the
 * trailer's order: the summit view in clear air; at camp, crafting the red parka, then the camera
 * turning once round the dressed player (dressed off camera by right-clicks, and checked); a gust
 * on the ridge and a braced one; climbing the cliff and hanging on sneak; climbing to ten blocks up
 * in third person and letting go; and the whiteout on the peak, the goggles, and the storm clearing.
 *
 * <p>Nothing about the mod's timing or heights is changed: gusts come when the mod sends them, and
 * the whiteout fades in and out through its own fog. Each scene checks its claim with the mod's
 * own rule and logs the numbers.
 */
public class Trailer implements FabricClientGameTest {
	private static final String PREFIX = "summiteer";
	/** Gabby's pick from the seed search (2026-10-21). */
	private static final String SEED = "-576765449";
	/** The range's top, from the seed search. */
	private static final int PEAK_X = 300, PEAK_Z = 860;
	/** The player's survival HUD is filmed in these; GLFW's mouse buttons. */
	private static final int LEFT = 0, RIGHT = 1;

	private Film film;
	/** Base camp: where the player stands, and which way the range is (an axis: dx, dz). */
	private BlockPos camp;
	private int campDx, campDz;
	private BlockPos ridge;
	/** The foot of the cliff, the wall's side (dx, dz) and its height. */
	private BlockPos foot;
	private int wallDx, wallDz, wallHeight;
	/** The peak, and where its last steps start. */
	private BlockPos peak, approach;

	/** Whether this run films the trailer: {@code -PvideoTrailer} with {@code -PvideoTour}. */
	public static boolean on() {
		return Film.on(PREFIX) && Boolean.getBoolean(PREFIX + ".videoTrailer");
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!on()) {
			return;
		}
		film = new Film(context, PREFIX, "video_trailer");
		film.take(() -> film.newWorld(SEED), this::stage, this::scenes);
	}

	// ------------------------------------------------------------------ the set

	/**
	 * Finds the peak, then the cliff, the ridge and base camp around it, builds the camp (a
	 * crafting table and a lit campfire), and keeps the spots. Searching generates the chunks
	 * round the range, so a stage takes a few minutes.
	 */
	private void stage() {
		int[] top = film.server.computeOnServer(s -> findSummit(s.overworld(), PEAK_X, PEAK_Z, 24));
		Film.check(top != null, "a summit-height peak with a walkable last few steps near " + PEAK_X + " " + PEAK_Z);
		film.keep("peak", join(top));
		int[] cliff = film.server.computeOnServer(s -> {
			// Near the peak most rock is at summit height already: look further out if need be.
			int[] near = findCliff(s.overworld(), top[0], top[2], 96, 10);
			return near != null ? near : findCliff(s.overworld(), top[0], top[2], 180, 10);
		});
		Film.check(cliff != null, "a cliff the ice axe grips, 10 blocks or more, above the treeline near the peak");
		film.keep("cliff", join(cliff));
		int[] open = film.server.computeOnServer(s -> findRidge(s.overworld(), top[0], top[2], 120));
		Film.check(open != null, "a flat spot on high ground, open to the wind, near the peak");
		film.keep("ridge", join(open));
		int[] base = film.server.computeOnServer(s -> findCamp(s.overworld(), top[0], top[2], 40, 320));
		Film.check(base != null, "flat snowy ground below the treeline near the range");
		film.keep("camp", join(base));
		film.server.runOnServer(s -> buildCamp(s.overworld(), base));
	}

	private void readSet() {
		int[] c = VideoTour.ints(film.kept("camp"));
		camp = new BlockPos(c[0], c[1], c[2]);
		campDx = c[3];
		campDz = c[4];
		int[] r = VideoTour.ints(film.kept("ridge"));
		ridge = new BlockPos(r[0], r[1], r[2]);
		int[] w = VideoTour.ints(film.kept("cliff"));
		foot = new BlockPos(w[0], w[1], w[2]);
		wallDx = w[3];
		wallDz = w[4];
		wallHeight = w[5];
		int[] p = VideoTour.ints(film.kept("peak"));
		peak = new BlockPos(p[0], p[1], p[2]);
		approach = new BlockPos(p[3], p[4], p[5]);
		film.note("set", "camp " + camp.toShortString() + ", ridge " + ridge.toShortString() + ", cliff foot " + foot.toShortString()
				+ " (" + wallHeight + " high), peak " + peak.toShortString());
	}

	private static String join(int[] values) {
		StringBuilder out = new StringBuilder();
		for (int v : values) {
			out.append(out.isEmpty() ? "" : " ").append(v);
		}
		return out.toString();
	}

	/** The crafting table two blocks towards the range, the campfire beside it. */
	private static BlockPos table(BlockPos camp, int dx, int dz) {
		return camp.offset(dx * 2, 0, dz * 2);
	}

	private static BlockPos campfire(BlockPos camp, int dx, int dz) {
		// Two to the right of the way the range is, one towards it.
		return camp.offset(-dz * 2 + dx, 0, dx * 2 + dz);
	}

	private static void buildCamp(ServerLevel level, int[] base) {
		BlockPos at = new BlockPos(base[0], base[1], base[2]);
		for (BlockPos pos : new BlockPos[] { table(at, base[3], base[4]), campfire(at, base[3], base[4]) }) {
			BlockPos ground = new BlockPos(pos.getX(), VideoTour.standY(level, pos.getX(), pos.getZ()), pos.getZ());
			level.setBlockAndUpdate(ground, pos.equals(table(at, base[3], base[4])) ? Blocks.CRAFTING_TABLE.defaultBlockState()
					: Blocks.CAMPFIRE.defaultBlockState());
		}
	}

	/**
	 * The highest column within {@code r} of (cx, cz) that stands at summit height with five
	 * blocks to spare and has a walkable way up its last six (or four) blocks: every step at most
	 * one block, so auto-jump takes it. Returns {top x, y, z, start x, y, z}.
	 */
	static int[] findSummit(ServerLevel level, int cx, int cz, int r) {
		int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };
		// The candidates, highest first: tried until one has a walkable approach.
		java.util.List<int[]> columns = new java.util.ArrayList<>();
		for (int x = cx - r; x <= cx + r; x++) {
			for (int z = cz - r; z <= cz + r; z++) {
				int y = VideoTour.standY(level, x, z);
				if (Zones.at(level, new BlockPos(x, y - 5, z)) == Zone.SUMMIT) {
					columns.add(new int[] { x, y, z });
				}
			}
		}
		columns.sort((a, b) -> b[1] - a[1]);
		for (int steps : new int[] { 6, 4 }) {
			for (int[] c : columns.subList(0, Math.min(60, columns.size()))) {
				for (int[] d : dirs) {
					boolean walkable = true;
					int prev = c[1];
					for (int k = 1; k <= steps && walkable; k++) {
						int y = VideoTour.standY(level, c[0] + d[0] * k, c[2] + d[1] * k);
						walkable = prev - y <= 1 && prev - y >= 0;
						prev = y;
					}
					if (walkable) {
						int sx = c[0] + d[0] * steps, sz = c[2] + d[1] * steps;
						return new int[] { c[0], c[1], c[2], sx, VideoTour.standY(level, sx, sz), sz };
					}
				}
			}
		}
		return null;
	}

	/**
	 * The best cliff within {@code r} of (cx, cz): a wall the axe grips (the mod's rule, {@code
	 * VideoTour.grips}) at least {@code minHeight} blocks from the ground up, with the player's spot
	 * at its foot above the treeline, open ground behind it for a third-person camera, and walls
	 * either side so it reads as a face. Taller and wider wins. Returns {foot x, y, z, wall dx, dz,
	 * height}.
	 */
	static int[] findCliff(ServerLevel level, int cx, int cz, int r, int minHeight) {
		int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
		int[] best = null;
		int bestScore = 0;
		for (int x = cx - r; x <= cx + r; x++) {
			for (int z = cz - r; z <= cz + r; z++) {
				int fy = VideoTour.standY(level, x, z);
				BlockPos feet = new BlockPos(x, fy, z);
				if (Zones.at(level, feet) == Zone.LOW) {
					continue;
				}
				for (int[] d : dirs) {
					int wx = x + d[0], wz = z + d[1];
					int h = 0;
					while (h < 40 && VideoTour.grips(level, new BlockPos(wx, fy + h, wz), feet)) {
						h++;
					}
					// The climbed part (the fall starts ten up) stays below summit height, so falling
					// snow there is no whiteout: in the first scout the cliff's foot was already at
					// summit height and both wall scenes were white (2026-10-21).
					if (h < minHeight || Zones.at(level, feet.above(Math.min(h, 12))) == Zone.SUMMIT) {
						continue;
					}
					boolean open = true;
					for (int k = 1; k <= 6 && open; k++) {
						open = VideoTour.standY(level, x - d[0] * k, z - d[1] * k) <= fy + 2;
					}
					if (!open) {
						continue;
					}
					int side = 0;
					for (int s = -2; s <= 2; s++) {
						if (s != 0 && VideoTour.standY(level, wx + d[1] * s, wz + d[0] * s) >= fy + 6) {
							side++;
						}
					}
					int score = Math.min(h, 24) * 2 + side * 3;
					if (score > bestScore) {
						bestScore = score;
						best = new int[] { x, fy, z, d[0], d[1], h };
					}
				}
			}
		}
		return best;
	}

	/**
	 * The ridge: a flat spot in the HIGH zone (above the treeline, below summit height) within
	 * {@code r} of (cx, cz), open to the sky and to the wind (nothing within four blocks stands
	 * more than a block above it), scored by how far the ground drops away round it. Returns {x, y, z}.
	 */
	static int[] findRidge(ServerLevel level, int cx, int cz, int r) {
		int[] best = null;
		int bestScore = -1;
		for (int x = cx - r; x <= cx + r; x += 2) {
			for (int z = cz - r; z <= cz + r; z += 2) {
				int y = VideoTour.standY(level, x, z);
				BlockPos feet = new BlockPos(x, y, z);
				if (Zones.at(level, feet) != Zone.HIGH || !level.canSeeSky(feet.above())) {
					continue;
				}
				int lo = y, hi = y;
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						int h = VideoTour.standY(level, x + dx, z + dz);
						lo = Math.min(lo, h);
						hi = Math.max(hi, h);
					}
				}
				if (hi - lo > 1 || !level.getFluidState(feet.below()).isEmpty()) {
					continue;
				}
				boolean open = true;
				for (int dx = -4; dx <= 4 && open; dx++) {
					for (int dz = -4; dz <= 4 && open; dz++) {
						open = VideoTour.standY(level, x + dx, z + dz) <= y + 1;
					}
				}
				if (!open) {
					continue;
				}
				int drops = 0;
				for (int a = 0; a < 16; a++) {
					double angle = Math.PI * 2 * a / 16;
					int h = VideoTour.standY(level, x + (int) Math.round(Math.cos(angle) * 9), z + (int) Math.round(Math.sin(angle) * 9));
					if (h <= y - 4) {
						drops++;
					}
				}
				if (drops > bestScore) {
					bestScore = drops;
					best = new int[] { x, y, z };
				}
			}
		}
		return best;
	}

	/**
	 * Base camp: flat snowy ground below the treeline, the nearest to (cx, cz) found by searching
	 * outwards in rings from {@code from} to {@code to} blocks. Snowy means the biome snows there (the
	 * game's own precipitation rule, as the mod's storm uses); flat means a 5x5 within one block,
	 * dry, no powder snow and no trees overhead or beside it. Returns {x, y, z, dx, dz}, the last two
	 * the axis pointing back at the range.
	 */
	static int[] findCamp(ServerLevel level, int cx, int cz, int from, int to) {
		for (int ring = from; ring <= to; ring += 4) {
			for (int i = -ring; i <= ring; i += 4) {
				int[][] spots = { { cx + i, cz - ring }, { cx + i, cz + ring }, { cx - ring, cz + i }, { cx + ring, cz + i } };
				for (int[] s : spots) {
					if (campable(level, s[0], s[1])) {
						int y = VideoTour.standY(level, s[0], s[1]);
						int dx = cx - s[0], dz = cz - s[1];
						boolean alongX = Math.abs(dx) >= Math.abs(dz);
						return new int[] { s[0], y, s[1], alongX ? Integer.signum(dx) : 0, alongX ? 0 : Integer.signum(dz) };
					}
				}
			}
		}
		return null;
	}

	private static boolean campable(ServerLevel level, int x, int z) {
		int y = VideoTour.standY(level, x, z);
		BlockPos feet = new BlockPos(x, y, z);
		if (Zones.at(level, feet) != Zone.LOW || y <= level.getSeaLevel() + 2) {
			return false;
		}
		if (level.getBiome(feet).value().getPrecipitationAt(feet, level.getSeaLevel()) != Biome.Precipitation.SNOW) {
			return false;
		}
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				int h = VideoTour.standY(level, x + dx, z + dz);
				if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) {
					if (Math.abs(h - y) > 1) {
						return false;
					}
					BlockState under = level.getBlockState(new BlockPos(x + dx, h - 1, z + dz));
					if (under.is(Blocks.POWDER_SNOW) || !under.getFluidState().isEmpty() || under.is(Blocks.ICE)) {
						return false;
					}
				}
				// No trees here or right beside: leaves would show over the motion-blocking ground.
				if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + dx, z + dz) != h) {
					return false;
				}
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ the kit

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** Vanilla red dye's colour: what dyeing the parka with one red dye gives. */
	private static int red() {
		return DyeColor.RED.getTextureDiffuseColor() & 0xFFFFFF;
	}

	private static ItemStack redParka() {
		ItemStack parka = new ItemStack(Clothes.PARKA.get());
		parka.set(DataComponents.DYED_COLOR, new DyedItemColor(red()));
		return parka;
	}

	/**
	 * Clears the player and gives health and food. {@code dressed}: the full kit on (the clothes,
	 * a red parka, the goggles unless {@code goggles} is false, crampons and gloves), the ice axe in
	 * hand. Waits off camera for the item name over the hotbar to fade.
	 */
	private void kit(boolean dressed, boolean goggles) {
		film.server.runOnServer(server -> {
			ServerPlayer p = player(server);
			p.getInventory().clearContent();
			var slots = TrinketsApi.getAttachment(p).getInventories();
			for (String slot : new String[] { "head/face", "feet/shoes", "hand/glove" }) {
				slots.get(slot).setItem(0, ItemStack.EMPTY);
			}
			if (dressed) {
				p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Clothes.BEANIE.get()));
				p.setItemSlot(EquipmentSlot.CHEST, redParka());
				p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Clothes.SNOW_PANTS.get()));
				p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Clothes.WOOL_SOCKS.get()));
				if (goggles) {
					slots.get("head/face").setItem(0, new ItemStack(Gear.SNOW_GOGGLES.get()));
				}
				slots.get("feet/shoes").setItem(0, new ItemStack(Gear.CRAMPONS.get()));
				slots.get("hand/glove").setItem(0, new ItemStack(Clothes.CLIMBING_GLOVES.get()));
				p.getInventory().setItem(0, new ItemStack(Gear.ICE_AXE.get()));
			}
			p.getInventory().setSelectedSlot(0);
			p.setHealth(p.getMaxHealth());
			p.getFoodData().setFoodLevel(20);
			p.setTicksFrozen(0);
		});
		// The first hotbar slot in hand from the client too: setting it on the server alone left an
		// earlier scene's slot 9 selected, and the climbs ran without the axe (second scout, 2026-10-21).
		hotbar(0);
		film.ctx.waitTicks(45);
		Film.check(film.ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot() == 0), "the first hotbar slot is in hand");
	}

	/** Survival, HUD on, third person ({@code distance} behind) or first person (0). No auto-jump. */
	private void ready(double distance) {
		film.hud(true);
		film.survival();
		film.thirdPerson(distance > 0, distance > 0 ? distance : 4);
		film.ctx.runOnClient(mc -> mc.options.autoJump().set(false));
	}

	/**
	 * Light snow (the game's rain, which falls as snow up here) or clear air, waiting off camera
	 * until it has fully come or gone.
	 */
	private void weather(boolean snow) {
		film.server.runCommand(snow ? "weather rain" : "weather clear");
		film.until0(mc -> snow ? mc.level.getRainLevel(1) > 0.99f : mc.level.getRainLevel(1) <= 0);
	}

	/** Puts the player at {@code feet}, still, looking at {@code look}. */
	private void place(Vec3 feet, Vec3 look) {
		film.camera(feet, look);
		film.server.runOnServer(server -> player(server).setDeltaMovement(Vec3.ZERO));
		film.ctx.waitTicks(3);
		film.turnHead(look);
	}

	private Vec3 pos() {
		return film.server.computeOnServer(server -> player(server).position());
	}

	private float health() {
		return film.server.computeOnServer(server -> player(server).getHealth());
	}

	private Zone zone() {
		return film.server.computeOnServer(server -> Zones.at(server.overworld(), player(server).blockPosition()));
	}

	private static Vec3 centre(BlockPos pos) {
		return Vec3.atBottomCenterOf(pos);
	}

	/** Against the wall at the cliff foot, {@code up} blocks above the ground, facing the wall. */
	private Vec3 onWall(double up) {
		return new Vec3(foot.getX() + 0.5 + wallDx * 0.19, foot.getY() + up, foot.getZ() + 0.5 + wallDz * 0.19);
	}

	private Vec3 wallPoint(double up) {
		return new Vec3(foot.getX() + 0.5 + wallDx * 0.5, foot.getY() + up, foot.getZ() + 0.5 + wallDz * 0.5);
	}

	/** Puts a hotbar slot in hand the way a player does, with its number key. */
	private void hotbar(int slot) {
		film.ctx.getInput().pressKey(o -> o.keyHotbarSlots[slot]);
	}

	// ------------------------------------------------------------------ the scenes

	private void scenes() {
		readSet();
		film.window(1920, 1080, 3);
		if (film.scouting()) {
			// The set first, before any action: the stage chose these spots itself, so Gabby sees them.
			film.scene("x_set_camp", this::previewCamp);
			film.scene("x_set_ridge", this::previewRidge);
			film.scene("x_set_cliff", this::previewCliff);
			film.scene("x_set_peak", this::previewPeak);
		}
		film.scene("w_open", this::open);
		film.scene("w_craft", this::craft);
		film.scene("w_turn", this::turn);
		film.scene("w_wind", this::wind);
		film.scene("w_climb", this::climb);
		film.scene("w_fall", this::fall);
		film.scene("w_summit", this::summit);
	}

	/** Scout only: stills of each spot the stage chose, from a spectator camera above it. */
	private void previewCamp() {
		Vec3 at = centre(camp);
		// From behind the camp, the range beyond it.
		preview(at.add(-campDx * 14, 7, -campDz * 14), at.add(campDx * 10, 2, campDz * 10));
	}

	private void previewRidge() {
		Vec3 at = centre(ridge);
		preview(at.add(10, 6, 10), at);
	}

	private void previewCliff() {
		Vec3 face = wallPoint(wallHeight / 2.0);
		preview(face.add(-wallDx * 20, wallHeight / 2.0 + 4, -wallDz * 20), face);
	}

	private void previewPeak() {
		Vec3 top = centre(peak);
		Vec3 from = centre(approach);
		preview(from.add(from.subtract(top).normalize().scale(14)).add(0, 7, 0), top);
	}

	private void preview(Vec3 eye, Vec3 look) {
		film.hud(false);
		film.spectator();
		film.calm();
		film.camera(eye, look);
		film.record(2);
	}

	/**
	 * Scene 1: on the summit in clear air, dressed for it, the view turning out over the range from
	 * where the climb came up. No HUD: it is the opening shot, and the title follows it.
	 */
	private void open() {
		weather(false);
		ready(6);
		kit(true, true);
		film.hud(false);
		Vec3 top = centre(peak);
		Vec3 back = top.subtract(centre(approach)).normalize();
		place(top, top.add(back.scale(30)).add(0, -4, 0));
		Vec3 from = top.add(back.scale(30)).add(0, -4, 0);
		Vec3 out = top.add(back.yRot((float) Math.toRadians(110)).scale(30)).add(0, -8, 0);
		for (int i = 1; i <= 100; i++) {
			film.turnHead(from.lerp(out, Film.ease(i / 100.0)));
			film.record(1);
		}
		film.log("open_zone", zone().toString());
		Film.check(zone() == Zone.SUMMIT, "the opening shot is on the summit (" + zone() + ")");
	}

	/**
	 * At base camp in light snow, one quick shot: the crafting table opened, a parka and a red dye
	 * laid in its grid, the red parka taken out. Checks its colour.
	 */
	private void craft() {
		weather(true);
		ready(0);
		kit(false, false);
		film.server.runOnServer(server -> {
			player(server).getInventory().setItem(0, new ItemStack(Clothes.PARKA.get()));
			player(server).getInventory().setItem(1, new ItemStack(Items.DYE.pick(DyeColor.RED)));
		});
		film.ctx.waitTicks(45);
		openTable();
		click(37, LEFT);
		click(5, LEFT);
		click(38, LEFT);
		click(6, LEFT);
		film.until(mc -> mc.gui.screen() instanceof CraftingScreen s && s.getMenu().getSlot(0).hasItem());
		film.record(10);
		click(0, LEFT);
		film.log("crafted", "red parka");
		click(37, LEFT);
		film.record(12);
		closeScreen();
		int colour = film.server.computeOnServer(server -> {
			ItemStack held = player(server).getInventory().getItem(0);
			DyedItemColor dyed = held.is(Clothes.PARKA.get()) ? held.get(DataComponents.DYED_COLOR) : null;
			return dyed == null ? -1 : dyed.rgb() & 0xFFFFFF;
		});
		film.log("parka_colour", String.format(Locale.ROOT, "#%06x", colour));
		Film.check(colour == red(), String.format(Locale.ROOT, "the parka is dyed red (#%06x, red dye is #%06x)", colour, red()));
	}

	/**
	 * Off camera, before the turn: each piece put on with a right-click from the hotbar the way a
	 * player would (vanilla's for the clothes, the mod's for the goggles, gloves and crampons), then
	 * the ice axe in hand. Checks each is worn, by its armour slot or the mod's own
	 * {@code Gear.wears...}, and that the worn parka is red. Not filmed: the trailer gives base camp
	 * only eight seconds (Gabby, 2026-10-21), and the turn shows it all on.
	 */
	private void dressOffCamera() {
		kit(false, false);
		Supplier<Item>[] pieces = pieces();
		film.server.runOnServer(server -> {
			ServerPlayer p = player(server);
			for (int i = 0; i < pieces.length; i++) {
				p.getInventory().setItem(i, i == 1 ? redParka() : new ItemStack(pieces[i].get()));
			}
			p.getInventory().setItem(8, new ItemStack(Gear.ICE_AXE.get()));
		});
		Vec3 at = centre(camp);
		// Looking up and away from the range and the crafting table, so a right-click hits no block
		// (in the scout it opened the table, which stood a block higher, and nothing went on).
		place(at, at.add(-campDx * 10, 8, -campDz * 10));
		Film.check(film.ctx.computeOnClient(mc -> mc.hitResult == null || mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS),
				"the player looks at no block before dressing (a right-click would use it)");
		for (int i = 0; i < pieces.length; i++) {
			hotbar(i);
			film.ctx.waitTicks(4);
			film.ctx.getInput().pressKey(o -> o.keyUse);
			film.ctx.waitTicks(6);
		}
		hotbar(8);
		String missing = film.server.computeOnServer(server -> {
			ServerPlayer p = player(server);
			StringBuilder out = new StringBuilder();
			if (!p.getItemBySlot(EquipmentSlot.HEAD).is(Clothes.BEANIE.get())) out.append(" beanie");
			if (!p.getItemBySlot(EquipmentSlot.CHEST).is(Clothes.PARKA.get())) out.append(" parka");
			if (!p.getItemBySlot(EquipmentSlot.LEGS).is(Clothes.SNOW_PANTS.get())) out.append(" snow_pants");
			if (!p.getItemBySlot(EquipmentSlot.FEET).is(Clothes.WOOL_SOCKS.get())) out.append(" wool_socks");
			if (!Gear.wearsGoggles(p)) out.append(" goggles");
			if (!Gear.wearsClimbingGloves(p)) out.append(" gloves");
			if (!Gear.wearsCrampons(p)) out.append(" crampons");
			return out.toString().trim();
		});
		int colour = film.server.computeOnServer(server -> {
			DyedItemColor dyed = player(server).getItemBySlot(EquipmentSlot.CHEST).get(DataComponents.DYED_COLOR);
			return dyed == null ? -1 : dyed.rgb() & 0xFFFFFF;
		});
		film.note("worn", missing.isEmpty() ? "all seven, by right-click" : "missing " + missing);
		film.note("parka_colour", String.format(Locale.ROOT, "#%06x", colour));
		Film.check(missing.isEmpty(), "every piece is worn after a right-click each (missing: " + missing + ")");
		Film.check(colour == red(), String.format(Locale.ROOT, "the worn parka is red (#%06x)", colour));
		// The axe's name over the hotbar fades before the turn starts.
		film.ctx.waitTicks(45);
	}

	@SuppressWarnings("unchecked")
	private static Supplier<Item>[] pieces() {
		return new Supplier[] { Clothes.BEANIE, Clothes.PARKA, Clothes.SNOW_PANTS, Clothes.WOOL_SOCKS, Gear.SNOW_GOGGLES,
				Clothes.CLIMBING_GLOVES, Gear.CRAMPONS };
	}

	/**
	 * At camp, dressed off camera piece by piece (see {@link #dressOffCamera}): the camera turns
	 * once round the player, so every piece shows. It is the game's own third-person camera: only
	 * the view turns, a little each frame, while the body and head are held facing the range, so
	 * the camera circles a player who stands still. (Riding an invisible armour stand instead showed
	 * no player at all in 26.3, in first or third person: the second and third scouts, 2026-10-21.)
	 */
	private void turn() {
		weather(true);
		ready(0);
		dressOffCamera();
		film.hud(false);
		film.thirdPerson(true, 4.5);
		Vec3 at = centre(camp);
		place(at, at.add(campDx * 10, 1.6, campDz * 10));
		float body = (float) Math.toDegrees(Math.atan2(-campDx, campDz));
		Vec3 chest = at.add(0, 1.1, 0);
		// The camera stays in third person after the turn (the next scene sets its own), so a scout's
		// closing still shows the turn's last frame.
		for (int i = 0; i < 140; i++) {
			// The view starts facing the range (camera behind the player) and turns once round.
			float view = body + 360f * (float) Film.ease(i / 139.0);
			film.ctx.runOnClient(mc -> {
				var p = mc.player;
				p.setYRot(view);
				p.yRotO = view;
				p.setXRot(18);
				p.xRotO = 18;
				p.yBodyRot = p.yBodyRotO = body;
				p.yHeadRot = p.yHeadRotO = body;
			});
			film.record(1);
		}
		double off = film.ctx.computeOnClient(mc -> mc.gameRenderer.mainCamera().position().distanceTo(chest));
		film.log("turn_camera", String.format(Locale.ROOT, "%.1f blocks from the chest", off));
		Film.check(off < 6, "the turn's camera circles the player (" + String.format(Locale.ROOT, "%.1f", off) + " blocks away)");
		film.log("turned", "360");
	}

	/**
	 * On the ridge in light snow, side-on to the wind: a gust is announced and shoves the player;
	 * at the next warning they crouch and hold. Waits for the mod's own gusts (up to twenty
	 * seconds each) and logs the warnings and hits for the cut and the sound.
	 */
	private void wind() {
		weather(true);
		ready(6);
		kit(true, true);
		long now = film.server.computeOnServer(server -> server.overworld().getGameTime());
		double a = Wind.direction(now);
		double dx = Math.cos(a), dz = Math.sin(a);
		Vec3 feet = centre(ridge);
		place(feet, feet.add(dz * 10, 1.4, -dx * 10));
		Zone zone = zone();
		film.log("ridge_zone", zone.toString());
		Film.check(zone == Zone.HIGH, "the ridge is high ground above the treeline (" + zone + ")");
		film.rolling();
		waitForGust("gust");
		film.record(30);
		double pushed = pos().subtract(feet).horizontalDistance();
		film.log("pushed", String.format(Locale.ROOT, "%.2f", pushed));
		Film.check(pushed > 0.3, "the gust pushed the player (" + String.format(Locale.ROOT, "%.2f", pushed) + " blocks)");
		waitForWarning();
		film.ctx.getInput().holdKey(o -> o.keyShift);
		try {
			Vec3 braced = pos();
			waitForGust("braced_gust");
			// Three seconds held firm after it: room for the cut to fill the ridge's fifteen seconds.
			film.record(60);
			double moved = pos().subtract(braced).horizontalDistance();
			film.log("braced_moved", String.format(Locale.ROOT, "%.2f", moved));
			Film.check(moved < pushed, "crouching, the gust moves the player less (" + String.format(Locale.ROOT, "%.2f against %.2f", moved, pushed) + ")");
		} finally {
			film.ctx.getInput().releaseKey(o -> o.keyShift);
		}
	}

	/**
	 * First person on the cliff in light snow: climbing from the foot with the ice axe, then
	 * hanging still on sneak, then on up. Checks blocks climbed, that the hang held, and the zone.
	 *
	 * <p>On the wall the gusts are switched off (the mod's own setting) and back on after: a gust
	 * mid-climb would knock the shot off the wall. The wind has its own scene.
	 */
	private void climb() {
		weather(true);
		ready(0);
		kit(true, true);
		boolean wind = Config.wind;
		Config.wind = false;
		try {
			film.ctx.waitTicks(25);
			place(onWall(0), wallPoint(5));
			double start = pos().y;
			film.rolling();
			// Up to six blocks (less on a short wall), a hang, then on to a block below the top.
			climbTo(start + Math.min(6, wallHeight - 4), 120);
			film.ctx.getInput().holdKey(o -> o.keyShift);
			double hangFrom;
			double hangTo;
			try {
				film.record(4);
				hangFrom = pos().y;
				film.log("hang", "");
				film.record(40);
				hangTo = pos().y;
			} finally {
				film.ctx.getInput().releaseKey(o -> o.keyShift);
			}
			climbTo(start + Math.min(9, wallHeight - 1), 80);
			double rose = pos().y - start;
			Zone zone = zone();
			film.log("climbed", String.format(Locale.ROOT, "%.2f blocks", rose));
			film.log("hang_slipped", String.format(Locale.ROOT, "%.2f", hangFrom - hangTo));
			film.log("cliff_zone", zone.toString());
			Film.check(rose > 3, "the ice axe climbs the cliff (rose " + String.format(Locale.ROOT, "%.2f", rose) + " blocks)");
			Film.check(Math.abs(hangFrom - hangTo) < 0.1, "sneaking, the player hangs still on the wall (moved "
					+ String.format(Locale.ROOT, "%.2f", hangTo - hangFrom) + ")");
			Film.check(zone != Zone.LOW, "the cliff is above the treeline (" + zone + ")");
		} finally {
			Config.wind = wind;
		}
	}

	/** Films the player climbing (forward held) until their feet reach {@code y}, at most {@code frames} frames. */
	private void climbTo(double y, int frames) {
		film.ctx.getInput().holdKey(o -> o.keyUp);
		try {
			for (int i = 0; i < frames && pos().y < y; i++) {
				film.record(1);
			}
		} finally {
			film.ctx.getInput().releaseKey(o -> o.keyUp);
		}
	}

	/**
	 * Third person, HUD on so the hearts show: the player climbs the last six blocks to ten blocks
	 * up, hangs a moment on sneak, then lets go and slides down the wall with the axe, landing
	 * unhurt. Checks the height, the zone and the health before and after. Gusts are off on the
	 * wall, as in the climb.
	 */
	private void fall() {
		weather(true);
		ready(6);
		kit(true, true);
		boolean wind = Config.wind;
		Config.wind = false;
		try {
			film.ctx.waitTicks(25);
			double up = Math.min(10, wallHeight - 1);
			place(onWall(up - 6), wallPoint(up - 2));
			Zone zone = zone();
			float before = health();
			film.rolling();
			climbTo(foot.getY() + up, 140);
			film.ctx.getInput().holdKey(o -> o.keyShift);
			try {
				film.log("hang", "");
				film.record(24);
			} finally {
				film.ctx.getInput().releaseKey(o -> o.keyShift);
			}
			double from = pos().y;
			film.log("let_go", String.format(Locale.ROOT, "%.2f above the foot", from - foot.getY()));
			for (int i = 0; i < 200 && !film.server.computeOnServer(server -> player(server).onGround()); i++) {
				film.record(1);
			}
			film.log("landed", "");
			film.record(60);
			float after = health();
			film.log("fell", String.format(Locale.ROOT, "%.2f blocks", from - pos().y));
			film.log("health", before + " to " + after);
			film.log("fall_zone", zone.toString());
			Film.check(from - pos().y >= up - 1, "the player slid down from about " + up + " blocks up (" + String.format(Locale.ROOT, "%.2f", from - pos().y) + ")");
			Film.check(zone != Zone.LOW, "the fall is above the treeline (" + zone + ")");
			Film.check(before == after && after == film.server.computeOnServer(server -> player(server).getMaxHealth()),
					"sliding down the wall with the axe, the landing doesn't hurt (" + before + " to " + after + ")");
		} finally {
			Config.wind = wind;
		}
	}

	/**
	 * The peak: the last steps up as a snowstorm comes in, the whiteout closing in to 20 blocks, the
	 * goggles going on and pushing it back to 48, then the storm clearing and the view turning out
	 * over the range (the opening shot, now earned). Checks the zone, the snow, both fog distances
	 * and that the fog lifts, all through the mod's own {@link Whiteout}.
	 */
	private void summit() {
		weather(false);
		ready(6);
		kit(true, false);
		film.server.runOnServer(server -> player(server).getInventory().setItem(1, new ItemStack(Gear.SNOW_GOGGLES.get())));
		film.ctx.waitTicks(45);
		film.ctx.runOnClient(mc -> mc.options.autoJump().set(true));
		Vec3 top = centre(peak);
		Vec3 start = centre(approach);
		place(start, top.add(0, 1.2, 0));
		film.rolling();
		film.server.runCommand("weather rain");
		film.log("storm", "");
		film.ctx.getInput().holdKey(o -> o.keyUp);
		try {
			for (int i = 0; i < 120; i++) {
				Vec3 at = pos();
				if (Math.hypot(at.x - top.x, at.z - top.z) < 0.6) {
					break;
				}
				film.turnHead(top.add(0, 1.2, 0));
				film.record(1);
			}
		} finally {
			film.ctx.getInput().releaseKey(o -> o.keyUp);
			film.ctx.runOnClient(mc -> mc.options.autoJump().set(false));
		}
		film.log("on_top", String.format(Locale.ROOT, "y %.1f", pos().y));
		Zone zone = zone();
		Film.check(zone == Zone.SUMMIT, "the peak is at summit height (" + zone + ")");
		Film.check(film.server.computeOnServer(server -> server.overworld().isRaining()), "it is snowing on the peak");
		film.until(mc -> Whiteout.amount() >= 0.99f);
		film.log("whiteout", "");
		film.record(20);
		boolean wanted = film.ctx.computeOnClient(mc -> Whiteout.wanted(mc.level, mc.gameRenderer.mainCamera().blockPosition()));
		float bare = film.ctx.computeOnClient(mc -> Whiteout.lastEnd());
		film.log("fog_bare", String.format(Locale.ROOT, "%.1f", bare));
		Film.check(wanted, "the mod's whiteout rule holds here (summit, snowing)");
		Film.check(Math.abs(bare - 20) < 0.5, "in the whiteout the fog ends at 20 blocks (" + bare + ")");
		hotbar(1);
		film.record(8);
		film.ctx.getInput().pressKey(o -> o.keyUse);
		film.log("goggles_on", "");
		film.record(30);
		float goggles = film.ctx.computeOnClient(mc -> Whiteout.lastEnd());
		film.log("fog_goggles", String.format(Locale.ROOT, "%.1f", goggles));
		Film.check(film.server.computeOnServer(server -> Gear.wearsGoggles(player(server))), "the goggles went on with a right-click");
		Film.check(Math.abs(goggles - 48) < 0.5, "with goggles the fog ends at 48 blocks (" + goggles + ")");
		hotbar(0);
		film.record(10);
		film.server.runCommand("weather clear");
		film.log("clear", "");
		film.until(mc -> Whiteout.amount() <= 0.01f);
		film.log("fog_gone", "");
		// The view turns out past where the climb came from, over the range.
		Vec3 back = top.subtract(start).normalize();
		Vec3 from = top.add(back.scale(30)).add(0, -2, 0);
		Vec3 out = top.add(back.yRot((float) Math.toRadians(110)).scale(30)).add(0, -8, 0);
		for (int i = 1; i <= 60; i++) {
			film.turnHead(from.lerp(out, Film.ease(i / 60.0)));
			film.record(1);
		}
		film.record(20);
		float left = film.ctx.computeOnClient(mc -> Whiteout.amount());
		film.log("fog_after", String.format(Locale.ROOT, "%.2f", left));
		Film.check(left <= 0.01f, "the fog lifts when the storm clears (faded in " + left + ")");
	}

	// ------------------------------------------------------------------ the crafting table

	/** Looks at the camp's crafting table and opens it with a right-click, on camera. */
	private void openTable() {
		Vec3 at = centre(camp);
		place(at, Vec3.atCenterOf(table(camp, campDx, campDz)));
		BlockPos block = film.server.computeOnServer(server -> {
			BlockPos t = table(camp, campDx, campDz);
			return new BlockPos(t.getX(), VideoTour.standY(server.overworld(), t.getX(), t.getZ()) - 1, t.getZ());
		});
		film.aim(block);
		film.rolling();
		film.record(12);
		// The bigger GUI before the screen opens, so it lays its slots out at that scale.
		film.bigGui();
		film.ctx.getInput().pressKey(o -> o.keyUse);
		film.log("table", "");
		film.until(mc -> mc.gui.screen() instanceof CraftingScreen);
		film.record(8);
	}

	/**
	 * Glides the cursor to a slot of the open crafting table (0 the result, 1-9 the grid, 10-36 the
	 * inventory, 37-45 the hotbar) and clicks it, as a player would.
	 */
	private void click(int slot, int button) {
		double[] to = film.ctx.computeOnClient(mc -> slotPixel(mc, slot));
		double[] from = film.ctx.computeOnClient(mc -> new double[] { mc.mouseHandler.xpos(), mc.mouseHandler.ypos() });
		int steps = 5 + film.jitter.nextInt(3);
		for (int i = 1; i <= steps; i++) {
			double t = Film.ease(i / (double) steps);
			film.ctx.getInput().setCursorPos(from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t);
			film.record(1);
		}
		// The click itself goes through the game's own container click (what a mouse click sends):
		// the test's simulated mouse presses moved the cursor but picked nothing up in the scout
		// (2026-10-21), with the game window in the background.
		film.ctx.runOnClient(mc -> mc.gameMode.handleContainerInput(((CraftingScreen) mc.gui.screen()).getMenu().containerId, slot,
				button, ContainerInput.PICKUP, mc.player));
		film.log(button == LEFT ? "click" : "right_click", Integer.toString(slot));
		film.record(2 + film.jitter.nextInt(3));
	}

	/** A slot's middle in window pixels: the screen centres its 176x166 panel; slots are 16 across. */
	private static double[] slotPixel(Minecraft mc, int slot) {
		Film.check(mc.gui.screen() instanceof CraftingScreen, "the crafting table is open");
		CraftingScreen screen = (CraftingScreen) mc.gui.screen();
		var s = screen.getMenu().getSlot(slot);
		double scale = mc.getWindow().getGuiScale();
		double left = (screen.width - 176) / 2;
		double top = (screen.height - 166) / 2;
		return new double[] { (left + s.x + 8) * scale, (top + s.y + 8) * scale };
	}

	private void closeScreen() {
		film.ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
		film.normalGui();
		film.record(10);
	}

	private int count(Item item) {
		return film.server.computeOnServer(server -> {
			var inv = player(server).getInventory();
			int n = 0;
			for (int i = 0; i < inv.getContainerSize(); i++) {
				if (inv.getItem(i).is(item)) {
					n += inv.getItem(i).getCount();
				}
			}
			return n;
		});
	}

	// ------------------------------------------------------------------ the gusts

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
}
