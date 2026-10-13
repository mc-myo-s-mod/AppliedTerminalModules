package me.myogoo.appliedterminalmodules.compat.recipeviewer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import appeng.api.stacks.AEItemKey;
import appeng.items.storage.ViewCellItem;
import appeng.menu.me.common.MEStorageMenu;
import appeng.util.CraftingRecipeUtil;
import me.myogoo.appliedterminalmodules.init.ModItems;
import me.myogoo.appliedterminalmodules.menu.CraftingRecipeTransfer;
import me.myogoo.appliedterminalmodules.menu.WorkstationKind;
import me.myogoo.appliedterminalmodules.network.TransferCraftingRecipePacket;
import me.myogoo.appliedterminalmodules.network.TransferWorkstationRecipePacket;
import me.myogoo.myotus.api.MyotusAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Client estimates only; extraction, permissions and final slot contents belong to the server. */
public final class CraftingModuleTransfer {
    private CraftingModuleTransfer() {}

    public static boolean isActive(MEStorageMenu menu) {
        var api = MyotusAPI.terminalModules();
        var slots = api.slots(menu, ModItems.CRAFTING_MODULE.get());
        return api.isInstalled(menu, ModItems.CRAFTING_MODULE.get()) && slots.size() == 10
                && slots.stream().allMatch(Slot::isActive);
    }

    public static boolean isActive(MEStorageMenu menu, WorkstationKind kind) {
        var api = MyotusAPI.terminalModules();
        var slots = api.slots(menu, kind.card());
        return (kind == WorkstationKind.STONECUTTING || kind == WorkstationKind.SMITHING)
                && api.isInstalled(menu, kind.card()) && slots.size() == kind.inputCount() + 1
                && slots.stream().allMatch(Slot::isActive);
    }

    public static Component text(String key) {
        return Component.translatable("appliedterminalmodules.recipe_transfer." + key);
    }

    public static Check check(MEStorageMenu menu, @Nullable RecipeHolder<?> displayRecipe) {
        var kind = CraftingRecipeTransfer.workstationKind(displayRecipe);
        if (kind == null ? !isActive(menu) : !isActive(menu, kind)) return failed("module_required");
        var player = Minecraft.getInstance().player;
        if (player == null || player.containerMenu != menu || !player.isAlive() || player.isSpectator()) {
            return failed("menu_unavailable");
        }
        var slots = MyotusAPI.terminalModules().slots(menu, kind == null ? ModItems.CRAFTING_MODULE.get() : kind.card());
        // The panel hides every slot off-screen when no collision-free layout fits.
        if (slots.stream().anyMatch(slot -> slot.x <= -1000 || slot.y <= -1000)) {
            return failed("panel_unavailable");
        }
        if (displayRecipe == null) return failed("server_recipe_required");
        var holder = player.level().getRecipeManager().byKey(displayRecipe.id()).orElse(null);
        List<Predicate<ItemStack>> ingredients;
        if (kind != null) {
            if (CraftingRecipeTransfer.workstationKind(holder) != kind) return failed("server_recipe_required");
            ingredients = CraftingRecipeTransfer.workstationInputs(holder);
            if (ingredients.size() != kind.inputCount()) return failed("server_recipe_required");
        } else {
            if (holder == null || !(holder.value() instanceof CraftingRecipe recipe)
                    || !(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)) {
                return failed("server_recipe_required");
            }
            if (!recipe.canCraftInDimensions(3, 3) || recipe.getIngredients().size() > 9) {
                return failed("recipe_too_large");
            }
            if (recipe.getIngredients().stream().allMatch(ingredient -> ingredient.isEmpty())) {
                return failed("server_recipe_required");
            }
            ingredients = CraftingRecipeUtil.ensure3by3CraftingMatrix(recipe).stream()
                    .map(ingredient -> ingredient.isEmpty() ? null : (Predicate<ItemStack>) ingredient).toList();
        }

        var sources = availableStacks(menu);
        // The server keeps matching inputs in place. Only mismatches can become available elsewhere.
        for (int i = 0; i < ingredients.size(); i++) {
            var stack = slots.get(i).getItem();
            if (!stack.isEmpty() && (ingredients.get(i) == null || !ingredients.get(i).test(stack))) sources.add(stack.copy());
        }
        Set<Integer> missing = new LinkedHashSet<>();
        int required = 0;
        for (int i = 0; i < ingredients.size(); i++) {
            var ingredient = ingredients.get(i);
            if (ingredient == null) continue;
            required++;
            if (ingredient.test(slots.get(i).getItem())) continue;
            boolean found = false;
            for (var source : sources) {
                if (!source.isEmpty() && ingredient.test(source)) {
                    source.shrink(1);
                    found = true;
                    break;
                }
            }
            if (!found) missing.add(i);
        }
        if (missing.size() == required && menu.getLinkStatus().connected() && menu.getClientRepo() == null) {
            return failed("network_pending");
        }
        var error = missing.size() == required ? text("missing_ingredients") : null;
        return new Check(holder.id(), Set.copyOf(missing), error);
    }

    /** Copies are safe for preflight reservation and EMI's inventory aggregation. Never includes craftable-only keys. */
    public static List<ItemStack> availableStacks(MEStorageMenu menu) {
        List<ItemStack> sources = new ArrayList<>();
        var repo = menu.getClientRepo();
        if (repo != null && menu.getLinkStatus().connected()) {
            var filter = ViewCellItem.createItemFilter(menu.getViewCells());
            for (var entry : repo.getAllEntries()) {
                if (entry.getStoredAmount() > 0 && entry.getWhat() instanceof AEItemKey key
                        && menu.isKeyVisible(key) && (filter == null || filter.isListed(key))) {
                    // At most nine units are needed to estimate one recipe set.
                    sources.add(key.toStack((int) Math.min(9, entry.getStoredAmount())));
                }
            }
        }
        var inventory = menu.getPlayerInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            if (!menu.isPlayerInventorySlotLocked(i)) sources.add(inventory.getItem(i).copy());
        }
        return sources;
    }

    /** Rechecks on click. A true return means a request was sent, not that server extraction succeeded. */
    public static boolean send(MEStorageMenu menu, @Nullable RecipeHolder<?> recipe) {
        var check = check(menu, recipe);
        if (!check.canTransfer()) return false;
        if (CraftingRecipeTransfer.workstationKind(recipe) != null) {
            PacketDistributor.sendToServer(new TransferWorkstationRecipePacket(menu.containerId, check.recipeId()));
        } else {
            PacketDistributor.sendToServer(new TransferCraftingRecipePacket(menu.containerId, check.recipeId()));
        }
        return true;
    }

    private static Check failed(String key) {
        return new Check(null, Set.of(), text(key));
    }

    public record Check(@Nullable ResourceLocation recipeId, Set<Integer> missingSlots, @Nullable Component error) {
        public boolean canTransfer() { return error == null && recipeId != null; }

        public Component tooltip() {
            return error != null ? error : text(missingSlots.isEmpty() ? "transfer_hint" : "partial_transfer");
        }
    }
}
