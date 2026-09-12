package com.a_c_e.bettercircuits.client.screen;

import com.a_c_e.bettercircuits.BetterCircuits;
import com.a_c_e.bettercircuits.block.entity.TimerBlockEntity;
import com.a_c_e.bettercircuits.block.menu.TimerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

//Redesigned to match RedstoneThresholdScreen's own layout exactly (same 256x40 panel, same slider mechanic) -
//see that class's own comment for the shared mechanics (drag-vs-click distinction, the slider drawn last so it
//sits on top). Track is 15 tiles of the user's own uploaded gui/timer_bar.png (a plain standalone GUI
//texture, not a block-atlas sprite the way Threshold's redstone_dust_line is - read its own alpha channel to
//confirm it already tiles edge-to-edge with no padding and needs no rotation, unlike the dust line) instead of
//tinted redstone dust, since a duration has no natural "power level" to tint by.
//
//Unlike Threshold's clean 1-15 value range, Timer's target spans up to 24000 ticks (or 72000 in seconds mode) -
//far too many distinct values for one-slider-position-per-value the way Threshold's 15 segments work. The
//slider instead drags CONTINUOUSLY along the track, its position a straight linear fraction between the current
//mode's own min/max (both always expressed in ticks - target is stored in ticks regardless of display mode,
//per TimerBlockEntity's own convention), rounded to the nearest whole second so the result always lands on a
//value that reads cleanly in ticks (a whole multiple of 20). The old 12 relative-delta buttons and the Min/Max
//buttons are gone entirely, replaced by this direct positioning - TimerMenu's own VALUE_BUTTON_BASE now sets an
//absolute tick count instead.
//
//The slider's own glyph is "T"/"S" (ticks/seconds) instead of Threshold's "<"/"="/">", toggled the same way
//(click-without-drag or scroll while over the slider's own real art bounds) - a straight binary toggle here
//rather than a 3-way cycle, reusing TimerMenu's existing MODE_BUTTON_ID unchanged from before this redesign.
//
//The corner readout is no longer a plain drawn string - the number itself is a real EditBox (valueBox) the
//player can click into and type an exact value, committed on Enter via the same VALUE_BUTTON_BASE mechanism
//the slider already uses. "ticks"/"seconds" stays static text drawn immediately after it (see updateReadout).
public class TimerScreen extends AbstractContainerScreen<TimerMenu> {
    private static final ResourceLocation TIMER_BAR = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "textures/gui/timer_bar.png");
    private static final ResourceLocation SLIDER = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "textures/gui/slider.png");
    //Same measured alpha-channel bounds as RedstoneThresholdScreen's own SLIDER_VISIBLE_X/WIDTH - it's the exact
    //same texture file. Bug fix: travelForValue below returns the ART's own intended offset directly (not the
    //sprite's raw origin) - artX = trackX + travelForValue(...), sliderX = artX - SLIDER_VISIBLE_X - so the art
    //itself reaches the true track edges at min/max. Getting this backwards (computing sliderX first, then
    //artX = sliderX + SLIDER_VISIBLE_X) was the original bug: it left the sprite's own transparent padding
    //exposed at both ends instead of letting the art reach them.
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
    private static final int TRACK_TILES = 15;
    private static final int TRACK_WIDTH = TRACK_TILES * SEGMENT_SIZE;
    //Same tuned offset as RedstoneThresholdScreen's own track row.
    private static final int TRACK_ROW_Y = IMAGE_HEIGHT - SEGMENT_SIZE - 1 - 8 + 3;

    //The corner readout used to be a plain drawn string ("X ticks"/"X seconds") - now the number itself is a
    //real EditBox the player can click into and type an exact value, with "ticks"/"seconds" staying static
    //text after it. Repositioned every frame (see render() below) since the suffix's own width changes between
    //the two modes, and the box sits immediately to its left.
    private static final int VALUE_BOX_WIDTH = 34;
    private static final int VALUE_BOX_GAP = 2;
    private static final int VALUE_BOX_MAX_DIGITS = 5;
    //Fine-tuning nudge against EditBox's own internal text padding, so the digits line up with the "ticks"/
    //"seconds" suffix's own baseline instead of sitting slightly high and to the left of it.
    private static final int VALUE_BOX_OFFSET_X = 5;
    private static final int VALUE_BOX_OFFSET_Y = 1;

    private boolean dragging;
    private boolean pressStartedOnSlider;
    private boolean movedDuringPress;
    private int lastSentValue = -1;
    private EditBox valueBox;
    private Component cachedSuffix = Component.empty();
    private int cachedSuffixX;

    public TimerScreen(TimerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = IMAGE_WIDTH;
        this.imageHeight = IMAGE_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        valueBox = new EditBox(font, 0, 0, VALUE_BOX_WIDTH, font.lineHeight + 2, Component.empty());
        valueBox.setBordered(false);
        valueBox.setTextColor(0x404040);
        valueBox.setTextShadow(false);
        valueBox.setMaxLength(VALUE_BOX_MAX_DIGITS);
        valueBox.setFilter(s -> s.matches("[0-9]*"));
        valueBox.setValue(Integer.toString(currentDisplayValue()));
        addRenderableWidget(valueBox);
    }

    private int currentDisplayValue() {
        return menu.isSecondsMode() ? menu.getTarget() / TimerBlockEntity.TICKS_PER_SECOND : menu.getTarget();
    }

    //Parses whatever the player actually typed, converts it from the current display unit back to ticks, and
    //clamps into the current mode's own valid range - same clamping the slider's own valueForX already applies,
    //so a typed value can never escape the block's real min/max regardless of what's entered. Silently ignores
    //empty/unparseable input rather than erroring, since that's just "nothing to commit yet".
    private void commitValue() {
        String text = valueBox.getValue();
        if (text.isEmpty()) {
            return;
        }
        int typed;
        try {
            typed = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return;
        }
        boolean seconds = menu.isSecondsMode();
        int ticks = seconds ? typed * TimerBlockEntity.TICKS_PER_SECOND : typed;
        setValue(Mth.clamp(ticks, minTicks(), maxTicks()));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (valueBox != null && valueBox.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            commitValue();
            valueBox.setFocused(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int minTicks() {
        return menu.isSecondsMode() ? TimerBlockEntity.MIN_TARGET_SECONDS_MODE : TimerBlockEntity.MIN_TARGET;
    }

    private int maxTicks() {
        return menu.isSecondsMode() ? TimerBlockEntity.MAX_TARGET_SECONDS_MODE : TimerBlockEntity.MAX_TARGET;
    }

    //How far the slider's own art has traveled along the track (0 = leftmost, TRACK_WIDTH-SLIDER_VISIBLE_WIDTH =
    //rightmost) for a given tick value - the inverse of valueForX below.
    private int travelForValue(int ticks) {
        int min = minTicks();
        int max = maxTicks();
        double fraction = Mth.clamp((ticks - min) / (double) (max - min), 0.0, 1.0);
        return (int) Math.round(fraction * (TRACK_WIDTH - SLIDER_VISIBLE_WIDTH));
    }

    //Inverse of travelForValue - screen-space mouseX to a tick value, rounded to the nearest whole second so a
    //drag always lands on a value that reads as a clean number of seconds (every mode boundary is already an
    //exact multiple of TICKS_PER_SECOND, so this never clips the valid range).
    private int valueForX(int mouseX) {
        int trackX = leftPos + LEFT_MARGIN;
        int travel = Mth.clamp(mouseX - trackX - SLIDER_VISIBLE_WIDTH / 2, 0, TRACK_WIDTH - SLIDER_VISIBLE_WIDTH);
        double fraction = (double) travel / (TRACK_WIDTH - SLIDER_VISIBLE_WIDTH);
        int min = minTicks();
        int max = maxTicks();
        int raw = min + (int) Math.round(fraction * (max - min));
        int rounded = Math.round(raw / (float) TimerBlockEntity.TICKS_PER_SECOND) * TimerBlockEntity.TICKS_PER_SECOND;
        return Mth.clamp(rounded, min, max);
    }

    private boolean isOverTrack(int mouseX, int mouseY) {
        int trackX = leftPos + LEFT_MARGIN;
        int trackY = topPos + TRACK_ROW_Y;
        return mouseX >= trackX && mouseX < trackX + TRACK_WIDTH && mouseY >= trackY && mouseY < trackY + SEGMENT_SIZE;
    }

    private boolean isOverSliderArt(int mouseX, int mouseY) {
        int artX = leftPos + LEFT_MARGIN + travelForValue(menu.getTarget());
        int sliderY = topPos + TRACK_ROW_Y;
        return mouseX >= artX && mouseX < artX + SLIDER_VISIBLE_WIDTH && mouseY >= sliderY && mouseY < sliderY + SEGMENT_SIZE;
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

        guiGraphics.drawString(font, cachedSuffix, cachedSuffixX, y + titleLabelY, 0x404040, false);

        int trackX = x + LEFT_MARGIN;
        int trackY = y + TRACK_ROW_Y;
        for (int i = 0; i < TRACK_TILES; i++) {
            guiGraphics.blit(TIMER_BAR, trackX + i * SEGMENT_SIZE, trackY, 0, 0.0F, 0.0F, SEGMENT_SIZE, SEGMENT_SIZE, SEGMENT_SIZE, SEGMENT_SIZE);
        }

        int artX = trackX + travelForValue(menu.getTarget());
        int sliderX = artX - SLIDER_VISIBLE_X;
        boolean sliderActive = dragging || isOverSliderArt(mouseX, mouseY);
        int outline = sliderActive ? PANEL_HIGHLIGHT : PANEL_BORDER;
        guiGraphics.fill(artX - 1, trackY - 1, artX + SLIDER_VISIBLE_WIDTH + 1, trackY, outline);
        guiGraphics.fill(artX - 1, trackY + SEGMENT_SIZE, artX + SLIDER_VISIBLE_WIDTH + 1, trackY + SEGMENT_SIZE + 1, outline);
        guiGraphics.fill(artX - 1, trackY - 1, artX, trackY + SEGMENT_SIZE + 1, outline);
        guiGraphics.fill(artX + SLIDER_VISIBLE_WIDTH, trackY - 1, artX + SLIDER_VISIBLE_WIDTH + 1, trackY + SEGMENT_SIZE + 1, outline);
        guiGraphics.blit(SLIDER, sliderX, trackY, 0, 0.0F, 0.0F, SEGMENT_SIZE, SEGMENT_SIZE, SEGMENT_SIZE, SEGMENT_SIZE);

        String modeText = menu.isSecondsMode() ? "S" : "T";
        int textX = artX + SLIDER_VISIBLE_WIDTH / 2 - font.width(modeText) / 2;
        int textY = trackY + SEGMENT_SIZE / 2 - font.lineHeight / 2;
        guiGraphics.drawString(font, modeText, textX, textY, 0xFFFFFF, true);
    }

    private void setValue(int value) {
        if (value == lastSentValue) {
            return;
        }
        lastSentValue = value;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TimerMenu.VALUE_BUTTON_BASE + value);
    }

    private void toggleMode() {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TimerMenu.MODE_BUTTON_ID);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isOverTrack((int) mouseX, (int) mouseY)) {
            dragging = true;
            movedDuringPress = false;
            pressStartedOnSlider = isOverSliderArt((int) mouseX, (int) mouseY);
            lastSentValue = -1;
            setValue(valueForX((int) mouseX));
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
            toggleMode();
        }
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0 && isOverSliderArt((int) mouseX, (int) mouseY)) {
            toggleMode();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Nullable
    private Integer hoveredValue(int mouseX, int mouseY) {
        return isOverTrack(mouseX, mouseY) ? valueForX(mouseX) : null;
    }

    //Recomputes the suffix text/position and the value box's own position + (when not focused) displayed text
    //BEFORE calling super.render() below - renderBg (which draws cachedSuffix) and the box's own widget render
    //both happen inside that same super call, so everything needs to be current by the time either one runs.
    //Skipped while the box is focused so an in-progress edit never gets stomped by the live synced value.
    private void updateReadout() {
        boolean seconds = menu.isSecondsMode();
        cachedSuffix = Component.translatable(seconds
                ? "gui.better_circuits.timer.seconds_suffix"
                : "gui.better_circuits.timer.ticks_suffix");
        cachedSuffixX = leftPos + imageWidth - RIGHT_MARGIN - font.width(cachedSuffix);
        valueBox.setX(cachedSuffixX - VALUE_BOX_GAP - VALUE_BOX_WIDTH + VALUE_BOX_OFFSET_X);
        valueBox.setY(topPos + titleLabelY - 1 + VALUE_BOX_OFFSET_Y);
        if (!valueBox.isFocused()) {
            valueBox.setValue(Integer.toString(currentDisplayValue()));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateReadout();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        Integer hovered = hoveredValue(mouseX, mouseY);
        if (hovered != null) {
            boolean seconds = menu.isSecondsMode();
            int displayValue = seconds ? hovered / TimerBlockEntity.TICKS_PER_SECOND : hovered;
            Component tooltip = Component.translatable(seconds
                    ? "gui.better_circuits.timer.seconds_readout"
                    : "gui.better_circuits.timer.ticks_readout", displayValue);
            guiGraphics.renderTooltip(font, tooltip, mouseX, mouseY);
        }
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
