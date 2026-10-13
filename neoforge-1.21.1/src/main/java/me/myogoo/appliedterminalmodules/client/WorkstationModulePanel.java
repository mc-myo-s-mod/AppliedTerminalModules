package me.myogoo.appliedterminalmodules.client;

import java.util.List;
import java.util.function.Consumer;

import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.Tooltip;
import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.menu.WorkstationKind;
import me.myogoo.appliedterminalmodules.menu.WorkstationModuleProvider;
import me.myogoo.appliedterminalmodules.network.WorkstationPackets;
import me.myogoo.myotus.client.TerminalEquipmentPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;

/** Native workstation slots inside Myotus' shared draggable, animated panel. */
public final class WorkstationModulePanel extends TerminalEquipmentPanel {
    private final AEBaseScreen<?> screen;
    private final MEStorageMenu menu;
    private final WorkstationKind kind;
    private int recipePage;

    public WorkstationModulePanel(AEBaseScreen<?> screen, WorkstationKind kind,
            ModConfigSpec.ConfigValue<List<Integer>> position) {
        super((MEStorageMenu) screen.getMenu(), kind.semantic(), position);
        this.screen = screen;
        this.menu = (MEStorageMenu) screen.getMenu();
        this.kind = kind;
    }

    @Override
    public Rect2i getBounds() {
        return new Rect2i(getX(), getY(), kind == WorkstationKind.SMITHING ? 140 : 122,
                switch (kind) { case STONECUTTING -> 106; case ANVIL -> 87; case SMITHING -> 48; case GRINDSTONE -> 68; });
    }

    @Override protected int dragHandleHeight() { return 20; }
    @Override protected boolean scrolling() { return false; }
    @Override protected List<Slot> visibleSlots() { return getSlots(); }

    @Override
    protected void updateSlotPositions() {
        for (int i = 0; i < getSlots().size(); i++) {
            int x, y;
            switch (kind) {
                case STONECUTTING -> { x = i == 0 ? 6 : 98; y = 23; }
                case ANVIL -> { x = i == 0 ? 6 : i == 1 ? 42 : 98; y = 25; }
                case SMITHING -> { x = i < 3 ? 6 + i * 22 : 114; y = 23; }
                case GRINDSTONE -> { x = i < 2 ? 6 : 98; y = i < 2 ? 23 + i * 20 : 33; }
                default -> throw new IllegalStateException();
            }
            getSlots().get(i).x = getX() + x;
            getSlots().get(i).y = getY() + y;
        }
        recipePage = Math.min(recipePage, lastPage());
    }

    private Component title() {
        return Component.translatable("gui.appliedterminalmodules." + kind.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    protected void drawPanelBackground(GuiGraphics graphics) {
        ModulePanelBackground.draw(graphics, getBounds(), title());
        for (var slot : getSlots()) {
            if (slot.isActive()) Icon.SLOT_BACKGROUND.getBlitter().dest(slot.x - 1, slot.y - 1).blit(graphics);
        }
        Icon.ARROW_RIGHT.getBlitter().dest(getX() + (kind == WorkstationKind.SMITHING ? 83 : 70),
                getY() + (kind == WorkstationKind.GRINDSTONE ? 33 : 24)).blit(graphics);
        if (kind == WorkstationKind.ANVIL) {
            graphics.drawString(Minecraft.getInstance().font, "+", getX() + 29, getY() + 29, 0xff404040, false);
            Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter().dest(getX() + 5, getY() + 46, 110, 20).blit(graphics);
            graphics.drawCenteredString(Minecraft.getInstance().font, Component.translatable("gui.appliedterminalmodules.anvil.rename"),
                    getX() + 60, getY() + 52, getSlots().getFirst().hasItem() ? 0xffffffff : 0xffaaaaaa);
            int cost = WorkstationModuleProvider.anvilCost(menu);
            if (cost > 0) {
                var label = cost >= 40 && !menu.getPlayer().getAbilities().instabuild
                        ? Component.translatable("container.repair.expensive")
                        : Component.translatable("gui.appliedterminalmodules.anvil.cost", cost);
                int color = menu.getPlayer().getAbilities().instabuild || menu.getPlayer().experienceLevel >= cost
                        ? 0xff237323 : 0xffa02020;
                graphics.drawString(Minecraft.getInstance().font, label, getX() + 6, getY() + 72, color, false);
            }
        }
        if (kind == WorkstationKind.STONECUTTING) {
            var recipes = WorkstationModuleProvider.recipes(menu);
            var selected = WorkstationModuleProvider.selectedRecipe(menu);
            for (int cell = 0; cell < 12 && recipePage * 12 + cell < recipes.size(); cell++) {
                int x = getX() + 6 + cell % 6 * 18, y = getY() + 46 + cell / 6 * 18;
                Icon.SLOT_BACKGROUND.getBlitter().dest(x - 1, y - 1).blit(graphics);
                if (recipes.get(recipePage * 12 + cell).id().equals(selected)) {
                    graphics.renderOutline(x - 1, y - 1, 18, 18, 0xff7fcfff);
                }
            }
            if (lastPage() > 0) {
                Icon.ARROW_LEFT.getBlitter().dest(getX() + 6, getY() + 85).blit(graphics);
                Icon.ARROW_RIGHT.getBlitter().dest(getX() + 98, getY() + 85).blit(graphics);
                graphics.drawCenteredString(Minecraft.getInstance().font, (recipePage + 1) + "/" + (lastPage() + 1),
                        getX() + 61, getY() + 89, 0xff404040);
            }
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, Point mouse, float layerHeight,
            Consumer<Slot> renderSlot, Consumer<Slot> renderHighlight) {
        super.renderContent(graphics, mouse, layerHeight, renderSlot, renderHighlight);
        if (kind != WorkstationKind.STONECUTTING) return;
        var recipes = WorkstationModuleProvider.recipes(menu);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, 0, layerHeight * 0.1f);
            graphics.pose().scale(1, 1, layerHeight / 1000);
            for (int cell = 0; cell < 12 && recipePage * 12 + cell < recipes.size(); cell++) {
                var output = recipes.get(recipePage * 12 + cell).value().getResultItem(menu.getPlayer().registryAccess());
                int x = getX() + 6 + cell % 6 * 18, y = getY() + 46 + cell / 6 * 18;
                graphics.renderItem(output, x, y);
                graphics.renderItemDecorations(Minecraft.getInstance().font, output, x, y);
            }
            graphics.flush();
        } finally { graphics.pose().popPose(); }
    }

    @Override
    protected boolean onContentMouseDown(Point mouse, int button) {
        if (button == 0 && kind == WorkstationKind.STONECUTTING) {
            int index = recipeAt(mouse.getX(), mouse.getY());
            var recipes = WorkstationModuleProvider.recipes(menu);
            if (index >= 0 && index < recipes.size()) {
                PacketDistributor.sendToServer(new WorkstationPackets.SelectRecipe(menu.containerId, recipes.get(index).id()));
                return true;
            }
            if (mouse.getY() >= getY() + 85) {
                if (mouse.getX() < getX() + 24) recipePage = Math.max(0, recipePage - 1);
                if (mouse.getX() >= getX() + 98) recipePage = Math.min(lastPage(), recipePage + 1);
                return true;
            }
        }
        if (button == 0 && kind == WorkstationKind.ANVIL && getSlots().getFirst().hasItem()
                && mouse.getX() >= getX() + 5 && mouse.getX() < getX() + 115
                && mouse.getY() >= getY() + 46 && mouse.getY() < getY() + 66) {
            AnvilRenameScreen.open(screen);
            return true;
        }
        return super.onContentMouseDown(mouse, button);
    }

    @Override
    protected boolean onContentMouseWheel(Point mouse, double delta) {
        if (kind != WorkstationKind.STONECUTTING || delta == 0) return false;
        recipePage = Math.clamp(recipePage + (delta > 0 ? -1 : 1), 0, lastPage());
        return true;
    }

    @Override
    public Tooltip getTooltip(int mouseX, int mouseY) {
        if (isMoving()) return null;
        if (mouseY < getY() + 20) return new Tooltip(title());
        if (kind == WorkstationKind.STONECUTTING) {
            int index = recipeAt(mouseX, mouseY);
            var recipes = WorkstationModuleProvider.recipes(menu);
            if (index >= 0 && index < recipes.size()) {
                return new Tooltip(recipes.get(index).value().getResultItem(menu.getPlayer().registryAccess()).getHoverName());
            }
        }
        return super.getTooltip(mouseX, mouseY);
    }

    private int lastPage() {
        return kind == WorkstationKind.STONECUTTING
                ? Math.max(0, (WorkstationModuleProvider.recipes(menu).size() - 1) / 12) : 0;
    }

    private int recipeAt(int mouseX, int mouseY) {
        int x = mouseX - getX() - 6, y = mouseY - getY() - 46;
        return x >= 0 && x < 108 && y >= 0 && y < 36 ? recipePage * 12 + y / 18 * 6 + x / 18 : -1;
    }
}
