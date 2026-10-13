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

public final class ChargingModulePanel extends TerminalEquipmentPanel {
    public ChargingModulePanel(MEStorageMenu menu) {
        super(menu, ModuleSemantics.CHARGING, ModulePanelPositions.CHARGING);
    }

    @Override
    public Rect2i getBounds() { return new Rect2i(getX(), getY(), 104, 47); }

    @Override
    protected int dragHandleHeight() { return 20; }

    @Override
    protected boolean scrolling() { return false; }

    @Override
    protected List<Slot> visibleSlots() { return getSlots(); }

    @Override
    protected void updateSlotPositions() {
        for (var slot : getSlots()) { slot.x = getX() + 44; slot.y = getY() + 23; }
    }

    @Override
    protected void drawPanelBackground(GuiGraphics graphics) {
        ModulePanelBackground.draw(graphics, getBounds(), Component.translatable("gui.appliedterminalmodules.charging"));
        for (var slot : getSlots()) {
            if (slot.isActive()) Icon.SLOT_BACKGROUND.getBlitter().dest(slot.x - 1, slot.y - 1).blit(graphics);
        }
    }
}
