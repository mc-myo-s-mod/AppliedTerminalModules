package me.myogoo.appliedterminalmodules.compat.accessories;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.me.common.MEStorageMenu;
import com.mojang.datafixers.util.Pair;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.menu.AccessoriesBasedSlot;
import io.wispforest.accessories.data.EntitySlotLoader;
import io.wispforest.accessories.networking.AccessoriesNetworking;
import io.wispforest.accessories.networking.client.SyncEntireContainer;
import me.myogoo.appliedterminalmodules.init.ModItems;
import me.myogoo.myotus.api.MyotusAPI;
import me.myogoo.myotus.api.terminal.ITerminalModuleProvider;
import me.myogoo.myotus.api.terminal.TerminalModuleSlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Instantiate only inside the Accessories loaded guard. Functional contents stay owned by Accessories. */
public final class AccessoriesModuleProvider implements ITerminalModuleProvider {
    private record SlotKey(String identifier, int index) {}

    @Override
    public List<? extends Slot> createSlots(MEStorageMenu menu) {
        return layout(menu).stream().map(key -> new ModuleSlot(menu, key)).toList();
    }

    @Override
    public void beforeMenuOpen(ServerPlayer player) {
        SyncEntireContainer.syncTo(player, packet -> AccessoriesNetworking.sendToPlayer(player, packet));
    }

    @Override
    public boolean isLayoutValid(MEStorageMenu menu) {
        var expected = layout(menu);
        var slots = MyotusAPI.terminalModules().slots(menu, ModItems.ACCESSORIES_MODULE.get());
        if (expected.size() != slots.size()) return false;
        for (int i = 0; i < expected.size(); i++) {
            var nativeSlot = ((TerminalModuleSlot) slots.get(i)).getDelegate();
            if (!(nativeSlot instanceof ModuleSlot moduleSlot) || !expected.get(i).equals(moduleSlot.key)
                    || moduleSlot.delegate() == null) return false;
        }
        return true;
    }

    @Override
    public ItemStack getCloneStack(Slot slot) {
        var moduleSlot = (ModuleSlot) slot;
        if (!moduleSlot.allowModification(moduleSlot.menu.getPlayer())) return ItemStack.EMPTY;
        // Accessories has no Curios-style clone extension: retain vanilla creative clone semantics.
        var stack = moduleSlot.getItem();
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(stack.getMaxStackSize());
    }

    private static List<SlotKey> layout(MEStorageMenu menu) {
        List<SlotKey> slots = new ArrayList<>();
        var capability = AccessoriesCapability.get(menu.getPlayer());
        if (capability == null) return slots;
        var containers = capability.getContainers();
        EntitySlotLoader.getEntitySlots(menu.getPlayer()).values().stream()
                .sorted(Comparator.comparingInt(io.wispforest.accessories.api.slot.SlotType::order)
                        .thenComparing(io.wispforest.accessories.api.slot.SlotType::name))
                .forEach(type -> {
                    var container = containers.get(type.name());
                    if (container == null) return;
                    for (int i = 0; i < container.getAccessories().getContainerSize(); i++) {
                        slots.add(new SlotKey(type.name(), i));
                    }
                });
        return slots;
    }

    private static final class ModuleSlot extends Slot {
        private final MEStorageMenu menu;
        private final SlotKey key;
        private AccessoriesBasedSlot delegate;

        private ModuleSlot(MEStorageMenu menu, SlotKey key) {
            super(new SimpleContainer(0), key.index(), -10000, -10000);
            this.menu = menu;
            this.key = key;
        }

        private AccessoriesBasedSlot delegate() {
            var capability = AccessoriesCapability.get(menu.getPlayer());
            AccessoriesContainer current = capability == null ? null : capability.getContainers().get(key.identifier());
            if (current == null || !EntitySlotLoader.getEntitySlots(menu.getPlayer()).containsKey(key.identifier())
                    || current.slotType() == null || key.index() >= current.getAccessories().getContainerSize()) {
                return null;
            }
            // Full sync and resizing can replace either the holder's container or its functional inventory.
            if (delegate == null || delegate.accessoriesContainer != current
                    || delegate.container != current.getAccessories()) {
                delegate = new AccessoriesBasedSlot(current, current.getAccessories(), key.index(), 0, 0);
            }
            return delegate;
        }

        @Override
        public boolean isActive() {
            return delegate() != null && MyotusAPI.terminalModules().isInstalled(menu, ModItems.ACCESSORIES_MODULE.get());
        }

        @Override
        public boolean allowModification(Player player) {
            if (player != menu.getPlayer() || !isActive()) return false;
            var stack = getItem();
            return !(menu.getHost() instanceof ItemMenuHost<?> host && !stack.isEmpty()
                    && stack == host.getItemStack());
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return allowModification(menu.getPlayer()) && delegate().mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return allowModification(player) && delegate().mayPickup(player);
        }

        @Override
        public ItemStack getItem() {
            var slot = delegate();
            return slot == null ? ItemStack.EMPTY : slot.getItem();
        }

        // Authoritative packet updates must work with the card removed as well.
        @Override
        public void set(ItemStack stack) {
            var slot = delegate();
            if (slot != null) slot.set(stack);
        }

        @Override
        public void setChanged() {
            var slot = delegate();
            if (slot != null) slot.setChanged();
        }

        @Override
        public void setByPlayer(ItemStack stack, ItemStack previous) {
            if (allowModification(menu.getPlayer())) delegate().setByPlayer(stack, previous);
        }

        @Override
        public ItemStack remove(int amount) {
            return mayPickup(menu.getPlayer()) ? delegate().remove(amount) : ItemStack.EMPTY;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            var slot = delegate();
            if (slot != null) slot.onTake(player, stack);
        }

        @Override
        public void onQuickCraft(ItemStack oldStack, ItemStack newStack) {
            var slot = delegate();
            if (slot != null) slot.onQuickCraft(oldStack, newStack);
        }

        @Override
        public int getMaxStackSize() {
            var slot = delegate();
            return slot == null ? 0 : slot.getMaxStackSize();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            var slot = delegate();
            return slot == null ? 0 : slot.getMaxStackSize(stack);
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            var slot = delegate();
            return slot == null ? null : slot.getNoItemIcon();
        }
    }
}
