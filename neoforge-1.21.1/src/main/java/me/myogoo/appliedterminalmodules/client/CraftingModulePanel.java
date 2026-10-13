package me.myogoo.appliedterminalmodules.client;

import java.util.List;

import appeng.client.gui.Icon;
import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.menu.ModuleSemantics;
import me.myogoo.myotus.client.TerminalEquipmentPanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

/** Crafting layout using the same movement, snapping and slot layers as other terminal modules. */
public final class CraftingModulePanel extends TerminalEquipmentPanel {
    private static final int WIDTH = 104;
    private static final int HEIGHT = 84;

    public CraftingModulePanel(MEStorageMenu menu) {
        super(menu, ModuleSemantics.CRAFTING, ModulePanelPositions.CRAFTING);
    }

    @Override
    public Rect2i getBounds() {
        var bounds = super.getBounds();
        return new Rect2i(bounds.getX(), bounds.getY(), WIDTH, HEIGHT);
    }

    @Override
    protected int dragHandleHeight() { return 20; }

    @Override
    protected boolean scrolling() { return false; }

    @Override
    protected List<Slot> visibleSlots() { return getSlots(); }

    @Override
    protected void updateSlotPositions() {
        var bounds = getBounds();
        var slots = getSlots();
        for (int i = 0; i < slots.size(); i++) {
            slots.get(i).x = bounds.getX() + (i < 9 ? 6 + i % 3 * 18 : 80);
            slots.get(i).y = bounds.getY() + (i < 9 ? 23 + i / 3 * 18 : 41);
        }
    }

    @Override
    protected void drawPanelBackground(GuiGraphics graphics) {
        int left = getBounds().getX();
        int top = getBounds().getY();
        ModulePanelBackground.draw(graphics, getBounds(), Component.translatable("container.crafting"));
        for (Slot slot : getSlots()) {
            if (slot.isActive()) Icon.SLOT_BACKGROUND.getBlitter().dest(slot.x - 1, slot.y - 1).blit(graphics);
        }
        Icon.ARROW_RIGHT.getBlitter().dest(left + 62, top + 41).blit(graphics);
    }
}
