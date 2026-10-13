package me.myogoo.appliedterminalmodules.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.network.WorkstationPackets;
import me.myogoo.myotus.api.MyotusAPI;
import me.myogoo.myotus.api.terminal.ITerminalModuleProvider;
import me.myogoo.myotus.api.terminal.TerminalModuleSlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;

/** Native workstation inventories belong to this viewer, with inputs before the guarded result slot. */
public final class WorkstationModuleProvider implements ITerminalModuleProvider {
    private final WorkstationKind kind;

    public WorkstationModuleProvider(WorkstationKind kind) {
        this.kind = kind;
    }

    @Override
    public List<? extends Slot> createSlots(MEStorageMenu menu) {
        var state = new WorkstationState(menu, kind);
        List<Slot> slots = new ArrayList<>(kind.inputCount() + 1);
        slots.addAll(state.delegate.slots.subList(0, kind.inputCount()));
        slots.add(new OutputSlot(state));
        return slots;
    }

    @Override
    public ItemStack getCloneStack(Slot slot) {
        return slot instanceof OutputSlot ? ItemStack.EMPTY : ITerminalModuleProvider.super.getCloneStack(slot);
    }

    public static void refresh(MEStorageMenu menu, WorkstationKind kind) {
        var state = state(menu, kind);
        if (state == null || menu.isClientSide()
                || kind != WorkstationKind.STONECUTTING && kind != WorkstationKind.ANVIL) return;
        var selected = selectedRecipe(menu);
        int cost = anvilCost(menu);
        if (!state.synced || !Objects.equals(state.lastSelected, selected) || state.lastCost != cost) {
            state.synced = true;
            state.lastSelected = selected;
            state.lastCost = cost;
            WorkstationPackets.sync(menu);
        }
    }

    /** Cleanup also works after removal and before the card's first tick. Only inputs are returned. */
    public static void returnInputs(MEStorageMenu menu, WorkstationKind kind) {
        var state = state(menu, kind);
        if (state == null || menu.isClientSide()) return;
        Container inputs = state.delegate.slots.getFirst().container;
        var player = (ServerPlayer) menu.getPlayer();
        boolean changed = false;
        for (int i = 0; i < kind.inputCount(); i++) {
            ItemStack stack = inputs.removeItemNoUpdate(i);
            if (stack.isEmpty()) continue;
            changed = true;
            if (!player.isAlive() || player.hasDisconnected()) {
                player.drop(stack, false);
            } else {
                menu.getPlayerInventory().placeItemBackInInventory(stack);
            }
        }
        if (changed) state.delegate.slotsChanged(inputs);
        state.output.set(ItemStack.EMPTY);
    }

    public static boolean selectRecipe(MEStorageMenu menu, ResourceLocation recipeId) {
        var state = state(menu, WorkstationKind.STONECUTTING);
        if (!canChange(state) || recipeId == null) return false;
        var stonecutter = (StonecutterMenu) state.delegate;
        var recipes = stonecutter.getRecipes();
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i).id().equals(recipeId)) {
                if (stonecutter.getSelectedRecipeIndex() == i) return false;
                stonecutter.clickMenuButton(menu.getPlayer(), i);
                refresh(menu, WorkstationKind.STONECUTTING);
                return true;
            }
        }
        return false;
    }

    public static boolean rename(MEStorageMenu menu, String name) {
        var state = state(menu, WorkstationKind.ANVIL);
        if (!canChange(state) || name == null || !state.delegate.slots.getFirst().hasItem()) return false;
        boolean changed = ((AnvilMenu) state.delegate).setItemName(name);
        if (changed) refresh(menu, WorkstationKind.ANVIL);
        return changed;
    }

    public static List<RecipeHolder<StonecutterRecipe>> recipes(MEStorageMenu menu) {
        var state = state(menu, WorkstationKind.STONECUTTING);
        if (state == null) return List.of();
        if (!menu.isClientSide()) return List.copyOf(((StonecutterMenu) state.delegate).getRecipes());
        // Client input synchronization must never invoke native result recomputation or clear the server preview.
        var input = state.delegate.slots.getFirst().getItem();
        if (input.isEmpty()) return List.of();
        var level = menu.getPlayer().level();
        return level.getRecipeManager().getRecipesFor(RecipeType.STONECUTTING, new SingleRecipeInput(input), level);
    }

    public static @Nullable ResourceLocation selectedRecipe(MEStorageMenu menu) {
        var state = state(menu, WorkstationKind.STONECUTTING);
        if (state == null) return null;
        if (menu.isClientSide()) return state.clientSelected;
        var stonecutter = (StonecutterMenu) state.delegate;
        int index = stonecutter.getSelectedRecipeIndex();
        return index >= 0 && index < stonecutter.getRecipes().size() ? stonecutter.getRecipes().get(index).id() : null;
    }

    public static int anvilCost(MEStorageMenu menu) {
        var state = state(menu, WorkstationKind.ANVIL);
        return state == null ? 0 : ((AnvilMenu) state.delegate).getCost();
    }

    public static void applyClientState(MEStorageMenu menu, @Nullable ResourceLocation selectedRecipeId, int anvilCost) {
        if (!menu.isClientSide()) return;
        var stonecutter = state(menu, WorkstationKind.STONECUTTING);
        if (stonecutter != null) stonecutter.clientSelected = selectedRecipeId;
        var anvil = state(menu, WorkstationKind.ANVIL);
        if (anvil != null) ((AnvilMenu) anvil.delegate).setMaximumCost(Math.max(0, anvilCost));
    }

    private static boolean canChange(@Nullable WorkstationState state) {
        if (state == null || state.menu.isClientSide()) return false;
        var player = state.menu.getPlayer();
        return player.isAlive() && !player.isSpectator() && player.containerMenu == state.menu
                && state.menu.stillValid(player) && MyotusAPI.terminalModules().isInstalled(state.menu, state.kind.card());
    }

    private static @Nullable WorkstationState state(MEStorageMenu menu, WorkstationKind kind) {
        var slots = MyotusAPI.terminalModules().slots(menu, kind.card());
        if (slots.isEmpty() || !(slots.getLast() instanceof TerminalModuleSlot slot)
                || !(slot.getDelegate() instanceof OutputSlot output)) return null;
        return output.state;
    }

    private static final class WorkstationState {
        private final MEStorageMenu menu;
        private final WorkstationKind kind;
        private final AbstractContainerMenu delegate;
        private final Slot output;
        private boolean synced;
        private @Nullable ResourceLocation lastSelected;
        private int lastCost;
        private @Nullable ResourceLocation clientSelected;

        private WorkstationState(MEStorageMenu menu, WorkstationKind kind) {
            this.menu = menu;
            this.kind = kind;
            var inventory = menu.getPlayerInventory();
            // Grindstone's NeoForge take hook consumes inputs inside this access callback.
            var access = menu.isClientSide() ? ContainerLevelAccess.NULL
                    : ContainerLevelAccess.create(menu.getPlayer().level(), menu.getPlayer().blockPosition());
            delegate = switch (kind) {
                case STONECUTTING -> new StonecutterMenu(menu.containerId, inventory, access) {
                    @Override
                    public void slotsChanged(Container container) {
                        if (!menu.isClientSide()) super.slotsChanged(container);
                    }
                };
                case ANVIL -> new AnvilMenu(menu.containerId, inventory, ContainerLevelAccess.NULL) {
                    private ItemStack previousInput = ItemStack.EMPTY;

                    @Override
                    public void slotsChanged(Container container) {
                        if (menu.isClientSide()) return;
                        ItemStack input = getSlot(0).getItem();
                        if (!ItemStack.matches(previousInput, input)) {
                            previousInput = input.copy();
                            // Vanilla AnvilScreen supplies this name on input changes; preserve it without that screen.
                            if (setItemName(input.isEmpty() ? "" : input.getHoverName().getString())) return;
                        }
                        super.slotsChanged(container);
                    }

                    @Override
                    public void createResult() {
                        if (!menu.isClientSide()) super.createResult();
                    }
                };
                case SMITHING -> new SmithingMenu(menu.containerId, inventory, access) {
                    @Override
                    public void slotsChanged(Container container) {
                        if (!menu.isClientSide()) super.slotsChanged(container);
                    }
                };
                case GRINDSTONE -> new GrindstoneMenu(menu.containerId, inventory, access) {
                    @Override
                    public void slotsChanged(Container container) {
                        if (!menu.isClientSide()) super.slotsChanged(container);
                    }
                };
            };
            output = delegate.slots.get(kind.inputCount());
        }
    }

    private static final class OutputSlot extends Slot {
        private final WorkstationState state;

        private OutputSlot(WorkstationState state) {
            super(state.output.container, state.output.getContainerSlot(), -10000, -10000);
            this.state = state;
        }

        @Override
        public boolean mayPlace(ItemStack stack) { return false; }

        @Override
        public boolean mayPickup(Player player) {
            if (player != state.menu.getPlayer()) return false;
            if (!state.menu.isClientSide()) return state.output.mayPickup(player);
            // Smithing's selected recipe is server-owned. Client predictions use its authoritative preview.
            return hasItem() && (state.kind != WorkstationKind.ANVIL
                    || anvilCost(state.menu) > 0 && (player.hasInfiniteMaterials()
                            || player.experienceLevel >= anvilCost(state.menu)));
        }

        @Override
        public Optional<ItemStack> tryRemove(int amount, int limit, Player player) {
            int batch = getItem().getCount();
            if (amount <= 0 || batch == 0 || limit < batch) return Optional.empty();
            return super.tryRemove(batch, limit, player);
        }

        @Override
        public ItemStack safeTake(int amount, int limit, Player player) {
            if (amount < getItem().getCount()) return ItemStack.EMPTY;
            return super.safeTake(amount, limit, player);
        }

        @Override
        public ItemStack remove(int amount) {
            return amount <= 0 || amount < getItem().getCount() ? ItemStack.EMPTY : state.output.remove(amount);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            if (!state.menu.isClientSide()) state.output.onTake(player, stack);
        }

        @Override
        public void onQuickCraft(ItemStack oldStack, ItemStack newStack) {
            if (!state.menu.isClientSide()) state.output.onQuickCraft(oldStack, newStack);
        }
    }
}
