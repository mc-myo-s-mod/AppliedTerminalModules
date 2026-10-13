package me.myogoo.appliedterminalmodules.gametest;

import java.util.ArrayList;
import java.util.List;

import appeng.client.Point;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.StyleManager;
import appeng.menu.me.common.MEStorageMenu;
import com.mojang.logging.LogUtils;
import me.myogoo.appliedterminalmodules.compat.recipeviewer.CraftingModuleTransfer;
import me.myogoo.appliedterminalmodules.init.ModItems;
import me.myogoo.appliedterminalmodules.menu.WorkstationKind;
import me.myogoo.appliedterminalmodules.verification.JeiSmoke;
import me.myogoo.myotus.api.MyotusAPI;
import me.myogoo.myotus.client.TerminalModuleRecipeTransfers;
import me.myogoo.myotus.client.TerminalModuleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;

public final class ViewerSmoke {
    public static boolean check() {
        // EMI's JEMI bridge skips JEI callbacks for mods that already provide a native EMI plugin.
        boolean jei = ModList.get().isLoaded("jei") && !ModList.get().isLoaded("emi");
        if (jei && !JeiSmoke.ready()) return false;
        boolean emi = ModList.get().isLoaded("emi");
        if (emi && !EmiChecks.ready()) return false;
        var client = Minecraft.getInstance();
        var player = client.player;
        var previousMenu = player.containerMenu;
        var previousScreen = client.screen;
        int previousScale = client.options.guiScale().get();
        var inventory = player.getInventory();
        List<ItemStack> previousStacks = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) previousStacks.add(inventory.getItem(i).copy());
        try {
            var menu = new MEStorageMenu(MEStorageMenu.TYPE, 70, inventory, new TestTerminals.TestHost(player));
            menu.addSlotListener(new ContainerListener() {
                @Override public void slotChanged(AbstractContainerMenu container, int slot, ItemStack stack) {}
                @Override public void dataChanged(AbstractContainerMenu container, int id, int value) {}
            });
            player.containerMenu = menu;
            for (int i = 0; i < inventory.getContainerSize(); i++) inventory.setItem(i, ItemStack.EMPTY);
            var recipe = client.level.getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("crafting_table")).orElseThrow();
            if (CraftingModuleTransfer.check(menu, recipe).canTransfer()) throw new IllegalStateException("Absent card accepted");
            TestTerminals.install(menu, ModItems.CRAFTING_MODULE.get());
            var slots = MyotusAPI.terminalModules().slots(menu, ModItems.CRAFTING_MODULE.get());
            for (var slot : slots) { slot.x = 20; slot.y = 20; }
            if (CraftingModuleTransfer.check(menu, recipe).canTransfer()) throw new IllegalStateException("Empty sources accepted");
            inventory.setItem(0, new ItemStack(Items.OAK_PLANKS, 3));
            var partial = CraftingModuleTransfer.check(menu, recipe);
            if (!partial.canTransfer() || partial.missingSlots().size() != 1) {
                throw new IllegalStateException("Client preflight failed to reserve repeated ingredients: " + partial);
            }
            inventory.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
            if (!CraftingModuleTransfer.check(menu, recipe).missingSlots().isEmpty()) {
                throw new IllegalStateException("Complete recipe marked missing");
            }
            if (jei) {
                @SuppressWarnings("unchecked") var crafting = (RecipeHolder<CraftingRecipe>) recipe;
                JeiSmoke.check(menu, crafting);
            }
            var screen = new MEStorageScreen<>(menu, inventory, Component.literal("Module transfer verification"),
                    StyleManager.loadStyleDoc("/screens/terminals/terminal.json"));
            screen.init(client, Math.max(960, client.getWindow().getGuiScaledWidth()),
                    Math.max(540, client.getWindow().getGuiScaledHeight()));
            var modules = ((TerminalModuleScreen) screen).myotus$getModuleWidgets();
            if (modules.getModuleWidgets().size() != 1) {
                throw new IllegalStateException("Expected the actual screen's single crafting module");
            }
            modules.getModuleWidgets().getFirst().updateBeforeRender();
            var routed = TerminalModuleRecipeTransfers.route(screen, recipe, false, false);
            if (!routed.canTransfer() || routed.sent() || routed.target() != ModItems.CRAFTING_MODULE.get()) {
                throw new IllegalStateException("Shared router did not choose the sole crafting module: " + routed);
            }
            var bounds = modules.getModuleWidgets().getFirst().getBounds();
            modules.onMouseDown(new Point(bounds.getX() + 2, bounds.getY() + 2), 1);
            if (modules.getLastClickedModuleCard() != ModItems.CRAFTING_MODULE.get()) {
                throw new IllegalStateException("Actual screen module click did not select its card");
            }
            var selected = TerminalModuleRecipeTransfers.route(screen, recipe, true, false);
            if (!selected.canTransfer() || selected.sent() || selected.target() != routed.target()) {
                throw new IllegalStateException("Selected-module max-transfer preview changed routing");
            }
            if (inventory.getItem(0).getCount() != 4 || slots.stream().anyMatch(slot -> slot.hasItem())) {
                throw new IllegalStateException("Recipe simulation mutated input inventories");
            }
            if (jei) JeiSmoke.checkEntryPoint(screen, recipe);
            slots.getFirst().x = -10000;
            if (CraftingModuleTransfer.check(menu, recipe).canTransfer()) throw new IllegalStateException("Hidden panel accepted");
            client.options.guiScale().set(1);
            client.resizeDisplay();
            checkWorkstations(client, menu, jei, emi);
            LogUtils.getLogger().info("ATM VIEWER PREFLIGHT PASS: missing/partial/complete sources, shared router sole/selected target, hidden panel, unchanged inventories; JEI registered handler={}", jei);
        } finally {
            player.containerMenu = previousMenu;
            for (int i = 0; i < previousStacks.size(); i++) inventory.setItem(i, previousStacks.get(i));
            client.setScreen(previousScreen);
            client.options.guiScale().set(previousScale);
            client.resizeDisplay();
        }
        return true;
    }

    private static void checkWorkstations(Minecraft client, MEStorageMenu menu, boolean jei, boolean emi) {
        var inventory = menu.getPlayerInventory();
        var stone = client.level.getRecipeManager().byKey(ResourceLocation.withDefaultNamespace(
                "stone_slab_from_stone_stonecutting")).orElseThrow();
        var smith = client.level.getRecipeManager().byKey(ResourceLocation.withDefaultNamespace(
                "netherite_sword_smithing")).orElseThrow();
        for (var kind : List.of(WorkstationKind.STONECUTTING, WorkstationKind.SMITHING)) {
            var recipe = kind == WorkstationKind.STONECUTTING ? stone : smith;
            for (int i = 0; i < inventory.getContainerSize(); i++) inventory.setItem(i, ItemStack.EMPTY);
            TestTerminals.cardSlot(menu).set(ItemStack.EMPTY);
            var screen = new MEStorageScreen<>(menu, inventory, Component.literal("Workstation viewer preflight"),
                    StyleManager.loadStyleDoc("/screens/terminals/terminal.json"));
            client.setScreen(screen);
            render(client, screen);
            preflight(screen, recipe, kind, false, -1, false, jei, emi);

            TestTerminals.install(menu, kind.card());
            render(client, screen);
            var modules = ((TerminalModuleScreen) screen).myotus$getModuleWidgets();
            var nativeSlots = MyotusAPI.terminalModules().slots(menu, kind.card());
            if (modules.getModuleWidgets().size() != 1 || nativeSlots.size() != kind.inputCount() + 1
                    || nativeSlots.stream().anyMatch(slot -> slot.x <= -1000 || slot.y <= -1000)) {
                throw new IllegalStateException("Actual screen did not place " + kind + " native slots");
            }
            preflight(screen, recipe, kind, false, -1, false, jei, emi);
            if (kind == WorkstationKind.SMITHING) {
                inventory.setItem(0, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2));
                inventory.setItem(1, new ItemStack(Items.DIAMOND_SWORD));
                preflight(screen, recipe, kind, true, 1, false, jei, emi);
                if (!CraftingModuleTransfer.check(menu, recipe).missingSlots().equals(java.util.Set.of(2))) {
                    throw new IllegalStateException("Smithing preflight lost template/base/addition order");
                }
                inventory.setItem(2, new ItemStack(Items.NETHERITE_INGOT, 3));
            } else {
                inventory.setItem(0, new ItemStack(Items.STONE, 4));
            }
            preflight(screen, recipe, kind, true, 0, false, jei, emi);
            var bounds = modules.getModuleWidgets().getFirst().getBounds();
            modules.onMouseDown(new Point(bounds.getX() + 2, bounds.getY() + 2), 1);
            if (modules.getLastClickedModuleCard() != kind.card()) {
                throw new IllegalStateException("Actual screen click did not select " + kind);
            }
            preflight(screen, recipe, kind, true, 0, true, jei, emi);
            preflight(screen, kind == WorkstationKind.STONECUTTING ? smith : stone,
                    kind == WorkstationKind.STONECUTTING ? WorkstationKind.SMITHING : WorkstationKind.STONECUTTING,
                    false, -1, false, jei, emi);
            int x = nativeSlots.getFirst().x;
            nativeSlots.getFirst().x = -10000;
            preflight(screen, recipe, kind, false, -1, false, jei, emi);
            nativeSlots.getFirst().x = x;
            if (nativeSlots.stream().anyMatch(slot -> slot.hasItem())) {
                throw new IllegalStateException("Workstation preflight populated native inputs or output");
            }
        }
        LogUtils.getLogger().info("ATM WORKSTATION VIEWER PREFLIGHT PASS: stonecutting/smithing absent, empty, partial smithing, complete, sole/selected Myotus router, wrong card, hidden panel, unchanged slots; registered JEI={}, registered EMI={}; simulation only", jei, emi);
    }

    private static void render(Minecraft client, MEStorageScreen<MEStorageMenu> screen) {
        // Run actual screen/widget placement before querying transfer availability.
        var graphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
        screen.render(graphics, 0, 0, 0);
        graphics.flush();
    }

    private static void preflight(MEStorageScreen<MEStorageMenu> screen, RecipeHolder<?> recipe,
            WorkstationKind kind, boolean canTransfer, int missing, boolean maxTransfer, boolean jei, boolean emi) {
        var menu = screen.getMenu();
        var inventory = menu.getPlayerInventory();
        List<ItemStack> before = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) before.add(inventory.getItem(i).copy());
        for (var slot : menu.slots) before.add(slot.getItem().copy());
        before.add(menu.getCarried().copy());
        var check = CraftingModuleTransfer.check(menu, recipe);
        if (check.canTransfer() != canTransfer || canTransfer && check.missingSlots().size() != missing) {
            throw new IllegalStateException(kind + " shared preflight mismatch: " + check);
        }
        var routed = TerminalModuleRecipeTransfers.route(screen, recipe, maxTransfer, false);
        boolean installed = MyotusAPI.terminalModules().isInstalled(menu, kind.card());
        if (routed.canTransfer() != canTransfer || routed.sent()
                || routed.target() != (installed ? kind.card() : null)) {
            throw new IllegalStateException(kind + " actual Myotus router mismatch: " + routed);
        }
        if (jei) JeiSmoke.checkWorkstation(menu, recipe, CraftingModuleTransfer.isActive(menu, kind),
                canTransfer, missing, maxTransfer);
        if (emi) EmiChecks.check(screen, recipe, canTransfer, maxTransfer);
        int index = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (!ItemStack.matches(before.get(index++), inventory.getItem(i))) {
                throw new IllegalStateException(kind + " viewer simulation changed player inventory");
            }
        }
        for (var slot : menu.slots) {
            if (!ItemStack.matches(before.get(index++), slot.getItem())) {
                throw new IllegalStateException(kind + " viewer simulation changed a menu slot");
            }
        }
        if (!ItemStack.matches(before.get(index), menu.getCarried())) {
            throw new IllegalStateException(kind + " viewer simulation changed the cursor");
        }
    }

    // Keep optional EMI symbols isolated from JEI-only smoke runs.
    private static final class EmiChecks {
        private static boolean ready() {
            var manager = dev.emi.emi.api.EmiApi.getRecipeManager();
            return manager != null && !manager.getRecipes().isEmpty();
        }

        private static void check(MEStorageScreen<MEStorageMenu> screen, RecipeHolder<?> holder,
                boolean canTransfer, boolean maxTransfer) {
            var recipe = dev.emi.emi.api.EmiApi.getRecipeManager().getRecipes().stream()
                    .filter(value -> value.getBackingRecipe() != null && value.getBackingRecipe().id().equals(holder.id()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Registered EMI recipe missing: " + holder.id()));
            var handler = dev.emi.emi.registry.EmiRecipeFiller.getAllHandlers(screen).stream()
                    .filter(value -> value instanceof me.myogoo.appliedterminalmodules.compat.emi.CraftingModuleEmiTransferHandler<?>)
                    .findFirst().orElseThrow(() -> new IllegalStateException("Registered EMI workstation handler missing"));
            var context = new dev.emi.emi.api.recipe.handler.EmiCraftContext<>(screen, handler.getInventory(screen),
                    dev.emi.emi.api.recipe.handler.EmiCraftContext.Type.FILL_BUTTON,
                    dev.emi.emi.api.recipe.handler.EmiCraftContext.Destination.NONE, maxTransfer ? 64 : 1);
            if (handler.canCraft(recipe, context) != canTransfer || handler.getTooltip(recipe, context).isEmpty()) {
                throw new IllegalStateException("Registered EMI workstation canCraft/tooltip mismatch: " + holder.id());
            }
            if (canTransfer && dev.emi.emi.registry.EmiRecipeFiller.getFirstValidHandler(recipe, screen) != handler) {
                throw new IllegalStateException("EMI did not select its registered workstation handler");
            }
        }
    }
}
