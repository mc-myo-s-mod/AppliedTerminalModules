package me.myogoo.appliedterminalmodules.compat.emi;

import java.util.List;

import appeng.menu.me.common.MEStorageMenu;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import me.myogoo.appliedterminalmodules.compat.recipeviewer.CraftingModuleTransfer;
import me.myogoo.appliedterminalmodules.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.jetbrains.annotations.Nullable;

/** Explicit module target for menus with competing handlers; uses the standard crafting layout's arrow gap. */
final class CraftingModuleEmiButton extends Widget {
    private static final Bounds BOUNDS = new Bounds(60, 0, 28, 16);
    private final EmiRecipe recipe;

    CraftingModuleEmiButton(EmiRecipe recipe) { this.recipe = recipe; }

    private @Nullable MEStorageMenu menu() {
        var screen = EmiApi.getHandledScreen();
        if (screen == null || !(screen.getMenu() instanceof MEStorageMenu menu)
                || !CraftingModuleTransfer.isActive(menu)) return null;
        return menu;
    }

    @Override
    public Bounds getBounds() { return menu() == null ? new Bounds(0, 0, 0, 0) : BOUNDS; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        var menu = menu();
        if (menu == null) return;
        var check = CraftingModuleTransfer.check(menu, recipe.getBackingRecipe());
        int color = !check.canTransfer() ? 0xff703838
                : !check.missingSlots().isEmpty() ? 0xffa87524 : 0xff555555;
        graphics.fill(BOUNDS.x(), BOUNDS.y(), BOUNDS.right(), BOUNDS.bottom(), color);
        graphics.renderItem(ModItems.CRAFTING_MODULE.get().getDefaultInstance(), BOUNDS.x(), BOUNDS.y());
        graphics.drawString(Minecraft.getInstance().font, "+", BOUNDS.x() + 18, BOUNDS.y() + 4,
                check.canTransfer() ? 0xffffffff : 0xffaaaaaa, false);
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
        var menu = menu();
        if (menu == null) return List.of();
        var check = CraftingModuleTransfer.check(menu, recipe.getBackingRecipe());
        return List.of(ClientTooltipComponent.create(CraftingModuleTransfer.text("transfer").getVisualOrderText()),
                ClientTooltipComponent.create(check.tooltip().getVisualOrderText()));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        var menu = menu();
        if (menu == null || button != 0 || !BOUNDS.contains(mouseX, mouseY)) return false;
        if (CraftingModuleTransfer.send(menu, recipe.getBackingRecipe())) {
            Minecraft.getInstance().setScreen(EmiApi.getHandledScreen());
        }
        return true;
    }
}
