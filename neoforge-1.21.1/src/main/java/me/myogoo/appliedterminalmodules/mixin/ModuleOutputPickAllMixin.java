package me.myogoo.appliedterminalmodules.mixin;

import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.AppliedTerminalModules;
import me.myogoo.myotus.api.terminal.TerminalModuleSlot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Workstation previews must not be collected by double-clicking an ordinary inventory slot. */
@Mixin(AbstractContainerMenu.class)
abstract class ModuleOutputPickAllMixin {
    @Inject(method = "canTakeItemForPickAll(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/inventory/Slot;)Z",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void appliedterminalmodules$excludeOutput(ItemStack carried, Slot slot, CallbackInfoReturnable<Boolean> result) {
        if ((Object) this instanceof MEStorageMenu menu && slot instanceof TerminalModuleSlot module
                && module.getMenu() == menu && slot.container instanceof ResultContainer
                && BuiltInRegistries.ITEM.getKey(module.getModuleCard()).getNamespace().equals(AppliedTerminalModules.MOD_ID)) {
            result.setReturnValue(false);
        }
    }
}
