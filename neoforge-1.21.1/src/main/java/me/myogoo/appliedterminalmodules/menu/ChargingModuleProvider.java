package me.myogoo.appliedterminalmodules.menu;

import java.util.List;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.energy.IEnergyService;
import appeng.core.AEConfig;
import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.init.ModItems;
import me.myogoo.myotus.api.MyotusAPI;
import me.myogoo.myotus.api.terminal.ITerminalModuleProvider;
import me.myogoo.myotus.api.terminal.TerminalModuleSlot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;

/** A single transient tool slot per viewer; only the connected ME grid supplies energy. */
public final class ChargingModuleProvider implements ITerminalModuleProvider {
    // The AE2 charger replenishes up to 800 AE from its grid per work call.
    private static final double MAX_AE_PER_TICK = 800;

    @Override
    public List<? extends Slot> createSlots(MEStorageMenu menu) {
        return List.of(new Slot(new SimpleContainer(1), 0, -10000, -10000) {
            @Override
            public boolean mayPlace(ItemStack stack) { return isChargeable(stack); }

            @Override
            public int getMaxStackSize() { return 1; }
        });
    }

    public static void tick(MEStorageMenu menu) {
        if (menu.isClientSide() || !menu.isValidMenu() || !menu.getPlayer().isAlive()
                || ((ServerPlayer) menu.getPlayer()).hasDisconnected()
                || menu.getPlayer().containerMenu != menu) return;
        if (menu.getHost() instanceof ItemMenuHost<?> host && !host.isValid()) return;
        var slot = slot(menu);
        if (slot == null || !slot.isInteractionAllowed(menu.getPlayer())
                || !menu.getHost().getLinkStatus().connected()) return;
        var node = menu.getGridNode();
        if (node == null || !node.isActive()) return;
        ItemStack stack = slot.getItem();
        if (!isChargeable(stack)) return;
        var energy = node.getGrid().getEnergyService();
        if (stack.getItem() instanceof IAEItemPowerStorage power) {
            chargeAE(stack, power, energy);
        } else {
            chargeFE(stack, energy);
        }
        slot.setChanged();
    }

    /** Removal/close returns the owned stack once, including full inventories and dead/disconnected players. */
    public static void returnItem(MEStorageMenu menu) {
        if (menu.isClientSide()) return;
        var slot = slot(menu);
        if (slot == null) return;
        var nativeSlot = slot.getDelegate();
        ItemStack stack = nativeSlot.container.removeItemNoUpdate(nativeSlot.getContainerSlot());
        if (stack.isEmpty()) return;
        var player = (ServerPlayer) menu.getPlayer();
        if (!player.isAlive() || player.hasDisconnected()) player.drop(stack, false);
        else menu.getPlayerInventory().placeItemBackInInventory(stack);
        nativeSlot.setChanged();
    }

    private static TerminalModuleSlot slot(MEStorageMenu menu) {
        var slots = MyotusAPI.terminalModules().slots(menu, ModItems.CHARGING_MODULE.get());
        return slots.isEmpty() ? null : (TerminalModuleSlot) slots.getFirst();
    }

    private static boolean isChargeable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof IAEItemPowerStorage power) {
            return power.getPowerFlow(stack).isAllowInsertion()
                    && Double.isFinite(power.getAEMaxPower(stack)) && power.getAEMaxPower(stack) > 0;
        }
        var power = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return power != null && power.canReceive() && power.getMaxEnergyStored() > 0;
    }

    private static void chargeAE(ItemStack stack, IAEItemPowerStorage power, IEnergyService energy) {
        double budget = Math.min(MAX_AE_PER_TICK,
                power.getChargeRate(stack) * AEConfig.instance().getChargerChargeRate());
        budget = Math.min(budget, power.getAEMaxPower(stack) - power.getAECurrentPower(stack));
        if (!Double.isFinite(budget) || budget <= 0) return;
        double accepted = budget - power.injectAEPower(stack, budget, Actionable.SIMULATE);
        if (!Double.isFinite(accepted) || accepted <= 0) return;
        double available = energy.extractAEPower(Math.min(budget, accepted), Actionable.SIMULATE, PowerMultiplier.ONE);
        if (available <= 0) return;
        double extracted = energy.extractAEPower(available, Actionable.MODULATE, PowerMultiplier.ONE);
        if (extracted <= 0) return;
        double remainder = power.injectAEPower(stack, extracted, Actionable.MODULATE);
        if (remainder > 0) energy.injectPower(Math.min(extracted, remainder), Actionable.MODULATE);
    }

    private static void chargeFE(ItemStack stack, IEnergyService energy) {
        var power = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (power == null || !power.canReceive() || power.getEnergyStored() >= power.getMaxEnergyStored()) return;
        double budget = Math.min(MAX_AE_PER_TICK,
                MAX_AE_PER_TICK * AEConfig.instance().getChargerChargeRate());
        double converted = PowerUnit.AE.convertTo(PowerUnit.FE, budget);
        if (!Double.isFinite(converted) || converted < 1) return;
        int limit = (int) Math.min(Integer.MAX_VALUE, converted);
        int accepted = Math.min(limit, power.receiveEnergy(limit, true));
        if (accepted <= 0) return;
        double available = energy.extractAEPower(PowerUnit.FE.convertTo(PowerUnit.AE, accepted),
                Actionable.SIMULATE, PowerMultiplier.ONE);
        // FE is integral: never withdraw power for a fraction that cannot reach the tool.
        int deliverable = (int) Math.min(accepted, PowerUnit.AE.convertTo(PowerUnit.FE, available));
        if (deliverable <= 0) return;
        double extracted = energy.extractAEPower(PowerUnit.FE.convertTo(PowerUnit.AE, deliverable),
                Actionable.MODULATE, PowerMultiplier.ONE);
        int supplied = (int) Math.min(deliverable, PowerUnit.AE.convertTo(PowerUnit.FE, extracted));
        int received = supplied > 0 ? Math.max(0, Math.min(supplied, power.receiveEnergy(supplied, false))) : 0;
        double refund = extracted - PowerUnit.FE.convertTo(PowerUnit.AE, received);
        if (refund > 0) energy.injectPower(refund, Actionable.MODULATE);
    }
}
