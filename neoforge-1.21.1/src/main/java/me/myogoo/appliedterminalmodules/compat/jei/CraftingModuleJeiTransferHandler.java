package me.myogoo.appliedterminalmodules.compat.jei;

import java.util.Optional;

import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.compat.recipeviewer.CraftingModuleTransfer;
import me.myogoo.appliedterminalmodules.menu.CraftingRecipeTransfer;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

/** Category-specific handlers keep precedence over this crafting/workstation fallback. */
public final class CraftingModuleJeiTransferHandler implements IUniversalRecipeTransferHandler<MEStorageMenu> {
    private final Class<? extends MEStorageMenu> menuClass;
    private final IRecipeTransferHandlerHelper helper;

    public CraftingModuleJeiTransferHandler(Class<? extends MEStorageMenu> menuClass,
            IRecipeTransferHandlerHelper helper) {
        this.menuClass = menuClass;
        this.helper = helper;
    }

    @Override
    public Class<? extends MEStorageMenu> getContainerClass() { return menuClass; }

    @Override
    public Optional<MenuType<MEStorageMenu>> getMenuType() { return Optional.empty(); }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(MEStorageMenu menu, Object recipe,
            IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
        if (!(recipe instanceof RecipeHolder<?> holder)) {
            if (!CraftingModuleTransfer.isActive(menu)) return helper.createInternalError();
            return helper.createUserErrorWithTooltip(CraftingModuleTransfer.text("server_recipe_required"));
        }
        var kind = CraftingRecipeTransfer.workstationKind(holder);
        if (kind == null ? !CraftingModuleTransfer.isActive(menu) : !CraftingModuleTransfer.isActive(menu, kind)) {
            return helper.createInternalError();
        }
        if (kind == null && !(holder.value() instanceof CraftingRecipe)) {
            return helper.createUserErrorWithTooltip(CraftingModuleTransfer.text("server_recipe_required"));
        }
        var check = CraftingModuleTransfer.check(menu, holder);
        if (!check.canTransfer()) return helper.createUserErrorWithTooltip(check.tooltip());
        // maxTransfer deliberately transfers one set. Simulation never sends a packet or changes a slot.
        if (doTransfer) {
            return CraftingModuleTransfer.send(menu, holder) ? null
                    : helper.createUserErrorWithTooltip(CraftingModuleTransfer.check(menu, holder).tooltip());
        }
        if (!check.missingSlots().isEmpty()) {
            return new IRecipeTransferError() {
                @Override
                public Type getType() { return Type.COSMETIC; }

                @Override
                public void getTooltip(ITooltipBuilder tooltip) { tooltip.add(check.tooltip()); }

                @Override
                public int getMissingCountHint() { return check.missingSlots().size(); }
            };
        }
        return null;
    }
}
