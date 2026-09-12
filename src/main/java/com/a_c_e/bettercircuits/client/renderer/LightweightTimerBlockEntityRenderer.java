package com.a_c_e.bettercircuits.client.renderer;

import com.a_c_e.bettercircuits.BetterCircuits;
import com.a_c_e.bettercircuits.block.entity.LightweightTimerBlockEntity;
import com.a_c_e.bettercircuits.block.lightweight.LightweightTimerBlock;
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

//Reuses the EXACT SAME standalone "timer_spinner" model heavy TimerBlockEntityRenderer already renders (see
//that class's own comment for why this can't be a normal blockstate model) - only the ORIENTATION differs, so
//no separate spinner model asset exists for this variant.
//
//Heavy TimerBlockEntityRenderer only ever rotates around the vertical (world Y) axis, since SUPPORT is always
//DOWN there. This one additionally needs the SAME whole-block SUPPORT reorientation the blockstate model
//itself gets, applied dynamically via PoseStack since a blockstate can't express continuous spin.
//
//Every rotation convention this method relies on is verified directly from real source, not inferred: Axis.XP/
//Axis.YP.rotationDegrees (com.mojang.math.Axis) are confirmed, from Axis's own source, to be plain, un-negated
//`new Quaternionf().rotationX/Y(radians)` calls; JOML's Quaternionf.rotationX/Y (org.joml, extracted from the
//joml-1.10.5-sources jar) are confirmed to build the textbook standard right-hand-rule axis-angle quaternion,
//no hidden convention; PoseStack.mulPose(Quaternionf) is confirmed to right-multiply the accumulated pose
//matrix (pose = pose * R), meaning each successive mulPose call composes in the SAME left-to-right order as
//the calls themselves, matching rotateX/rotateY below exactly. Minecraft's own blockstate x/y model rotation
//(net.minecraft.client.resources.model.BlockModelRotation's constructor) is confirmed to build its rotation as
//Ry(-y) then Rx(-x) (both standard, both negated relative to the stated blockstate x/y values) via
//`new Quaternionf().rotateYXZ(-y*TO_RADIANS, -x*TO_RADIANS, 0)` - reproduced here as the render() method's
//Axis.YP(-supportRot[1]) then Axis.XP(-supportRot[0]) calls, sanity-checked to correctly send canonical DOWN
//to each of the 6 real SUPPORT directions.
//
//The LOCAL (FACING-driven) angle could NOT reuse BCBlockStateProvider's own localFacingRotation label directly
//- that label is only ever fed into rotateBoxXZ/faceRotation (a different rotation system operating on raw box
//coordinates and UV rects) or into Minecraft's OWN blockstate loader, never into Axis.YP.rotationDegrees
//directly, so there's no guarantee reusing its numeric output produces the same visual angle here. An earlier
//version of this method assumed Axis.YP was negated relative to Axis.XP based on apparent behavior against
//heavy Timer's own floor table - that assumption was WRONG (both are equally standard, per the source above);
//the actual explanation is that heavy TimerBlockEntityRenderer's own long-standing floor table has its own
//latent EAST/WEST mixup (it points the tip at input instead of output for those two facings specifically -
//confirmed by rederiving the correct table from the sources above; flagged to the user separately, left
//untouched here since it's a different, already-shipped block outside this task's scope). computeLocalAngle
//below is derived independently from the verified sources, not by copying either heavy's table or the shared
//datagen label: it brute-forces which of the 4 candidate angles actually lands the canonical tip on the true
//output direction (FACING.getOpposite()) under this renderer's own (now fully source-verified) transform.
public class LightweightTimerBlockEntityRenderer implements BlockEntityRenderer<LightweightTimerBlockEntity> {
    private static final ModelResourceLocation SPINNER_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/timer_spinner"));
    private static final int[] CANDIDATE_ANGLES = {0, 90, 180, 270};

    public LightweightTimerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LightweightTimerBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof LightweightTimerBlock)) {
            return;
        }

        Direction support = state.getValue(LightweightTimerBlock.SUPPORT);
        Direction facing = state.getValue(LightweightTimerBlock.FACING);
        int[] supportRot = supportRotation(support);
        int target = Math.max(1, be.getTarget());
        float localAngle = computeLocalAngle(support, facing) - 360f * be.getProgress() / target;

        BakedModel model = Minecraft.getInstance().getModelManager().getModel(SPINNER_MODEL);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-supportRot[1]));
        poseStack.mulPose(Axis.XP.rotationDegrees(-supportRot[0]));
        poseStack.mulPose(Axis.YP.rotationDegrees(localAngle));
        poseStack.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(), buffer.getBuffer(RenderType.cutout()), state, model,
                1f, 1f, 1f, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static int[] supportRotation(Direction support) {
        return switch (support) {
            case DOWN -> new int[]{0, 0};
            case UP -> new int[]{180, 0};
            case NORTH -> new int[]{90, 180};
            case SOUTH -> new int[]{90, 0};
            case EAST -> new int[]{90, 270};
            case WEST -> new int[]{90, 90};
        };
    }

    //Brute-forces which of the 4 candidate local angles, run through THIS renderer's own exact transform chain
    //(axisYP/axisXP below, matching the render() method's own calibrated Axis.YP/XP calls), lands the
    //canonical tip (-Z) on the true output direction (FACING.getOpposite()) - see this class's own comment for
    //why the shared datagen label can't be reused here.
    private static int computeLocalAngle(Direction support, Direction facing) {
        int[] supportRot = supportRotation(support);
        Direction output = facing.getOpposite();
        int[] target = {output.getStepX(), output.getStepY(), output.getStepZ()};
        for (int theta : CANDIDATE_ANGLES) {
            int[] v = axisYP(new int[]{0, 0, -1}, theta);
            v = axisXP(v, -supportRot[0]);
            v = axisYP(v, -supportRot[1]);
            if (v[0] == target[0] && v[1] == target[1] && v[2] == target[2]) {
                return theta;
            }
        }
        //Unreachable - one of the 4 candidates always lands exactly on target for any valid (support, facing).
        return 0;
    }

    //Axis.YP.rotationDegrees is confirmed (from Axis's own source) to be a plain, standard rotateY - no
    //negation.
    private static int[] axisYP(int[] v, int deg) {
        return rotateY(v, deg);
    }

    //Axis.XP.rotationDegrees is confirmed (from Axis's own source) to be a plain, standard rotateX - no
    //negation.
    private static int[] axisXP(int[] v, int deg) {
        return rotateX(v, deg);
    }

    private static int[] rotateX(int[] v, int deg) {
        int x = v[0], y = v[1], z = v[2];
        return switch (((deg % 360) + 360) % 360) {
            case 90 -> new int[]{x, -z, y};
            case 180 -> new int[]{x, -y, -z};
            case 270 -> new int[]{x, z, -y};
            default -> new int[]{x, y, z};
        };
    }

    private static int[] rotateY(int[] v, int deg) {
        int x = v[0], y = v[1], z = v[2];
        return switch (((deg % 360) + 360) % 360) {
            case 90 -> new int[]{z, y, -x};
            case 180 -> new int[]{-x, y, -z};
            case 270 -> new int[]{-z, y, x};
            default -> new int[]{x, y, z};
        };
    }
}
