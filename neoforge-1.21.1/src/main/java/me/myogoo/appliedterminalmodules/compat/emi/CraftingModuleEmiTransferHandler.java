package me.myogoo.appliedterminalmodules.compat.emi;

import java.util.ArrayList;
import java.util.List;

import appeng.menu.me.common.MEStorageMenu;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import me.myogoo.appliedterminalmodules.compat.recipeviewer.CraftingModuleTransfer;
import me.myogoo.appliedterminalmodules.init.ModItems;
import me.myogoo.appliedterminalmodules.menu.CraftingRecipeTransfer;
import me.myogoo.appliedterminalmodules.menu.WorkstationKind;
import me.myogoo.myotus.api.MyotusAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.jetbrains.annotations.Nullable;

/** Transfers one recipe set to the installed module, leaving output collection to ordinary slot clicks. */
public final class CraftingModuleEmiTransferHandler<T extends MEStorageMenu> implements EmiRecipeHandler<T> {
    private final boolean includeCrafting;

    public CraftingModuleEmiTransferHandler() { this(true); }

    public CraftingModuleEmiTransferHandler(boolean includeCrafting) { this.includeCrafting = includeCrafting; }

    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<T> screen) {
        var menu = screen.getMenu();
        List<EmiStack> stacks = new ArrayList<>();
        if (includeCrafting && CraftingModuleTransfer.isActive(menu)
                || CraftingModuleTransfer.isActive(menu, WorkstationKind.STONECUTTING)
                || CraftingModuleTransfer.isActive(menu, WorkstationKind.SMITHING)) {
            for (var stack : CraftingModuleTransfer.availableStacks(menu)) stacks.add(EmiStack.of(stack));
        }
        if (includeCrafting && CraftingModuleTransfer.isActive(menu)) {
            var slots = MyotusAPI.terminalModules().slots(menu, ModItems.CRAFTING_MODULE.get());
            for (int i = 0; i < 9; i++) stacks.add(EmiStack.of(slots.get(i).getItem().copy()));
        }
        for (var kind : List.of(WorkstationKind.STONECUTTING, WorkstationKind.SMITHING)) {
            if (!CraftingModuleTransfer.isActive(menu, kind)) continue;
            var slots = MyotusAPI.terminalModules().slots(menu, kind.card());
            for (int i = 0; i < kind.inputCount(); i++) stacks.add(EmiStack.of(slots.get(i).getItem().copy()));
        }
        return new EmiPlayerInventory(stacks);
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        var screen = EmiApi.getHandledScreen();
        if (screen == null || !(screen.getMenu() instanceof MEStorageMenu menu) || !handlesRecipe(recipe)) return false;
        var kind = workstationKind(recipe);
        return kind != null ? CraftingModuleTransfer.isActive(menu, kind)
                : CraftingModuleTransfer.isActive(menu);
    }

    @Override
    public boolean alwaysDisplaySupport(EmiRecipe recipe) { return false; }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<T> context) {
        return handlesRecipe(recipe)
                && CraftingModuleTransfer.check(context.getScreenHandler(), recipe.getBackingRecipe()).canTransfer();
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<T> context) {
        // Amount and destination intentionally do not trigger output clicks, repeats or autocrafting.
        return handlesRecipe(recipe)
                && CraftingModuleTransfer.send(context.getScreenHandler(), recipe.getBackingRecipe());
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(EmiRecipe recipe, EmiCraftContext<T> context) {
        var check = CraftingModuleTransfer.check(context.getScreenHandler(), recipe.getBackingRecipe());
        return List.of(ClientTooltipComponent.create(check.tooltip().getVisualOrderText()));
    }

    private static @Nullable WorkstationKind workstationKind(EmiRecipe recipe) {
        var kind = CraftingRecipeTransfer.workstationKind(recipe.getBackingRecipe());
        return kind == WorkstationKind.STONECUTTING && recipe.getCategory().equals(VanillaEmiRecipeCategories.STONECUTTING)
                || kind == WorkstationKind.SMITHING && recipe.getCategory().equals(VanillaEmiRecipeCategories.SMITHING)
                        ? kind : null;
    }

    private boolean handlesRecipe(EmiRecipe recipe) {
        return workstationKind(recipe) != null || includeCrafting
                && recipe.getCategory().equals(VanillaEmiRecipeCategories.CRAFTING)
                && CraftingRecipeTransfer.workstationKind(recipe.getBackingRecipe()) == null;
    }

    /** Explicit target also works when AE2's pattern handler claims every recipe category first. */
    static final class WorkstationButton extends Widget {
        private final EmiRecipe recipe;
        private final Bounds bounds;

        WorkstationButton(EmiRecipe recipe, int x, int y) {
            this.recipe = recipe;
            bounds = new Bounds(x, y, 16, 16);
        }

        private @Nullable MEStorageMenu menu() {
            var screen = EmiApi.getHandledScreen();
            var kind = workstationKind(recipe);
            return kind != null && screen != null && screen.getMenu() instanceof MEStorageMenu menu
                    && CraftingModuleTransfer.isActive(menu, kind) ? menu : null;
        }

        @Override
        public Bounds getBounds() { return menu() == null ? new Bounds(0, 0, 0, 0) : bounds; }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            var menu = menu();
            if (menu == null) return;
            var check = CraftingModuleTransfer.check(menu, recipe.getBackingRecipe());
            int color = !check.canTransfer() ? 0xff703838 : !check.missingSlots().isEmpty() ? 0xffa87524 : 0xff555555;
            graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), color);
            graphics.renderItem(workstationKind(recipe).card().getDefaultInstance(), bounds.x(), bounds.y());
        }

        @Override
        public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
            var menu = menu();
            if (menu == null) return List.of();
            return List.of(ClientTooltipComponent.create(workstationKind(recipe).card().getDefaultInstance()
                            .getHoverName().getVisualOrderText()),
                    ClientTooltipComponent.create(CraftingModuleTransfer.check(menu, recipe.getBackingRecipe())
                            .tooltip().getVisualOrderText()));
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            var menu = menu();
            if (menu == null || button != 0 || !bounds.contains(mouseX, mouseY)) return false;
            if (CraftingModuleTransfer.send(menu, recipe.getBackingRecipe())) {
                Minecraft.getInstance().setScreen(EmiApi.getHandledScreen());
            }
            return true;
        }
    }
}
