package me.myogoo.appliedterminalmodules.compat.emi;

import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.BasicCellChestMenu;
import appeng.menu.me.items.CraftingTermMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.me.items.WirelessCraftingTermMenu;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;

@EmiEntrypoint
public final class CraftingModuleEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        var handler = new CraftingModuleEmiTransferHandler<MEStorageMenu>();
        registry.addRecipeHandler(MEStorageMenu.TYPE, handler);
        registry.addRecipeHandler(MEStorageMenu.WIRELESS_TYPE, handler);
        registry.addRecipeHandler(MEStorageMenu.PORTABLE_ITEM_CELL_TYPE, handler);
        registry.addRecipeHandler(MEStorageMenu.PORTABLE_FLUID_CELL_TYPE, handler);
        registry.addRecipeHandler(BasicCellChestMenu.TYPE, handler);
        registry.addRecipeHandler(CraftingTermMenu.TYPE, new CraftingModuleEmiTransferHandler<CraftingTermMenu>(false));
        registry.addRecipeHandler(WirelessCraftingTermMenu.TYPE,
                new CraftingModuleEmiTransferHandler<WirelessCraftingTermMenu>(false));
        registry.addRecipeHandler(PatternEncodingTermMenu.TYPE,
                new CraftingModuleEmiTransferHandler<PatternEncodingTermMenu>(false));
        // EMI's first matching handler wins; never replace AE2's native crafting/pattern handlers.
        // The public decorator supplies an explicitly targeted module button regardless of handler
        // precedence, including third-party MEStorageMenu subclasses without a registration here.
        registry.addRecipeDecorator(VanillaEmiRecipeCategories.CRAFTING, (recipe, widgets) -> {
            if (recipe instanceof EmiCraftingRecipe && widgets.getWidth() == 118 && widgets.getHeight() == 54) {
                widgets.add(new CraftingModuleEmiButton(recipe));
            }
        });
        registry.addRecipeDecorator(VanillaEmiRecipeCategories.STONECUTTING, (recipe, widgets) -> {
            if (widgets.getWidth() == 76 && widgets.getHeight() == 18) {
                widgets.add(new CraftingModuleEmiTransferHandler.WorkstationButton(recipe, 29, 1));
            }
        });
        registry.addRecipeDecorator(VanillaEmiRecipeCategories.SMITHING, (recipe, widgets) -> {
            if (widgets.getWidth() == 112 && widgets.getHeight() == 18) {
                widgets.add(new CraftingModuleEmiTransferHandler.WorkstationButton(recipe, 66, 1));
            }
        });
        // AE2 already forwards AEBaseScreen.getExclusionZones(), including CraftingModulePanel.
    }
}
