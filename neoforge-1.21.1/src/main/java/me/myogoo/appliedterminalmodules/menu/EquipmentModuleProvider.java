package me.myogoo.appliedterminalmodules.menu;

import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.myotus.api.terminal.ITerminalModuleProvider;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

import java.util.List;

public final class EquipmentModuleProvider implements ITerminalModuleProvider {
    @Override
    public List<? extends Slot> createSlots(MEStorageMenu menu) {
        var player = menu.getPlayer();
        // Distinct vanilla slots share the live inventory without reusing Myotus' built-in delegates.
        var vanilla = new InventoryMenu(player.getInventory(), !player.level().isClientSide, player);
        return List.of(vanilla.getSlot(5), vanilla.getSlot(6), vanilla.getSlot(7), vanilla.getSlot(8),
                vanilla.getSlot(InventoryMenu.SHIELD_SLOT));
    }
}
