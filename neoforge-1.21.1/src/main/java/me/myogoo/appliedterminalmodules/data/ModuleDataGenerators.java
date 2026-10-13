package me.myogoo.appliedterminalmodules.data;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import me.myogoo.appliedterminalmodules.AppliedTerminalModules;
import me.myogoo.appliedterminalmodules.init.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.conditions.AndCondition;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import net.neoforged.neoforge.common.conditions.NotCondition;
import net.neoforged.neoforge.data.event.GatherDataEvent;

public final class ModuleDataGenerators {
    private ModuleDataGenerators() {}

    public static void gatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeServer(),
                new Recipes(event.getGenerator().getPackOutput(), event.getLookupProvider()));
    }

    private static final class Recipes extends RecipeProvider {
        private Recipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected void buildRecipes(RecipeOutput output) {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.EQUIPMENT_MODULE.get())
                    .requires(AEItems.BASIC_CARD).requires(Items.IRON_CHESTPLATE).requires(Items.SHIELD)
                    .requires(AEItems.LOGIC_PROCESSOR)
                    .unlockedBy("has_basic_card", has(AEItems.BASIC_CARD))
                    .save(output, id("equipment_module"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.CRAFTING_MODULE.get())
                    .requires(AEItems.BASIC_CARD).requires(Items.CRAFTING_TABLE)
                    .unlockedBy("has_basic_card", has(AEItems.BASIC_CARD))
                    .save(output, id("crafting_module"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.CURIOS_MODULE.get())
                    .requires(AEItems.ADVANCED_CARD).requires(Items.ENDER_PEARL)
                    .requires(AEItems.LOGIC_PROCESSOR)
                    .unlockedBy("has_advanced_card", has(AEItems.ADVANCED_CARD))
                    .save(output.withConditions(new AndCondition(List.of(
                            new ModLoadedCondition("curios"),
                            new NotCondition(new ModLoadedCondition("accessories_compat_layer"))))), id("curios_module"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.STONECUTTING_MODULE.get())
                    .requires(AEItems.BASIC_CARD).requires(Items.STONECUTTER)
                    .unlockedBy("has_basic_card", has(AEItems.BASIC_CARD))
                    .save(output, id("stonecutting_module"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ANVIL_MODULE.get())
                    .requires(AEItems.BASIC_CARD).requires(Items.ANVIL)
                    .unlockedBy("has_basic_card", has(AEItems.BASIC_CARD))
                    .save(output, id("anvil_module"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.CHARGING_MODULE.get())
                    .requires(AEItems.ADVANCED_CARD).requires(AEBlocks.CHARGER)
                    .requires(AEItems.ENGINEERING_PROCESSOR)
                    .unlockedBy("has_advanced_card", has(AEItems.ADVANCED_CARD))
                    .save(output, id("charging_module"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.SMITHING_MODULE.get())
                    .requires(AEItems.BASIC_CARD).requires(Items.SMITHING_TABLE)
                    .unlockedBy("has_basic_card", has(AEItems.BASIC_CARD))
                    .save(output, id("smithing_module"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.GRINDSTONE_MODULE.get())
                    .requires(AEItems.BASIC_CARD).requires(Items.GRINDSTONE)
                    .unlockedBy("has_basic_card", has(AEItems.BASIC_CARD))
                    .save(output, id("grindstone_module"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ACCESSORIES_MODULE.get())
                    .requires(AEItems.ADVANCED_CARD).requires(Items.ENDER_PEARL)
                    .requires(AEItems.CALCULATION_PROCESSOR)
                    .unlockedBy("has_advanced_card", has(AEItems.ADVANCED_CARD))
                    .save(output.withConditions(new ModLoadedCondition("accessories")), id("accessories_module"));
        }

        private static ResourceLocation id(String path) {
            return ResourceLocation.fromNamespaceAndPath(AppliedTerminalModules.MOD_ID, path);
        }
    }
}
