package com.a_c_e.bettercircuits.client.renderer;

import com.a_c_e.bettercircuits.block.entity.FilteredHopperBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

//Renders an item frame flush inside the block's own top (funnel) opening - always on that face regardless of
//the hopper's own FACING (the opening itself is always up - see FilteredHopperBlock's own class comment on why
//hit-testing uses Direction.UP unconditionally) - plus the currently-filtered item sitting inside it, if any.
//First renderer in this project to draw an ItemStack directly (grepped: no existing ItemRenderer.renderStatic
//usage anywhere else in this codebase) - TimerBlockEntityRenderer's own "look up a model, hand-rotate a
//PoseStack" technique is the closest precedent, reused here for both the frame and the item.
//
//The frame itself needs no dedicated art at all - it reuses vanilla's OWN real item frame model directly.
//Vanilla ships a purpose-built fake blockstate (assets/minecraft/blockstates/item_frame.json - there's no real
//Blocks.ITEM_FRAME, this file exists SOLELY so vanilla's own model-baking pass discovers and bakes
//block/item_frame under a queryable key) specifically for ItemFrameRenderer's own use - decompiling that class
//confirmed the exact key: ModelResourceLocation.vanilla("item_frame", "map=false"). Looking that same,
//already-baked model up here needs no RegisterAdditional registration of our own (unlike TimerBlockEntityRenderer's
//own timer_spinner model, which has no blockstate pointing to it and needs that explicit step) - it's already
//discoverable via the normal blockstate-scanning pipeline.
//
//Both the frame and the item use the same -90-degrees-around-X rotation (see the item's own history: vanilla's
//ItemFrameRenderer applies this exact rotation for a frame mounted so it's viewed by looking DOWN at it, which
//is exactly this block's own "inside the top opening" case) - worked out from the frame model's own real
//coordinates (template_item_frame.json: a 12x12 square centered in X/Z, flush against the local Z=16 face)
//confirms this reorients it to sit flush against the block's own Y=16 (top) face instead, matching the item's
//own already-correct position exactly (both derived from the same vanilla source).
//
//Bug fix: this is declared over HopperBlockEntity, NOT FilteredHopperBlockEntity, and registered against
//BlockEntityType.HOPPER (see BetterCircuits.ClientModEvents#onRegisterRenderers and BCBlockEntityTypes' own
//comment) - HopperBlockEntity's own constructor hardcodes vanilla's real BlockEntityType.HOPPER internally, so
//every FilteredHopperBlockEntity instance's getType() reports THAT, not any separate type this mod might
//register. The block entity renderer dispatcher looks up a renderer by that SAME getType(), so a renderer
//registered against a different, unused type was simply never found for any real instance at all. Registering
//against the real type means this now runs for EVERY hopper in the world, vanilla ones included, hence the
//explicit instanceof guard below - a plain vanilla hopper just returns immediately, rendering nothing extra, so
//it looks completely unchanged.
public class FilteredHopperBlockEntityRenderer implements BlockEntityRenderer<HopperBlockEntity> {
    private static final ModelResourceLocation FRAME_MODEL = ModelResourceLocation.vanilla("item_frame", "map=false");

    public FilteredHopperBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HopperBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!(be instanceof FilteredHopperBlockEntity filteredHopper)) {
            return;
        }

        //Frame - always drawn, even with nothing filtered, so the block reads as visually distinct from a
        //plain hopper on sight (per the user's own request).
        BakedModel frameModel = Minecraft.getInstance().getModelManager().getModel(FRAME_MODEL);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90));
        poseStack.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(), buffer.getBuffer(RenderType.cutout()), be.getBlockState(), frameModel,
                1f, 1f, 1f, packedLight, packedOverlay);
        poseStack.popPose();

        ItemStack filter = filteredHopper.getFilter();
        if (filter.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5, 0.9375, 0.5);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90));
        poseStack.translate(0, 0, 0.0625);
        poseStack.scale(0.5F, 0.5F, 0.5F);
        Minecraft.getInstance().getItemRenderer().renderStatic(filter, ItemDisplayContext.FIXED, packedLight, packedOverlay,
                poseStack, buffer, be.getLevel(), 0);
        poseStack.popPose();
    }
}
