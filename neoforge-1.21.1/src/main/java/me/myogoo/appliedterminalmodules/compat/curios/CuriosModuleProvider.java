package me.myogoo.appliedterminalmodules.compat.curios;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.me.common.MEStorageMenu;
import com.mojang.datafixers.util.Pair;
import me.myogoo.appliedterminalmodules.init.ModItems;
import me.myogoo.myotus.api.MyotusAPI;
import me.myogoo.myotus.api.terminal.ITerminalModuleProvider;
import me.myogoo.myotus.api.terminal.TerminalModuleSlot;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.inventory.CurioSlot;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncCurios;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Instantiate only inside the Curios loaded guard. Contents remain owned by Curios. */
public final class CuriosModuleProvider implements ITerminalModuleProvider {
    private record SlotKey(String identifier, int index) {}

    @Override
    public List<? extends Slot> createSlots(MEStorageMenu menu) {
        return layout(menu).stream().map(key -> new ModuleSlot(menu, key)).toList();
    }

    @Override
    public void beforeMenuOpen(ServerPlayer player) {
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> PacketDistributor.sendToPlayer(player,
                new SPacketSyncCurios(player.getId(), handler.getCurios())));
    }

    @Override
    public boolean isLayoutValid(MEStorageMenu menu) {
        var expected = layout(menu);
        var slots = MyotusAPI.terminalModules().slots(menu, ModItems.CURIOS_MODULE.get());
        if (expected.size() != slots.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            var nativeSlot = ((TerminalModuleSlot) slots.get(i)).getDelegate();
            if (!(nativeSlot instanceof ModuleSlot moduleSlot) || !expected.get(i).equals(moduleSlot.key)
                    || moduleSlot.delegate() == null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getCloneStack(Slot slot) {
        var moduleSlot = (ModuleSlot) slot;
        if (!moduleSlot.allowModification(moduleSlot.menu.getPlayer())) {
            return ItemStack.EMPTY;
        }
        var nativeSlot = moduleSlot.delegate();
        var stack = nativeSlot.getSlotExtension()
                .getCloneStack(nativeSlot.getSlotContext(), nativeSlot.getItem());
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(stack.getMaxStackSize());
    }

    private static List<SlotKey> layout(MEStorageMenu menu) {
        List<SlotKey> slots = new ArrayList<>();
        CuriosApi.getCuriosInventory(menu.getPlayer()).ifPresent(inventory -> inventory.getCurios().entrySet()
                .stream().filter(entry -> entry.getValue().isVisible())
                .sorted(Comparator.<Map.Entry<String, ICurioStacksHandler>>comparingInt(entry ->
                        CuriosApi.getSlot(entry.getKey(), menu.getPlayer().level())
                                .map(type -> type.getOrder()).orElse(0)).thenComparing(Map.Entry::getKey))
                .forEach(entry -> {
                    for (int i = 0; i < entry.getValue().getStacks().getSlots(); i++) {
                        slots.add(new SlotKey(entry.getKey(), i));
                    }
                }));
        return slots;
    }

    private static final class ModuleSlot extends Slot {
        private final MEStorageMenu menu;
        private final SlotKey key;
        private ICurioStacksHandler handler;
        private CurioSlot delegate;
        private NonNullList<Boolean> renders;
        private List<Boolean> activeStates;

        private ModuleSlot(MEStorageMenu menu, SlotKey key) {
            super(new SimpleContainer(0), key.index(), -10000, -10000);
            this.menu = menu;
            this.key = key;
        }

        private CurioSlot delegate() {
            var current = CuriosApi.getCuriosInventory(menu.getPlayer())
                    .map(inventory -> inventory.getCurios().get(key.identifier())).orElse(null);
            if (current == null || !current.isVisible() || key.index() >= current.getStacks().getSlots()) {
                return null;
            }
            // Full sync can replace handlers and their state lists without changing packet slot indices.
            if (delegate == null || current != handler || delegate.getItemHandler() != current.getStacks()
                    || renders != current.getRenders() || activeStates != current.getActiveStates()
                    || delegate.canToggleRender() != current.canToggleRendering()) {
                handler = current;
                renders = current.getRenders();
                activeStates = current.getActiveStates();
                delegate = new CurioSlot(menu.getPlayer(), current.getStacks(), key.index(), key.identifier(),
                        0, 0, renders, activeStates, current.canToggleRendering(), false, false);
            }
            return delegate;
        }

        @Override
        public boolean isActive() {
            return delegate() != null && MyotusAPI.terminalModules().isInstalled(menu, ModItems.CURIOS_MODULE.get());
        }

        @Override
        public boolean allowModification(Player player) {
            if (player != menu.getPlayer() || !isActive()) {
                return false;
            }
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

        // Packet synchronization must update live contents even while the module card is absent.
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
            return allowModification(menu.getPlayer()) ? delegate().remove(amount) : ItemStack.EMPTY;
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
