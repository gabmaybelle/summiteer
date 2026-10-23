package com.gabbymaybelle.summiteer.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * Summiteer's one advancement trigger, {@code summiteer:mountain}: a criterion names the moment it
 * waits for ({@code "event": "summit"} and so on, see {@link Advancements}), and the mod fires that
 * moment for a player when it happens.
 */
public class MountainTrigger extends SimpleCriterionTrigger<MountainTrigger.TriggerInstance> {
	@Override
	public Codec<TriggerInstance> codec() {
		return TriggerInstance.CODEC;
	}

	public void trigger(ServerPlayer player, String event) {
		trigger(player, instance -> instance.event().equals(event));
	}

	public record TriggerInstance(Optional<ContextAwarePredicate> player, String event) implements SimpleCriterionTrigger.SimpleInstance {
		public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
				EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
				Codec.STRING.fieldOf("event").forGetter(TriggerInstance::event))
				.apply(i, TriggerInstance::new));
	}
}
