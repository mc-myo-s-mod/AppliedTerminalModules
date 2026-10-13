package me.myogoo.appliedterminalmodules.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.init.ModItems;
import me.myogoo.myotus.api.MyotusAPI;
import me.myogoo.myotus.api.terminal.ITerminalModuleProvider;
import me.myogoo.myotus.api.terminal.TerminalModuleSlot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

/** Each open terminal owns its grid; no terminal-host inventory or ME extraction is required. */
public final class CraftingModuleProvider implements ITerminalModuleProvider {
    @Override
    public List<? extends Slot> createSlots(MEStorageMenu menu) {
        var state = new CraftingState(menu);
        List<Slot> slots = new ArrayList<>(10);
        for (int i = 0; i < 9; i++) {
            slots.add(new Slot(state.grid, i, -10000, -10000));
        }
        slots.add(new CraftingOutputSlot(state));
        return slots;
    }

    public static void refresh(MEStorageMenu menu) {
        var state = state(menu);
        if (state != null) state.updateResult();
    }

    /** Removal callbacks receive a card snapshot, so return the menu's inputs instead of editing the card. */
    public static void returnIngredients(MEStorageMenu menu) {
        var state = state(menu);
        if (state == null || menu.isClientSide()) return;
        state.updating = true;
        try {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = state.grid.removeItemNoUpdate(i);
                if (stack.isEmpty()) continue;
                var player = (ServerPlayer) menu.getPlayer();
                if (!player.isAlive() || player.hasDisconnected()) {
                    player.drop(stack, false);
                } else {
                    menu.getPlayerInventory().placeItemBackInInventory(stack);
                }
            }
        } finally {
            state.updating = false;
            state.updateResult();
        }
    }

    private static CraftingState state(MEStorageMenu menu) {
        var slots = MyotusAPI.terminalModules().slots(menu, ModItems.CRAFTING_MODULE.get());
        if (slots.isEmpty()) return null;
        var output = (TerminalModuleSlot) slots.getLast();
        return ((CraftingOutputSlot) output.getDelegate()).state;
    }

    private static final class CraftingGrid extends SimpleContainer implements CraftingContainer {
        private CraftingGrid() { super(9); }

        @Override
        public int getWidth() { return 3; }

        @Override
        public int getHeight() { return 3; }
    }

    private static final class CraftingState {
        private final MEStorageMenu menu;
        private final CraftingGrid grid = new CraftingGrid();
        private final ResultContainer result = new ResultContainer();
        private boolean updating;

        private CraftingState(MEStorageMenu menu) {
            this.menu = menu;
            grid.addListener(container -> updateResult());
        }

        private void updateResult() {
            // The client receives the authoritative preview through the permanent result slot.
            if (updating || menu.isClientSide()) return;
            var level = menu.getPlayer().level();
            var input = grid.asCraftInput();
            var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
            ItemStack output = ItemStack.EMPTY;
            result.setRecipeUsed(null);
            if (recipe != null && result.setRecipeUsed(level, (ServerPlayer) menu.getPlayer(), recipe)) {
                output = recipe.value().assemble(input, level.registryAccess());
                if (!output.isItemEnabled(level.enabledFeatures())) output = ItemStack.EMPTY;
            }
            result.setItem(0, output);
        }
    }

    private static final class CraftingOutputSlot extends ResultSlot {
        private final CraftingState state;
        private boolean extracted;

        private CraftingOutputSlot(CraftingState state) {
            super(state.menu.getPlayer(), state.grid, state.result, 0, -10000, -10000);
            this.state = state;
        }

        @Override
        public Optional<ItemStack> tryRemove(int count, int limit, Player player) {
            state.updateResult();
            int batch = getItem().getCount();
            if (count <= 0 || batch == 0 || limit < batch) return Optional.empty();
            // Right-click takes a complete recipe, as ResultContainer does in the vanilla crafting menu.
            return super.tryRemove(batch, limit, player);
        }

        @Override
        public ItemStack safeTake(int count, int limit, Player player) {
            state.updateResult();
            // Myotus shift-click supplies the available inventory capacity as count, not limit.
            if (count < getItem().getCount()) return ItemStack.EMPTY;
            return super.safeTake(count, limit, player);
        }

        @Override
        public ItemStack remove(int count) {
            if (count <= 0 || count < getItem().getCount()) return ItemStack.EMPTY;
            ItemStack taken = super.remove(count);
            extracted = !taken.isEmpty();
            return taken;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            state.updating = true;
            try {
                // Vanilla number-key transfer skips remove(); Myotus's wrapper cannot forward onSwapCraft.
                if (!extracted) super.onQuickCraft(stack, stack.getCount());
                super.onTake(player, stack);
            } finally {
                extracted = false;
                state.updating = false;
                state.updateResult();
            }
        }
    }
}
