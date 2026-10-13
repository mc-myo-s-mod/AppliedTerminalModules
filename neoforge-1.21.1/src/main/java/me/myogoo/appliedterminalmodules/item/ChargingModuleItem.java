package me.myogoo.appliedterminalmodules.item;

import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.menu.ChargingModuleProvider;
import net.minecraft.world.item.ItemStack;

public final class ChargingModuleItem extends ModuleItem {
    @Override
    public void onTerminalTick(MEStorageMenu menu, ItemStack stack) {
        ChargingModuleProvider.tick(menu);
    }

    @Override
    public void onTerminalClose(MEStorageMenu menu, ItemStack stack) {
        ChargingModuleProvider.returnItem(menu);
    }
}
