package com.a_c_e.bettercircuits.client.renderer;

import com.a_c_e.bettercircuits.entity.FilteredHopperMinecartEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MinecartRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

//Draws the same item-frame-plus-filtered-item overlay as FilteredHopperBlockEntityRenderer, on top of the
//displayed hopper block MinecartRenderer already bakes inside the cart. Decompiled MinecartRenderer.render to
//confirm renderMinecartContents is called with the pose stack already in the same block-local (0,0,0)-(1,1,1)
//unit-cube space a BlockEntityRenderer gets (scale/translate/rotate already applied by the caller so the
//"contained block" renders as if placed at its own origin) - so the exact same transforms FilteredHopper-
//BlockEntityRenderer uses reproduce identically here, no adaptation needed.
public class FilteredHopperMinecartRenderer extends MinecartRenderer<FilteredHopperMinecartEntity> {
    private static final ModelResourceLocation FRAME_MODEL = ModelResourceLocation.vanilla("item_frame", "map=false");

    public FilteredHopperMinecartRenderer(EntityRendererProvider.Context context) {
        super(context, ModelLayers.HOPPER_MINECART);
    }

    @Override
    protected void renderMinecartContents(FilteredHopperMinecartEntity entity, float partialTick, BlockState state,
                                           PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.renderMinecartContents(entity, partialTick, state, poseStack, buffer, packedLight);

        BakedModel frameModel = Minecraft.getInstance().getModelManager().getModel(FRAME_MODEL);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90));
        poseStack.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(), buffer.getBuffer(RenderType.cutout()), state, frameModel,
                1f, 1f, 1f, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        ItemStack filter = entity.getFilter();
        if (filter.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5, 0.9375, 0.5);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90));
        poseStack.translate(0, 0, 0.0625);
        poseStack.scale(0.5F, 0.5F, 0.5F);
        Minecraft.getInstance().getItemRenderer().renderStatic(filter, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, entity.level(), 0);
        poseStack.popPose();
    }
}
