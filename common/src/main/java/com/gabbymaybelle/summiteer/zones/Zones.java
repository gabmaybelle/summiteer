package com.gabbymaybelle.summiteer.zones;

import com.gabbymaybelle.summiteer.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * Which {@link Zone} a place is in. Nothing here uses a fixed height: the bands are measured from
 * the world's sea level, and "mountain" means a biome in vanilla's {@code #minecraft:is_mountain}
 * or the shared {@code #c:is_mountain} tag, which terrain mods (Terralith, Tectonic's biomes...)
 * fill in for their own mountains. Summit height counts in any biome, so a peak whose biome isn't
 * tagged still gets its summit.
 */
public final class Zones {
	public static final TagKey<Biome> C_IS_MOUNTAIN = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_mountain"));

	private Zones() {
	}

	/** The zone at a place in the world. Only the Overworld has mountains' weather. */
	public static Zone at(Level level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD) {
			return Zone.LOW;
		}
		return band(isMountain(level.getBiome(pos)), pos.getY(), level.getSeaLevel(), Config.highAboveSeaLevel, Config.summitAboveSeaLevel);
	}

	public static boolean isMountain(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(C_IS_MOUNTAIN);
	}

	/**
	 * The rule itself, kept free of the game so it can be unit tested: summit height counts
	 * anywhere; above the treeline counts in the mountains only.
	 */
	public static Zone band(boolean mountain, int y, int seaLevel, int highAboveSeaLevel, int summitAboveSeaLevel) {
		int rise = y - seaLevel;
		if (rise >= summitAboveSeaLevel) {
			return Zone.SUMMIT;
		}
		if (mountain && rise >= highAboveSeaLevel) {
			return Zone.HIGH;
		}
		return Zone.LOW;
	}
}
