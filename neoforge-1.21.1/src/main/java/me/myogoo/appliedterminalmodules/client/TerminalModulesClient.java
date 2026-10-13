package me.myogoo.appliedterminalmodules.client;

import java.util.List;

import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.AppliedTerminalModules;
import me.myogoo.appliedterminalmodules.compat.recipeviewer.CraftingModuleTransfer;
import me.myogoo.appliedterminalmodules.init.ModItems;
import me.myogoo.appliedterminalmodules.menu.ModuleSemantics;
import me.myogoo.appliedterminalmodules.menu.CraftingRecipeTransfer;
import me.myogoo.appliedterminalmodules.menu.WorkstationKind;
import me.myogoo.myotus.api.MyotusAPI;
import me.myogoo.myotus.api.client.ITerminalModuleRecipeTransfer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.jetbrains.annotations.Nullable;

@Mod(value = AppliedTerminalModules.MOD_ID, dist = Dist.CLIENT)
public final class TerminalModulesClient {
    public TerminalModulesClient(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ModulePanelPositions.SPEC);
        bus.addListener(TerminalModulesClient::setup);
    }

    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (var position : List.of(ModulePanelPositions.EQUIPMENT, ModulePanelPositions.CRAFTING,
                    ModulePanelPositions.CURIOS, ModulePanelPositions.ACCESSORIES, ModulePanelPositions.STONECUTTING,
                    ModulePanelPositions.ANVIL, ModulePanelPositions.CHARGING, ModulePanelPositions.SMITHING,
                    ModulePanelPositions.GRINDSTONE)) {
                MyotusAPI.Client.registerTerminalModulePosition(position);
            }
            MyotusAPI.Client.registerTerminalModule(ModItems.EQUIPMENT_MODULE.get(), (screen, stack) ->
                    MyotusAPI.Client.terminalModulePanel((MEStorageMenu) screen.getMenu(), ModuleSemantics.EQUIPMENT,
                            ModulePanelPositions.EQUIPMENT));
            MyotusAPI.Client.registerTerminalModule(ModItems.CRAFTING_MODULE.get(), (screen, stack) ->
                    new CraftingModulePanel((MEStorageMenu) screen.getMenu()));
            MyotusAPI.Client.registerTerminalModule(ModItems.CHARGING_MODULE.get(), (screen, stack) ->
                    new ChargingModulePanel((MEStorageMenu) screen.getMenu()));
            for (var kind : WorkstationKind.values()) {
                var position = switch (kind) {
                    case STONECUTTING -> ModulePanelPositions.STONECUTTING;
                    case ANVIL -> ModulePanelPositions.ANVIL;
                    case SMITHING -> ModulePanelPositions.SMITHING;
                    case GRINDSTONE -> ModulePanelPositions.GRINDSTONE;
                };
                MyotusAPI.Client.registerTerminalModule(kind.card(), (screen, stack) ->
                        new WorkstationModulePanel(screen, kind, position));
                if (kind == WorkstationKind.STONECUTTING || kind == WorkstationKind.SMITHING) {
                    MyotusAPI.Client.registerTerminalModuleRecipeTransfer(kind.card(), new ITerminalModuleRecipeTransfer() {
                        @Override
                        public boolean supports(MEStorageMenu menu, RecipeHolder<?> recipe) {
                            return CraftingRecipeTransfer.workstationKind(recipe) == kind;
                        }

                        @Override
                        public @Nullable Component getError(MEStorageMenu menu, RecipeHolder<?> recipe) {
                            return CraftingModuleTransfer.check(menu, recipe).error();
                        }

                        @Override
                        public boolean transfer(MEStorageMenu menu, RecipeHolder<?> recipe, boolean maxTransfer) {
                            return CraftingModuleTransfer.send(menu, recipe);
                        }
                    });
                }
            }
            MyotusAPI.Client.registerTerminalModuleRecipeTransfer(ModItems.CRAFTING_MODULE.get(),
                    new ITerminalModuleRecipeTransfer() {
                        @Override
                        public boolean supports(MEStorageMenu menu, RecipeHolder<?> holder) {
                            return holder.value() instanceof CraftingRecipe recipe
                                    && (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)
                                    && recipe.canCraftInDimensions(3, 3) && recipe.getIngredients().size() <= 9;
                        }

                        @Override
                        public @Nullable Component getError(MEStorageMenu menu, RecipeHolder<?> recipe) {
                            return CraftingModuleTransfer.check(menu, recipe).error();
                        }

                        @Override
                        public boolean transfer(MEStorageMenu menu, RecipeHolder<?> recipe, boolean maxTransfer) {
                            // The existing server transfer deliberately fills one set, even for max transfer.
                            return CraftingModuleTransfer.send(menu, recipe);
                        }
                    });
            if (ModItems.CURIOS_MODULE.get().isAvailable()) {
                MyotusAPI.Client.registerTerminalModule(ModItems.CURIOS_MODULE.get(), (screen, stack) ->
                        MyotusAPI.Client.terminalModulePanel((MEStorageMenu) screen.getMenu(), ModuleSemantics.CURIOS,
                                ModulePanelPositions.CURIOS));
            }
            if (ModList.get().isLoaded("accessories")) {
                MyotusAPI.Client.registerTerminalModule(ModItems.ACCESSORIES_MODULE.get(), (screen, stack) ->
                        MyotusAPI.Client.terminalModulePanel((MEStorageMenu) screen.getMenu(), ModuleSemantics.ACCESSORIES,
                                ModulePanelPositions.ACCESSORIES));
            }
        });
    }
}
