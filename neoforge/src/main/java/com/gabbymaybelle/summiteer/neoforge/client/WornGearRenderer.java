package com.gabbymaybelle.summiteer.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Draws worn gear on the player: the item's worn model (its "none" display context, see
 * {@code assets/summiteer/items}) on a body part, the way vanilla draws a pumpkin on a head. The
 * same placement as Trinkets Updated's attach files on Fabric ({@code assets/summiteer/trinkets}).
 */
public final class WornGearRenderer implements ICurioRenderer {
	/** A body part of the humanoid model and the point on it, in blocks, where the gear goes. */
	public record Spot(Function<HumanoidModel<?>, ModelPart> part, Vector3f at) {
	}

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

	private final List<Spot> spots;

	public WornGearRenderer(List<Spot> spots) {
		this.spots = spots;
	}

	@Override
	public <S extends LivingEntityRenderState, M extends EntityModel<? super S>> void render(ItemStack stack, SlotContext slotContext,
			PoseStack poseStack, SubmitNodeCollector collector, int light, S state, RenderLayerParent<S, M> parent,
			EntityRendererProvider.Context context, float yRot, float xRot) {
		if (!(parent.getModel() instanceof HumanoidModel<?> model)) {
			return;
		}
		ItemStackRenderState item = new ItemStackRenderState();
		Minecraft.getInstance().getItemModelResolver().updateForLiving(item, stack, ItemDisplayContext.NONE, slotContext.entity());
		for (Spot spot : spots) {
			poseStack.pushPose();
			model.root().translateAndRotate(poseStack);
			spot.part().apply(model).translateAndRotate(poseStack);
			poseStack.translate(spot.at().x(), spot.at().y(), spot.at().z());
			// Turned the way vanilla turns a pumpkin worn on the head.
			poseStack.scale(-1, -1, 1);
			item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
	}
}
