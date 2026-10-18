package com.gabbymaybelle.summiteer.weather;

import com.gabbymaybelle.summiteer.Config;
import com.gabbymaybelle.summiteer.zones.Zone;
import com.gabbymaybelle.summiteer.zones.Zones;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wind and cold for players up high. Nothing scans the world: each player is looked at once a
 * second (which zone, is it snowing on them, are they sheltered, is a fire near), and in between
 * only the gusts already due and the freezing of players already cold are handled. Only the
 * Overworld has mountain weather (see {@link Zones#at}).
 */
public final class Weather {
	/** A gust on its way to one player: when its warning started and when it hits. */
	private record Gust(long warnAt, long hitAt) {
	}

	private static final Map<UUID, Gust> GUSTS = new HashMap<>();
	private static final Set<UUID> FREEZING = new HashSet<>();

	/** How far around a player a fire or lit campfire keeps the cold off. */
	private static final int FIRE_REACH = 3;

	private Weather() {
	}

	public static void serverTick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		Set<UUID> present = new HashSet<>();
		for (ServerPlayer player : level.players()) {
			UUID id = player.getUUID();
			present.add(id);
			if (player.isSpectator() || player.isCreative()) {
				GUSTS.remove(id);
				FREEZING.remove(id);
				continue;
			}
			// Spread the once-a-second look over the second, so many players don't all land on one tick.
			if (Math.floorMod(now + id.hashCode(), 20) == 0) {
				look(level, player, now);
			}
			tickGust(level, player, now);
			if (FREEZING.contains(id)) {
				freeze(player, now);
			}
		}
		// Forget players who left or changed dimension.
		GUSTS.keySet().retainAll(present);
		FREEZING.retainAll(present);
	}

	/** The once-a-second look at one player: schedules or calls off gusts, starts or stops the cold. */
	private static void look(ServerLevel level, ServerPlayer player, long now) {
		UUID id = player.getUUID();
		BlockPos pos = player.blockPosition();
		Zone zone = Zones.at(level, pos);
		boolean sheltered = isSheltered(level, player);
		boolean storm = isSnowingAt(level, pos);

		if (!Config.wind || zone == Zone.LOW || sheltered) {
			GUSTS.remove(id);
		} else if (!GUSTS.containsKey(id)) {
			int wait = Wind.ticksUntilNextGust(storm, level.getRandom().nextDouble());
			long hitAt = now + Math.max(wait, Wind.WARNING_TICKS);
			GUSTS.put(id, new Gust(hitAt - Wind.WARNING_TICKS, hitAt));
		}

		boolean cold = Config.cold
				&& Cold.isCold(zone, storm && !sheltered, level.isDarkOutside())
				&& !nearFire(level, pos);
		if (cold) {
			FREEZING.add(id);
		} else {
			FREEZING.remove(id);
		}
	}

	private static void tickGust(ServerLevel level, ServerPlayer player, long now) {
		Gust gust = GUSTS.get(player.getUUID());
		if (gust == null || now < gust.warnAt()) {
			return;
		}
		double angle = Wind.direction(now);
		double dx = Math.cos(angle);
		double dz = Math.sin(angle);
		if (now < gust.hitAt()) {
			warn(level, player, now - gust.warnAt(), dx, dz);
			return;
		}
		GUSTS.remove(player.getUUID());
		// Check again at the moment it hits: ducking under a ledge or crouching at the last second counts.
		if (isSheltered(level, player)) {
			return;
		}
		Zone zone = Zones.at(level, player.blockPosition());
		double push = Wind.strength(zone, isSnowingAt(level, player.blockPosition()), player.isShiftKeyDown(), player.isFallFlying());
		if (push > 0) {
			player.push(dx * push, 0.05, dz * push);
			// Players move themselves, so the client must be told about the push.
			player.syncVelocity = true;
		}
	}

	/** The warning: a whoosh from upwind as it starts, then snow streaming past for two seconds. */
	private static void warn(ServerLevel level, ServerPlayer player, long sinceWarn, double dx, double dz) {
		if (sinceWarn == 0) {
			level.playSound(null, player.getX() - dx * 4, player.getEyeY(), player.getZ() - dz * 4,
					SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, 0.5F, 1.4F);
		}
		if (sinceWarn % 4 == 0) {
			// Snowflakes from upwind, drifting the way the gust will push; only this player sees them.
			level.sendParticles(player, ParticleTypes.SNOWFLAKE, false, false,
					player.getX() - dx * 3, player.getEyeY(), player.getZ() - dz * 3,
					0, dx, 0.02, dz, 0.6);
		}
	}

	/**
	 * Adds freezing for a player in the cold. Vanilla thaws everyone out by 2 a tick when they
	 * aren't in powder snow, so this adds those 2 back every tick, plus the cold's own rate spread
	 * over the second. The frost on screen and the freeze damage are vanilla's.
	 */
	private static void freeze(ServerPlayer player, long now) {
		int perSecond = Cold.perSecond(0);
		int extra = perSecond == 0 ? 0 : (Math.floorMod(now, 20) < perSecond ? 1 : 0);
		int cap = player.getTicksRequiredToFreeze() + 20;
		player.setTicksFrozen(Math.min(cap, player.getTicksFrozen() + 2 + extra));
	}

	/** Under a roof of any kind: nothing between the player's head and the sky. */
	private static boolean isSheltered(ServerLevel level, ServerPlayer player) {
		return !level.canSeeSky(BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()));
	}

	/** A storm here that falls as snow (it rains below and snows up top). */
	static boolean isSnowingAt(ServerLevel level, BlockPos pos) {
		return level.isRaining() && level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel()) == Biome.Precipitation.SNOW;
	}

	private static boolean nearFire(ServerLevel level, BlockPos pos) {
		for (BlockPos near : BlockPos.betweenClosed(pos.offset(-FIRE_REACH, -1, -FIRE_REACH), pos.offset(FIRE_REACH, 2, FIRE_REACH))) {
			BlockState state = level.getBlockState(near);
			if (state.is(BlockTags.FIRE) || CampfireBlock.isLitCampfire(state)) {
				return true;
			}
		}
		return false;
	}
}
