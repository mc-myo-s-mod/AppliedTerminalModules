package me.myogoo.appliedterminalmodules.menu;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import appeng.api.config.FuzzyMode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageHelper;
import appeng.items.storage.ViewCellItem;
import appeng.me.helpers.ActionHostEnergySource;
import appeng.me.storage.NullInventory;
import appeng.menu.me.common.MEStorageMenu;
import appeng.util.CraftingRecipeUtil;
import appeng.util.prioritylist.IPartitionList;
import me.myogoo.appliedterminalmodules.init.ModItems;
import me.myogoo.myotus.api.MyotusAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.jetbrains.annotations.Nullable;

public final class CraftingRecipeTransfer {
    private CraftingRecipeTransfer() {}

    public static void transfer(ServerPlayer player, int containerId, ResourceLocation recipeId) {
        if (!player.isAlive() || player.isSpectator() || player.hasDisconnected()
                || !(player.containerMenu instanceof MEStorageMenu menu)
                || menu.containerId != containerId || !menu.isValidMenu() || !menu.stillValid(player)) return;
        var slots = MyotusAPI.terminalModules().slots(menu, ModItems.CRAFTING_MODULE.get());
        if (slots.size() != 10 || slots.stream().anyMatch(slot -> !slot.isActive())) return;
        var holder = player.level().getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof CraftingRecipe recipe)
                || !(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)
                || !recipe.canCraftInDimensions(3, 3) || recipe.getIngredients().isEmpty()
                || recipe.getIngredients().size() > 9) return;
        var ingredients = CraftingRecipeUtil.ensure3by3CraftingMatrix(recipe).stream()
                .map(ingredient -> ingredient.isEmpty() ? null : (Predicate<ItemStack>) ingredient).toList();
        fillInputs(menu, slots, ingredients);
        CraftingModuleProvider.refresh(menu);
        // Vanilla broadcasts the slot changes at the end of the server tick.
    }

    public static void transferWorkstation(ServerPlayer player, int containerId, ResourceLocation recipeId) {
        if (!player.isAlive() || player.isSpectator() || player.hasDisconnected()
                || !(player.containerMenu instanceof MEStorageMenu menu)
                || menu.containerId != containerId || !menu.isValidMenu() || !menu.stillValid(player)) return;
        var holder = player.level().getRecipeManager().byKey(recipeId).orElse(null);
        var kind = workstationKind(holder);
        if (kind == null) return;
        var ingredients = workstationInputs(holder);
        if (ingredients.size() != kind.inputCount()) return;
        var slots = MyotusAPI.terminalModules().slots(menu, kind.card());
        if (slots.size() != kind.inputCount() + 1 || slots.stream().anyMatch(slot -> !slot.isActive())) return;
        fillInputs(menu, slots, ingredients);
        if (kind == WorkstationKind.STONECUTTING) WorkstationModuleProvider.selectRecipe(menu, holder.id());
        WorkstationModuleProvider.refresh(menu, kind);
    }

    public static @Nullable WorkstationKind workstationKind(@Nullable RecipeHolder<?> holder) {
        if (holder == null) return null;
        if (holder.value() instanceof StonecutterRecipe) return WorkstationKind.STONECUTTING;
        if (holder.value() instanceof SmithingRecipe) return WorkstationKind.SMITHING;
        return null;
    }

    /** Smithing exposes three native predicates, not a public ingredient array in Minecraft 1.21.1. */
    public static List<Predicate<ItemStack>> workstationInputs(@Nullable RecipeHolder<?> holder) {
        if (holder == null || holder.value().isIncomplete()) return List.of();
        if (holder.value() instanceof StonecutterRecipe stonecutting) {
            var ingredients = stonecutting.getIngredients();
            return ingredients.size() == 1 && !ingredients.getFirst().isEmpty()
                    ? List.of(ingredients.getFirst()) : List.of();
        }
        if (holder.value() instanceof SmithingRecipe smithing) {
            return List.of(smithing::isTemplateIngredient, smithing::isBaseIngredient, smithing::isAdditionIngredient);
        }
        return List.of();
    }

    private static void fillInputs(MEStorageMenu menu, List<Slot> slots, List<Predicate<ItemStack>> ingredients) {
        var player = menu.getPlayer();
        var host = menu.getHost();
        var storage = host.getLinkStatus().connected() ? host.getInventory() : NullInventory.of();
        IEnergySource energy = host instanceof IEnergySource source ? source
                : host instanceof IActionHost actionHost ? new ActionHostEnergySource(actionHost) : IEnergySource.empty();
        var filter = ViewCellItem.createItemFilter(menu.getViewCells());

        // Clear all mismatches first so ingredients from the old recipe can be used in new positions.
        for (int i = 0; i < ingredients.size(); i++) {
            Slot slot = slots.get(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || ingredients.get(i) != null && ingredients.get(i).test(stack)) continue;
            stack = stack.copy();
            var key = AEItemKey.of(stack);
            long inserted = StorageHelper.poweredInsert(energy, storage, key, stack.getCount(), menu.getActionSource());
            stack.shrink((int) inserted);
            returnToPlayer(menu, stack);
            // A full inventory must keep the remainder in the grid, never drop or replace it.
            slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
        }

        KeyCounter available = storage.getAvailableStacks();
        for (int i = 0; i < ingredients.size(); i++) {
            var slot = slots.get(i);
            var ingredient = ingredients.get(i);
            if (slot.hasItem() || ingredient == null) continue;
            ItemStack input = ItemStack.EMPTY;
            for (var key : matchingKeys(ingredient, filter, available)) {
                if (!menu.isKeyVisible(key)) continue;
                long extracted = StorageHelper.poweredExtraction(energy, storage, key, 1, menu.getActionSource());
                if (extracted > 0) {
                    input = key.toStack((int) extracted);
                    break;
                }
            }
            if (input.isEmpty()) {
                for (int playerSlot = 0; playerSlot < player.getInventory().items.size(); playerSlot++) {
                    if (menu.isPlayerInventorySlotLocked(playerSlot)) continue;
                    var stack = player.getInventory().getItem(playerSlot);
                    if (!stack.isEmpty() && ingredient.test(stack)) {
                        input = stack.split(1);
                        break;
                    }
                }
            }
            slot.setByPlayer(input);
        }
        player.getInventory().setChanged();
    }

    private static void returnToPlayer(MEStorageMenu menu, ItemStack remainder) {
        var inventory = menu.getPlayerInventory();
        // Inventory.add discards overflow for creative players. Move only what fits, including in creative mode.
        for (int pass = 0; pass < 2 && !remainder.isEmpty(); pass++) {
            for (int i = 0; i < inventory.items.size() && !remainder.isEmpty(); i++) {
                if (menu.isPlayerInventorySlotLocked(i)) continue;
                var current = inventory.getItem(i);
                if (pass == 0 && !current.isEmpty() && ItemStack.isSameItemSameComponents(current, remainder)) {
                    int count = Math.min(remainder.getCount(), current.getMaxStackSize() - current.getCount());
                    if (count > 0) { current.grow(count); remainder.shrink(count); }
                } else if (pass == 1 && current.isEmpty()) {
                    inventory.setItem(i, remainder.split(Math.min(remainder.getCount(), remainder.getMaxStackSize())));
                }
            }
        }
    }

    private static List<AEItemKey> matchingKeys(Predicate<ItemStack> ingredient, IPartitionList filter, KeyCounter available) {
        // Keep crafting's ingredient-order/fuzzy candidate lookup; smithing supplies only native predicates.
        var candidates = ingredient instanceof Ingredient hint
                ? Arrays.stream(hint.getItems()).map(AEItemKey::of).filter(key -> key != null)
                        .flatMap(key -> available.findFuzzy(key, FuzzyMode.IGNORE_ALL).stream())
                : StreamSupport.stream(available.spliterator(), false);
        return candidates
                .filter(entry -> entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey key
                        && ingredient.test(key.getReadOnlyStack())
                        && (filter == null || filter.isListed(key)))
                .sorted((a, b) -> Long.compare(b.getLongValue(), a.getLongValue()))
                .map(entry -> (AEItemKey) entry.getKey()).distinct().toList();
    }
}
