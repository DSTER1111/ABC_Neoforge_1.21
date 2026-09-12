package com.a_c_e.bettercircuits.client.renderer;

import com.a_c_e.bettercircuits.BetterCircuits;
import com.a_c_e.bettercircuits.block.TimerBlock;
import com.a_c_e.bettercircuits.block.entity.TimerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

//Renders the torch+pointer+tip assembly (see BCBlockStateProvider#timerSpinner) rotating continuously - this
//geometry can't be a normal blockstate model (a blockstate can't express continuous rotation, only a fixed set
//of discrete variants), so it's baked as a standalone model (registered via ModelEvent.RegisterAdditional in
//BetterCircuits, referenced only here) and rotated by hand each frame around the block's own center, the same
//"static baked model + PoseStack rotation" approach vanilla itself uses for e.g. the Conduit's spinning shell.
public class TimerBlockEntityRenderer implements BlockEntityRenderer<TimerBlockEntity> {
    private static final ModelResourceLocation SPINNER_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/timer_spinner"));

    public TimerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TimerBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof TimerBlock)) {
            return;
        }

        //Bug fix: this used to reuse the exact SOUTH/WEST/NORTH/EAST -> 0/90/180/270 table every other gate's
        //blockstate builder uses for its own model geometry - but the spinner is a separately-baked standalone
        //model (see this class's own comment), rotated by hand via Axis.YP.rotationDegrees below rather than
        //through Minecraft's own blockstate-model y-rotation pipeline, and the two don't share the same
        //rotation handedness for a 90-degree turn (confirmed by the user's own in-game observation: WEST/EAST
        //placement pointed the tip exactly backwards, while SOUTH/NORTH - both 180-degree-symmetric turns -
        //happened to still line up). WEST and EAST are swapped from the "shared" table above to compensate.
        Direction facing = state.getValue(TimerBlock.FACING);
        float staticYRot = switch (facing) {
            case SOUTH -> 0f;
            case WEST -> 270f;
            case NORTH -> 180f;
            case EAST -> 90f;
            default -> 0f;
        };
        int target = Math.max(1, be.getTarget());
        //Negated - a positive angle around Axis.YP spins counter-clockwise as viewed from above in this
        //renderer's coordinate space, confirmed backwards by the user's own in-game observation; the static
        //per-FACING term above is untouched since that's placement orientation, not spin direction.
        float angle = staticYRot - 360f * be.getProgress() / target;

        BakedModel model = Minecraft.getInstance().getModelManager().getModel(SPINNER_MODEL);

        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        poseStack.translate(-0.5, 0, -0.5);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(), buffer.getBuffer(RenderType.cutout()), state, model,
                1f, 1f, 1f, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
