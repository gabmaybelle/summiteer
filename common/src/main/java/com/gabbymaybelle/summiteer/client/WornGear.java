package com.gabbymaybelle.summiteer.client;

import com.gabbymaybelle.summiteer.Summiteer;
import com.gabbymaybelle.summiteer.clothes.Clothes;
import com.gabbymaybelle.summiteer.gear.Gear;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.joml.Vector3f;

/**
 * Worn gear on the player: each of the goggles, crampons and gloves has a small worn model
 * ({@code models/item/worn/}) drawn on a body part, the way vanilla draws a pumpkin on a head.
 * 1.21.1's item models can't pick a model by where the item is drawn, so the worn models are loaded
 * as extra models by each loader and drawn here: Trinkets' renderer on Fabric, Curios' on NeoForge.
 * Also the colour of the dyed clothes in the hand.
 */
public final class WornGear {
	/** A body part of the humanoid model and the point on it, in blocks, where the gear goes. */
	public record Spot(Function<HumanoidModel<?>, ModelPart> part, Vector3f at) {
	}

	public static final ResourceLocation GOGGLES_MODEL = Summiteer.id("item/worn/snow_goggles");
	public static final ResourceLocation CRAMPONS_MODEL = Summiteer.id("item/worn/crampons");
	public static final ResourceLocation GLOVES_MODEL = Summiteer.id("item/worn/climbing_gloves");
	public static final List<ResourceLocation> MODELS = List.of(GOGGLES_MODEL, CRAMPONS_MODEL, GLOVES_MODEL);

	/** The middle of the head. */
	public static final List<Spot> HEAD = List.of(new Spot(model -> model.head, new Vector3f(0, -0.25F, 0)));
	/** The bottom of each leg (12 px long). */
	public static final List<Spot> FEET = List.of(
			new Spot(model -> model.leftLeg, new Vector3f(0, 0.75F, 0)),
			new Spot(model -> model.rightLeg, new Vector3f(0, 0.75F, 0)));
	/** The bottom of each arm (its middle is 1 px off the shoulder joint, toward the outside). */
	public static final List<Spot> HANDS = List.of(
			new Spot(model -> model.leftArm, new Vector3f(1 / 16F, 0.625F, 0)),
			new Spot(model -> model.rightArm, new Vector3f(-1 / 16F, 0.625F, 0)));

	private WornGear() {
	}

	/** The worn model's id for a piece of gear, or null. */
	public static ResourceLocation model(ItemStack stack) {
		if (stack.is(Gear.SNOW_GOGGLES.get())) {
			return GOGGLES_MODEL;
		}
		if (stack.is(Gear.CRAMPONS.get())) {
			return CRAMPONS_MODEL;
		}
		return stack.is(Clothes.CLIMBING_GLOVES.get()) ? GLOVES_MODEL : null;
	}

	/** Where a piece of gear goes on the body. */
	public static List<Spot> spots(ItemStack stack) {
		if (stack.is(Gear.SNOW_GOGGLES.get())) {
			return HEAD;
		}
		return stack.is(Gear.CRAMPONS.get()) ? FEET : HANDS;
	}

	/** Draws a worn model on each of its spots; the pose stack is the render layer's own. */
	public static void render(ItemStack stack, BakedModel model, HumanoidModel<?> body, PoseStack poseStack, MultiBufferSource buffers, int light) {
		for (Spot spot : spots(stack)) {
			poseStack.pushPose();
			spot.part().apply(body).translateAndRotate(poseStack);
			poseStack.translate(spot.at().x(), spot.at().y(), spot.at().z());
			// Turned the way vanilla turns a pumpkin worn on the head.
			poseStack.scale(-1, -1, 1);
			Minecraft.getInstance().getItemRenderer().render(stack, ItemDisplayContext.NONE, false, poseStack, buffers, light, OverlayTexture.NO_OVERLAY, model);
			poseStack.popPose();
		}
	}

	/** The tint of the dyeable clothes' first layer: the dye, or cream when undyed. */
	public static int tint(ItemStack stack, int layer) {
		return layer > 0 ? -1 : DyedItemColor.getOrDefault(stack, Clothes.UNDYED);
	}
}
