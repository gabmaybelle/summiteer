package com.gabbymaybelle.summiteer.neoforge.client;

import com.gabbymaybelle.summiteer.client.WornGear;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/** Draws worn gear on the player through Curios (see {@link WornGear}; Trinkets does it on Fabric). */
public final class WornGearRenderer implements ICurioRenderer {
	@Override
	public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext, PoseStack poseStack,
			RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount,
			float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
		if (parent.getModel() instanceof HumanoidModel<?> body) {
			WornGear.render(stack, Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.standalone(WornGear.model(stack))),
					body, poseStack, buffers, light);
		}
	}
}
