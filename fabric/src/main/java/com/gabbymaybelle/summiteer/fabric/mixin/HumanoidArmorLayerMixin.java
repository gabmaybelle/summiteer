package com.gabbymaybelle.summiteer.fabric.mixin;

import com.gabbymaybelle.summiteer.clothes.Clothes;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Undyed wool clothes are cream on the player, not leather's brown: vanilla falls back to
 * leather's colour for anything dyeable. (NeoForge asks the item instead, see its client class.)
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
	@ModifyArg(method = "renderArmorPiece", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/component/DyedItemColor;getOrDefault(Lnet/minecraft/world/item/ItemStack;I)I"), index = 1)
	private int summiteer$undyedWool(ItemStack stack, int fallback) {
		return Clothes.isWoolClothing(stack) ? Clothes.UNDYED : fallback;
	}
}
