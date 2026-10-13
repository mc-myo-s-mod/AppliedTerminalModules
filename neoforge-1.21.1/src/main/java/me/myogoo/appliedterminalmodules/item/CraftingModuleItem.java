package me.myogoo.appliedterminalmodules.item;

import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.menu.CraftingModuleProvider;
import net.minecraft.world.item.ItemStack;

public final class CraftingModuleItem extends ModuleItem {
    public CraftingModuleItem() {}

    @Override
    public void onTerminalTick(MEStorageMenu menu, ItemStack stack) {
        CraftingModuleProvider.refresh(menu);
    }

    @Override
    public void onTerminalClose(MEStorageMenu menu, ItemStack stack) {
        CraftingModuleProvider.returnIngredients(menu);
    }
}
