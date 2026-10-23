package com.gabbymaybelle.summiteer.advancement;

import com.gabbymaybelle.summiteer.Summiteer;
import com.gabbymaybelle.summiteer.platform.Services;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The Summiteer advancements tab (picked with Gabby 2026-10-22): the advancements themselves are data
 * files in {@code data/summiteer/advancement/mountain/}; this fires the moments they wait for, through
 * one trigger ({@link MountainTrigger}). Placing a trail cairn uses vanilla's own placed-block trigger.
 * Firing an event a player already has costs nothing: the game skips finished criteria.
 */
public final class Advancements {
	/** Above the treeline: high ground in the mountains, or a summit. */
	public static final String HIGH = "high";
	public static final String SUMMIT = "summit";
	/** On a summit in a whiteout. */
	public static final String WHITEOUT = "whiteout";
	/** Climbing a wall with the ice axe. */
	public static final String ICE_CLIMB = "ice_climb";
	/** All the mountain clothes and climbing gloves on: full warmth. */
	public static final String DRESSED = "dressed";
	/** Rope hung from an anchor. */
	public static final String ROPE = "rope";
	/** Slept in a tent above the treeline. */
	public static final String CAMP = "camp";
	/** A thrown ender pearl blown a few blocks off course. */
	public static final String PEARL = "pearl";

	/** Made at once (it needs nothing from the game) and handed to the registry as it is. */
	public static final MountainTrigger MOUNTAIN = new MountainTrigger();

	private Advancements() {
	}

	/** Registers the trigger; called once from {@link Summiteer#init}. */
	public static void init() {
		Services.PLATFORM.<CriterionTrigger<?>>register(Registries.TRIGGER_TYPE, Summiteer.id("mountain"), () -> MOUNTAIN);
	}

	/** Fires a moment for a player (server side; nothing for anyone else). */
	public static void fire(Player player, String event) {
		if (player instanceof ServerPlayer serverPlayer) {
			MOUNTAIN.trigger(serverPlayer, event);
		}
	}
}
