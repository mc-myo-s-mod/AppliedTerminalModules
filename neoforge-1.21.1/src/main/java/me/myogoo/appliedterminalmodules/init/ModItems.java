package me.myogoo.appliedterminalmodules.init;

import me.myogoo.appliedterminalmodules.AppliedTerminalModules;
import me.myogoo.appliedterminalmodules.item.CraftingModuleItem;
import me.myogoo.appliedterminalmodules.item.ChargingModuleItem;
import me.myogoo.appliedterminalmodules.item.ModuleItem;
import me.myogoo.appliedterminalmodules.item.WorkstationModuleItem;
import me.myogoo.appliedterminalmodules.menu.WorkstationKind;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AppliedTerminalModules.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AppliedTerminalModules.MOD_ID);

    public static final DeferredItem<ModuleItem> EQUIPMENT_MODULE =
            ITEMS.register("equipment_module", () -> new ModuleItem());
    public static final DeferredItem<CraftingModuleItem> CRAFTING_MODULE =
            ITEMS.register("crafting_module", CraftingModuleItem::new);
    public static final DeferredItem<ModuleItem> CURIOS_MODULE =
            ITEMS.register("curios_module", () -> new ModuleItem("curios"));
    public static final DeferredItem<ModuleItem> ACCESSORIES_MODULE =
            ITEMS.register("accessories_module", () -> new ModuleItem("accessories"));
    public static final DeferredItem<ChargingModuleItem> CHARGING_MODULE =
            ITEMS.register("charging_module", ChargingModuleItem::new);
    public static final DeferredItem<WorkstationModuleItem> STONECUTTING_MODULE =
            ITEMS.register("stonecutting_module", () -> new WorkstationModuleItem(WorkstationKind.STONECUTTING));
    public static final DeferredItem<WorkstationModuleItem> ANVIL_MODULE =
            ITEMS.register("anvil_module", () -> new WorkstationModuleItem(WorkstationKind.ANVIL));
    public static final DeferredItem<WorkstationModuleItem> SMITHING_MODULE =
            ITEMS.register("smithing_module", () -> new WorkstationModuleItem(WorkstationKind.SMITHING));
    public static final DeferredItem<WorkstationModuleItem> GRINDSTONE_MODULE =
            ITEMS.register("grindstone_module", () -> new WorkstationModuleItem(WorkstationKind.GRINDSTONE));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.appliedterminalmodules"))
                    .icon(EQUIPMENT_MODULE::toStack)
                    .displayItems((parameters, output) -> {
                        output.accept(EQUIPMENT_MODULE);
                        output.accept(CRAFTING_MODULE);
                        output.accept(STONECUTTING_MODULE);
                        output.accept(ANVIL_MODULE);
                        output.accept(CHARGING_MODULE);
                        output.accept(SMITHING_MODULE);
                        output.accept(GRINDSTONE_MODULE);
                        if (CURIOS_MODULE.get().isAvailable()) output.accept(CURIOS_MODULE);
                        if (ACCESSORIES_MODULE.get().isAvailable()) output.accept(ACCESSORIES_MODULE);
                    }).build());

    private ModItems() {}
}
