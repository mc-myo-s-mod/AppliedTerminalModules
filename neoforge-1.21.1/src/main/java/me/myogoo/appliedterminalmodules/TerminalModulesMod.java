package me.myogoo.appliedterminalmodules;

import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.compat.curios.CuriosModuleProvider;
import me.myogoo.appliedterminalmodules.compat.accessories.AccessoriesModuleProvider;
import me.myogoo.appliedterminalmodules.data.ModuleDataGenerators;
import me.myogoo.appliedterminalmodules.init.ModItems;
import me.myogoo.appliedterminalmodules.menu.CraftingModuleProvider;
import me.myogoo.appliedterminalmodules.menu.ChargingModuleProvider;
import me.myogoo.appliedterminalmodules.menu.WorkstationKind;
import me.myogoo.appliedterminalmodules.menu.WorkstationModuleProvider;
import me.myogoo.appliedterminalmodules.menu.EquipmentModuleProvider;
import me.myogoo.appliedterminalmodules.menu.ModuleSemantics;
import me.myogoo.appliedterminalmodules.network.TransferCraftingRecipePacket;
import me.myogoo.appliedterminalmodules.network.WorkstationPackets;
import me.myogoo.myotus.api.MyotusAPI;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;

@Mod(AppliedTerminalModules.MOD_ID)
public final class TerminalModulesMod {
    public TerminalModulesMod(IEventBus bus) {
        ModItems.ITEMS.register(bus);
        ModItems.TABS.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(ModuleDataGenerators::gatherData);
        bus.addListener(TransferCraftingRecipePacket::register);
        bus.addListener(WorkstationPackets::register);
        NeoForge.EVENT_BUS.addListener(TerminalModulesMod::containerClosed);
    }

    private static void containerClosed(PlayerContainerEvent.Close event) {
        // A card can be removed immediately before close, before its next Myotus lifecycle update.
        if (event.getContainer() instanceof MEStorageMenu menu) {
            CraftingModuleProvider.returnIngredients(menu);
            ChargingModuleProvider.returnItem(menu);
            for (var kind : WorkstationKind.values()) WorkstationModuleProvider.returnInputs(menu, kind);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            var modules = MyotusAPI.terminalModules();
            modules.registerSlots(ModItems.EQUIPMENT_MODULE.get(), ModuleSemantics.EQUIPMENT,
                    new EquipmentModuleProvider());
            modules.registerSlots(ModItems.CRAFTING_MODULE.get(), ModuleSemantics.CRAFTING,
                    new CraftingModuleProvider());
            modules.registerSlots(ModItems.CHARGING_MODULE.get(), ModuleSemantics.CHARGING,
                    new ChargingModuleProvider());
            for (var kind : WorkstationKind.values()) {
                modules.registerSlots(kind.card(), kind.semantic(), new WorkstationModuleProvider(kind));
            }
            if (ModItems.CURIOS_MODULE.get().isAvailable()) {
                modules.registerSlots(ModItems.CURIOS_MODULE.get(), ModuleSemantics.CURIOS,
                        new CuriosModuleProvider());
            }
            if (ModList.get().isLoaded("accessories")) {
                modules.registerSlots(ModItems.ACCESSORIES_MODULE.get(), ModuleSemantics.ACCESSORIES,
                        new AccessoriesModuleProvider());
            }
        });
    }
}
