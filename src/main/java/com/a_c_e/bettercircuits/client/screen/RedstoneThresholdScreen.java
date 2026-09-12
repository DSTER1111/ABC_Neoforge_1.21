package com.a_c_e.bettercircuits.client.screen;

import com.a_c_e.bettercircuits.BetterCircuits;
import com.a_c_e.bettercircuits.block.entity.RedstoneThresholdBlockEntity;
import com.a_c_e.bettercircuits.block.menu.RedstoneThresholdMenu;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import javax.annotation.Nullable;
import java.util.List;

//15 threshold-value segments in a single row, alternating vanilla's own real block/redstone_dust_line0/line1
//sprites (starting with line0) - the two textures an actual redstone wire alternates between along its own
//length to avoid a repetitive look, reused here for the same reason across a row of 15 tiles - each blitted at
//its native 16x16 with a per-value color tint, rather than a flat GuiGraphics.fill, per the user's own request
//that this look like actual redstone dust.
//
//Rotated 90 degrees: decompiling redstone_dust_side.json (the model these textures back in-world) shows the
//texture's own V axis (top-to-bottom in the PNG) maps to the wire's LENGTHWISE direction (away from the block's
//center), with U (left-to-right) as the wire's width - i.e. the art itself "runs" vertically. Our own segments
//sit in a horizontal row, so the sprite is rotated 90 degrees (via the pose stack, around each segment's own
//center) so the line reads as running left-to-right, forming what looks like one continuous wire.
//
//The segments aren't Button widgets - a vanilla Button always paints its own textured background first, which
//would sit UNDER our own blit and still show through at the edges/on hover - so hit-testing is done manually in
//mouseClicked instead, dispatching through the same handleInventoryButtonClick plumbing a real Button would
//have used.
//
//Tint reproduces RedStoneWireBlock's own getColorForPower formula exactly (decompiled to confirm) - the same
//one real redstone dust uses at each power level - rather than a hand-picked palette, so "8" on this screen is
//colored exactly like an actual power-8 wire.
//
//The threshold value is selectable by dragging a slider handle (the user's own uploaded gui/slider.png) across
//the line, on top of it - a plain click-and-release still works too, since dragging is really just "the click
//that started the drag, continuously re-evaluated" (see mouseClicked/mouseDragged below): the value only
//updates when the drag actually crosses into a different segment's own column, not on every pixel of mouse
//movement, so it doesn't spam handleInventoryButtonClick. Once a drag has started, only the mouse's X matters
//(clamped to the track's own column range) - Y is intentionally NOT re-checked past the initial click, since a
//real hand doesn't hold a perfectly steady Y while dragging sideways.
//
//No separate mode buttons - clicking the slider itself (its own real art bounds, not just anywhere on the
//track) WITHOUT dragging, or scrolling over it, cycles the activation type (LESS -> GREATER -> EQUAL -> repeat)
//instead, per the user's own "integrate the buttons into the slider" request - a click that turns into a drag
//only ever adjusts the value, never the type. The current type is drawn as a small centered glyph directly on
//the slider so it travels with it.
public class RedstoneThresholdScreen extends AbstractContainerScreen<RedstoneThresholdMenu> {
    private static final ResourceLocation DUST_LINE_0 = ResourceLocation.withDefaultNamespace("block/redstone_dust_line0");
    private static final ResourceLocation DUST_LINE_1 = ResourceLocation.withDefaultNamespace("block/redstone_dust_line1");
    private static final ResourceLocation SLIDER = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "textures/gui/slider.png");
    //The uploaded slider.png's own visible art doesn't fill its full 16x16 canvas - read its actual alpha
    //channel to confirm: opaque pixels only span x=4-11 (8px wide), full height. The hover/click outline (and
    //the "is this click ON the slider, for mode-cycling purposes" hit test) should hug that real art, not the
    //canvas it's padded inside, so these hardcode the measured offset/width rather than assuming the full
    //texture bounds.
    private static final int SLIDER_VISIBLE_X = 4;
    private static final int SLIDER_VISIBLE_WIDTH = 8;

    private static final int PANEL_FILL = 0xFFC6C6C6;
    private static final int PANEL_BORDER = 0xFF000000;
    private static final int PANEL_HIGHLIGHT = 0xFFFFFFFF;
    private static final int PANEL_SHADOW = 0xFF555555;

    private static final int IMAGE_WIDTH = 256;
    private static final int IMAGE_HEIGHT = 40;
    private static final int LEFT_MARGIN = 8;
    private static final int RIGHT_MARGIN = 8;
    private static final int SEGMENT_SIZE = 16;
    private static final int TRACK_WIDTH = (RedstoneThresholdBlockEntity.MAX_THRESHOLD - RedstoneThresholdBlockEntity.MIN_THRESHOLD + 1) * SEGMENT_SIZE;
    //Bottom-aligned against the panel's own 1px border (with an extra 8px lifted off that, per the user's own
    //adjustment), so the top band stays clear for the title (top-left, vanilla-standard position) and the value
    //readout (top-right) without either one colliding with the line/slider row underneath.
    private static final int SEGMENT_ROW_Y = IMAGE_HEIGHT - SEGMENT_SIZE - 1 - 8 + 3;

    private static final List<Component> MODE_LABELS = List.of(Component.literal("<"), Component.literal("="), Component.literal(">"));
    private static final List<String> MODE_READOUT_KEYS = List.of(
            "gui.better_circuits.redstone_threshold.less_than",
            "gui.better_circuits.redstone_threshold.equals",
            "gui.better_circuits.redstone_threshold.greater_than");

    private boolean dragging;
    private boolean pressStartedOnSlider;
    private boolean movedDuringPress;
    private int lastSentValue = -1;

    public RedstoneThresholdScreen(RedstoneThresholdMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = IMAGE_WIDTH;
        this.imageHeight = IMAGE_HEIGHT;
    }

    //LESS -> GREATER -> EQUAL -> LESS, per the user's own explicit cycle order (not the natural MODE_LESS/
    //MODE_EQUAL/MODE_GREATER field ordering, which only reflects ContainerData's own encoding).
    private static int nextMode(int mode) {
        return switch (mode) {
            case RedstoneThresholdBlockEntity.MODE_LESS -> RedstoneThresholdBlockEntity.MODE_GREATER;
            case RedstoneThresholdBlockEntity.MODE_GREATER -> RedstoneThresholdBlockEntity.MODE_EQUAL;
            default -> RedstoneThresholdBlockEntity.MODE_LESS;
        };
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL_BORDER);
        guiGraphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, PANEL_FILL);
        guiGraphics.fill(x + 1, y + 1, x + imageWidth - 1, y + 2, PANEL_HIGHLIGHT);
        guiGraphics.fill(x + 1, y + 1, x + 2, y + imageHeight - 1, PANEL_HIGHLIGHT);
        guiGraphics.fill(x + imageWidth - 2, y + 1, x + imageWidth - 1, y + imageHeight - 1, PANEL_SHADOW);
        guiGraphics.fill(x + 1, y + imageHeight - 2, x + imageWidth - 1, y + imageHeight - 1, PANEL_SHADOW);

        Component readout = Component.translatable(MODE_READOUT_KEYS.get(menu.getMode()), menu.getThreshold());
        guiGraphics.drawString(font, readout, x + imageWidth - RIGHT_MARGIN - font.width(readout), y + titleLabelY, 0x404040, false);

        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite line0 = atlas.apply(DUST_LINE_0);
        TextureAtlasSprite line1 = atlas.apply(DUST_LINE_1);
        for (int value = RedstoneThresholdBlockEntity.MIN_THRESHOLD; value <= RedstoneThresholdBlockEntity.MAX_THRESHOLD; value++) {
            int sx = x + segmentX(value);
            int sy = y + SEGMENT_ROW_Y;
            float[] tint = colorForPower(value);
            boolean even = (value - RedstoneThresholdBlockEntity.MIN_THRESHOLD) % 2 == 0;
            TextureAtlasSprite sprite = even ? line0 : line1;

            int cx = sx + SEGMENT_SIZE / 2;
            int cy = sy + SEGMENT_SIZE / 2;
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(cx, cy, 0);
            guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(90));
            guiGraphics.pose().translate(-SEGMENT_SIZE / 2.0, -SEGMENT_SIZE / 2.0, 0);
            guiGraphics.blit(0, 0, 0, SEGMENT_SIZE, SEGMENT_SIZE, sprite, tint[0], tint[1], tint[2], 1.0F);
            guiGraphics.pose().popPose();
        }

        int artX = x + artXOffsetForValue(menu.getThreshold());
        int sliderX = artX - SLIDER_VISIBLE_X;
        int sliderY = y + SEGMENT_ROW_Y;
        boolean sliderActive = dragging || isOverSliderArt(mouseX, mouseY);
        int outline = sliderActive ? PANEL_HIGHLIGHT : PANEL_BORDER;
        guiGraphics.fill(artX - 1, sliderY - 1, artX + SLIDER_VISIBLE_WIDTH + 1, sliderY, outline);
        guiGraphics.fill(artX - 1, sliderY + SEGMENT_SIZE, artX + SLIDER_VISIBLE_WIDTH + 1, sliderY + SEGMENT_SIZE + 1, outline);
        guiGraphics.fill(artX - 1, sliderY - 1, artX, sliderY + SEGMENT_SIZE + 1, outline);
        guiGraphics.fill(artX + SLIDER_VISIBLE_WIDTH, sliderY - 1, artX + SLIDER_VISIBLE_WIDTH + 1, sliderY + SEGMENT_SIZE + 1, outline);
        guiGraphics.blit(SLIDER, sliderX, sliderY, 0, 0.0F, 0.0F, SEGMENT_SIZE, SEGMENT_SIZE, SEGMENT_SIZE, SEGMENT_SIZE);

        String modeText = MODE_LABELS.get(menu.getMode()).getString();
        int textX = artX + SLIDER_VISIBLE_WIDTH / 2 - font.width(modeText) / 2;
        int textY = sliderY + SEGMENT_SIZE / 2 - font.lineHeight / 2;
        guiGraphics.drawString(font, modeText, textX, textY, 0xFFFFFF, true);
    }

    //Where the slider's own real ART (not the sprite's raw origin) should sit for a given value, spanning the
    //FULL track edge-to-edge - value=MIN lands the art's own left edge flush against the track's own left edge,
    //value=MAX flush against its right edge. Bug fix: the sprite has 4px of transparent padding on each side
    //(read its own alpha channel to confirm - see SLIDER_VISIBLE_X/WIDTH's own comment), so naively positioning
    //the sprite's origin at each segment's own left edge (the old formula) left that padding visibly exposed at
    //both ends instead of letting the art itself reach them - found by the user at value=MIN specifically, but
    //the same gap exists symmetrically at MAX too, hence fixing both ends via one continuous fraction rather
    //than patching MIN alone. Only the SLIDER GRAPHIC's position changes here - segmentX (which segment tile a
    //click/drag lands in) is unaffected and still exactly one 16px cell per value.
    private int artXOffsetForValue(int value) {
        double fraction = (value - RedstoneThresholdBlockEntity.MIN_THRESHOLD)
                / (double) (RedstoneThresholdBlockEntity.MAX_THRESHOLD - RedstoneThresholdBlockEntity.MIN_THRESHOLD);
        int travel = TRACK_WIDTH - SLIDER_VISIBLE_WIDTH;
        return LEFT_MARGIN + (int) Math.round(fraction * travel);
    }

    private int segmentX(int value) {
        return LEFT_MARGIN + (value - RedstoneThresholdBlockEntity.MIN_THRESHOLD) * SEGMENT_SIZE;
    }

    //Same formula RedStoneWireBlock.COLORS itself computes (decompiled to confirm) - see this class's own
    //comment. Returned as 0-1 floats directly, matching GuiGraphics#blit's own tint parameter type.
    private static float[] colorForPower(int power) {
        float f = power / 15.0F;
        float r = f * 0.6F + (f > 0.0F ? 0.4F : 0.3F);
        float g = Mth.clamp(f * f * 0.7F - 0.5F, 0.0F, 1.0F);
        float b = Mth.clamp(f * f * 0.6F - 0.7F, 0.0F, 1.0F);
        return new float[]{r, g, b};
    }

    @Nullable
    private Integer hoveredValue(int mouseX, int mouseY) {
        int relY = mouseY - (topPos + SEGMENT_ROW_Y);
        if (relY < 0 || relY >= SEGMENT_SIZE) {
            return null;
        }
        for (int value = RedstoneThresholdBlockEntity.MIN_THRESHOLD; value <= RedstoneThresholdBlockEntity.MAX_THRESHOLD; value++) {
            int sx = leftPos + segmentX(value);
            if (mouseX >= sx && mouseX < sx + SEGMENT_SIZE) {
                return value;
            }
        }
        return null;
    }

    //True only over the slider's own real art bounds (not just anywhere on the track) - this is what gates
    //mode-cycling (both the click and the scroll-wheel path), matching the user's own "on the slider" wording.
    private boolean isOverSliderArt(int mouseX, int mouseY) {
        int artX = leftPos + artXOffsetForValue(menu.getThreshold());
        int sliderY = topPos + SEGMENT_ROW_Y;
        return mouseX >= artX && mouseX < artX + SLIDER_VISIBLE_WIDTH && mouseY >= sliderY && mouseY < sliderY + SEGMENT_SIZE;
    }

    //Clamps to the track's own column range regardless of Y - used while a drag is already in progress, where a
    //real hand won't hold a perfectly steady Y while sliding sideways (unlike the initial click, which still
    //goes through hoveredValue's own Y-bounded check, so a stray click elsewhere in the GUI can't start a drag).
    private int valueForX(int mouseX) {
        int relX = mouseX - (leftPos + LEFT_MARGIN);
        int index = Mth.clamp(relX / SEGMENT_SIZE, 0, RedstoneThresholdBlockEntity.MAX_THRESHOLD - RedstoneThresholdBlockEntity.MIN_THRESHOLD);
        return RedstoneThresholdBlockEntity.MIN_THRESHOLD + index;
    }

    //Only actually dispatches when the value differs from the last one sent - mouseDragged fires every frame the
    //mouse moves, even by a single pixel within the same segment's own column, and there's no reason to send a
    //fresh handleInventoryButtonClick (a real network packet in multiplayer) for a drag that hasn't crossed into
    //a new value yet.
    private void setValue(int value) {
        if (value == lastSentValue) {
            return;
        }
        lastSentValue = value;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RedstoneThresholdMenu.VALUE_BUTTON_BASE + value - RedstoneThresholdBlockEntity.MIN_THRESHOLD);
    }

    private void cycleMode() {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RedstoneThresholdMenu.MODE_BUTTON_BASE + nextMode(menu.getMode()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Integer value = hoveredValue((int) mouseX, (int) mouseY);
        if (value != null) {
            dragging = true;
            movedDuringPress = false;
            pressStartedOnSlider = isOverSliderArt((int) mouseX, (int) mouseY);
            lastSentValue = -1;
            setValue(value);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            movedDuringPress = true;
            setValue(valueForX((int) mouseX));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && pressStartedOnSlider && !movedDuringPress) {
            cycleMode();
        }
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0 && isOverSliderArt((int) mouseX, (int) mouseY)) {
            cycleMode();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        Integer hovered = hoveredValue(mouseX, mouseY);
        if (hovered != null) {
            guiGraphics.renderTooltip(font, Component.literal(String.valueOf(hovered)), mouseX, mouseY);
        }
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
