package me.myogoo.appliedterminalmodules.item;

import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.menu.WorkstationKind;
import me.myogoo.appliedterminalmodules.menu.WorkstationModuleProvider;
import net.minecraft.world.item.ItemStack;

public final class WorkstationModuleItem extends ModuleItem {
    private final WorkstationKind kind;

    public WorkstationModuleItem(WorkstationKind kind) {
        this.kind = kind;
    }

    @Override
    public void onTerminalTick(MEStorageMenu menu, ItemStack stack) {
        WorkstationModuleProvider.refresh(menu, kind);
    }

    @Override
    public void onTerminalClose(MEStorageMenu menu, ItemStack stack) {
        WorkstationModuleProvider.returnInputs(menu, kind);
    }
}
