package com.gabbymaybelle.summiteer.zones;

import com.gabbymaybelle.summiteer.Config;
import com.gabbymaybelle.summiteer.Summiteer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * Which {@link Zone} a place is in. Nothing here uses a fixed height: the bands are measured from
 * the world's sea level, and "mountain" means a biome in vanilla's {@code #minecraft:is_mountain}
 * or the shared {@code #c:is_mountain} tag, which terrain mods (Terralith, Tectonic's biomes...)
 * fill in for their own mountains. Peak biomes ({@code #summiteer:above_treeline}: vanilla's three
 * peaks and the shared {@code #c:is_mountain/peak}) are above the trees by definition, so they count
 * as high ground at any height: a low range of jagged peaks still gets its weather. Summit height
 * counts in any biome, so a peak whose biome isn't tagged still gets its summit.
 */
public final class Zones {
	public static final TagKey<Biome> C_IS_MOUNTAIN = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_mountain"));
	public static final TagKey<Biome> ABOVE_TREELINE = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Summiteer.MOD_ID, "above_treeline"));

	private Zones() {
	}

	/** The zone at a place in the world. Only the Overworld has mountains' weather. */
	public static Zone at(Level level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD) {
			return Zone.LOW;
		}
		Holder<Biome> biome = level.getBiome(pos);
		return band(isMountain(biome), isPeak(biome), pos.getY(), level.getSeaLevel(), Config.highAboveSeaLevel, Config.summitAboveSeaLevel);
	}

	public static boolean isMountain(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(C_IS_MOUNTAIN);
	}

	/** A biome that is above the trees wherever it is, so high ground at any height. */
	public static boolean isPeak(Holder<Biome> biome) {
		return biome.is(ABOVE_TREELINE);
	}

	/**
	 * The rule itself, kept free of the game so it can be unit tested: summit height counts
	 * anywhere; peaks are high at any height; other mountains are high above the treeline.
	 */
	public static Zone band(boolean mountain, boolean peak, int y, int seaLevel, int highAboveSeaLevel, int summitAboveSeaLevel) {
		int rise = y - seaLevel;
		if (rise >= summitAboveSeaLevel) {
			return Zone.SUMMIT;
		}
		if (peak || mountain && rise >= highAboveSeaLevel) {
			return Zone.HIGH;
		}
		return Zone.LOW;
	}
}
