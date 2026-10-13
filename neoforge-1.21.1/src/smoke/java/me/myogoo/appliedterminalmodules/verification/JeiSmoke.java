package me.myogoo.appliedterminalmodules.verification;

import java.util.List;
import java.util.Optional;
import java.lang.reflect.Proxy;

import appeng.client.gui.AEBaseScreen;
import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.AppliedTerminalModules;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import me.myogoo.myotus.client.TerminalModuleTransferKey;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

@JeiPlugin
public final class JeiSmoke implements IModPlugin {
    private static IJeiRuntime runtime;
    public static Boolean transferKey;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(AppliedTerminalModules.MOD_ID, "smoke");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime value) { runtime = value; }

    public static boolean ready() { return runtime != null; }

    public static void checkEntryPoint(AEBaseScreen<?> screen, RecipeHolder<?> recipe) {
        if (!(runtime.getRecipesGui() instanceof me.myogoo.myotus.client.JeiTerminalModuleTransfers.ParentScreenAccess)) {
            throw new IllegalStateException("JEI parent-screen mixin missing");
        }
        org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
        var client = Minecraft.getInstance();
        var previousScreen = client.screen;
        var manager = runtime.getRecipeTransferManager();
        var category = runtime.getRecipeManager().getRecipeCategory(RecipeTypes.CRAFTING);
        var layout = (IRecipeLayoutDrawable<?>) Proxy.newProxyInstance(IRecipeLayoutDrawable.class.getClassLoader(),
                new Class<?>[] {IRecipeLayoutDrawable.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getRecipe" -> recipe;
                    case "getRecipeCategory" -> category;
                    case "getRecipeSlotsView" -> (IRecipeSlotsView) List::of;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        try {
            client.screen = screen;
            Object service;
            java.lang.reflect.Method transfer;
            Object[] args;
            String owner;
            try {
                var type = Class.forName("mezz.jei.common.transfer.RecipeTransferService");
                service = type.getConstructor(IRecipeTransferManager.class).newInstance(manager);
                transfer = type.getDeclaredMethod("transferRecipe", AbstractContainerScreen.class,
                        IRecipeLayoutDrawable.class, Player.class, boolean.class, boolean.class);
                args = new Object[] {screen, layout, client.player, true, false};
                owner = "RecipeTransferService";
            } catch (ClassNotFoundException legacyJei) {
                service = null;
                transfer = Class.forName("mezz.jei.common.transfer.RecipeTransferUtil").getDeclaredMethod(
                        "transferRecipe", IRecipeTransferManager.class, AbstractContainerMenu.class,
                        IRecipeLayoutDrawable.class, Player.class, boolean.class, boolean.class);
                args = new Object[] {manager, screen.getMenu(), layout, client.player, true, false};
                owner = "RecipeTransferUtil";
            }
            transfer.setAccessible(true);
            transferKey = false;
            if (TerminalModuleTransferKey.isDown()) throw new IllegalStateException("Smoke key override failed");
            if (((Optional<?>) transfer.invoke(service, args)).isPresent()) {
                throw new IllegalStateException("Ordinary JEI recipe preview changed");
            }
            transferKey = true;
            if (!TerminalModuleTransferKey.isDown()) throw new IllegalStateException("Smoke key mixin missing");
            var preview = (Optional<?>) transfer.invoke(service, args);
            if (((IRecipeTransferError) preview.orElseThrow()).getType() != IRecipeTransferError.Type.COSMETIC) {
                throw new IllegalStateException("Module preview did not intercept " + owner);
            }
            args[args.length - 1] = true;
            if (((Optional<?>) transfer.invoke(service, args)).isPresent()) {
                throw new IllegalStateException("Module click did not dispatch from " + owner);
            }
            com.mojang.logging.LogUtils.getLogger().info(
                    "ATM JEI ENTRY POINT PASS: {}, ordinary preview, module preview and max-transfer request dispatched; synthetic client menu, server inventory transfer tested separately", owner);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Actual JEI transfer entry point failed", error);
        } finally {
            transferKey = null;
            client.screen = previousScreen;
        }
    }

    public static void check(MEStorageMenu menu, RecipeHolder<CraftingRecipe> recipe) {
        var category = runtime.getRecipeManager().getRecipeCategory(RecipeTypes.CRAFTING);
        var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, category).orElseThrow();
        // JEI wraps universal handlers in its own adapter; exercise the registered public handler.
        var error = handler.transferRecipe(menu, recipe, () -> List.of(), menu.getPlayer(), false, false);
        if (error != null) throw new IllegalStateException("JEI dry-run rejected a complete recipe: " + error.getType());
    }

    public static void checkWorkstation(MEStorageMenu menu, RecipeHolder<?> recipe,
            boolean active, boolean canTransfer, int missing, boolean maxTransfer) {
        if (recipe.value() instanceof StonecutterRecipe) {
            @SuppressWarnings("unchecked") var typed = (RecipeHolder<StonecutterRecipe>) recipe;
            checkWorkstation(menu, typed, RecipeTypes.STONECUTTING, active, canTransfer, missing, maxTransfer);
        } else if (recipe.value() instanceof SmithingRecipe) {
            @SuppressWarnings("unchecked") var typed = (RecipeHolder<SmithingRecipe>) recipe;
            checkWorkstation(menu, typed, RecipeTypes.SMITHING, active, canTransfer, missing, maxTransfer);
        } else {
            throw new IllegalArgumentException("Unsupported workstation recipe: " + recipe.id());
        }
    }

    private static <R> void checkWorkstation(MEStorageMenu menu, R recipe, RecipeType<R> type,
            boolean active, boolean canTransfer, int missing, boolean maxTransfer) {
        var category = runtime.getRecipeManager().getRecipeCategory(type);
        var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, category).orElseThrow();
        // Always simulate: this fixture is not a server-opened container.
        var error = handler.transferRecipe(menu, recipe, () -> List.of(), menu.getPlayer(), maxTransfer, false);
        var expected = !active ? IRecipeTransferError.Type.INTERNAL
                : !canTransfer ? IRecipeTransferError.Type.USER_FACING
                : missing > 0 ? IRecipeTransferError.Type.COSMETIC : null;
        if (expected == null ? error != null : error == null || error.getType() != expected) {
            throw new IllegalStateException("Registered JEI " + type + " preflight: expected " + expected
                    + ", got " + (error == null ? null : error.getType()));
        }
        if (canTransfer && missing > 0 && error.getMissingCountHint() != missing) {
            throw new IllegalStateException("JEI workstation missing-count hint changed");
        }
    }
}
