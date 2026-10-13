package me.myogoo.appliedterminalmodules.gametest;

import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.AppliedTerminalModules;
import me.myogoo.appliedterminalmodules.compat.accessories.AccessoriesModuleProvider;
import me.myogoo.appliedterminalmodules.init.ModItems;
import me.myogoo.appliedterminalmodules.menu.ModuleSemantics;
import me.myogoo.myotus.api.MyotusAPI;
import me.myogoo.myotus.api.terminal.TerminalModuleSlot;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@GameTestHolder(AppliedTerminalModules.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AccessoriesModuleGameTests {
    private AccessoriesModuleGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void accessoriesAvailabilityMatchesOptionalDependency(GameTestHelper helper) {
        boolean loaded = ModList.get().isLoaded("accessories");
        var menu = TestTerminals.create(helper).menu();
        helper.assertTrue(ModItems.ACCESSORIES_MODULE.get().isAvailable() == loaded,
                "Accessories card availability follows the optional dependency");
        if (!loaded) {
            helper.assertTrue(!TestTerminals.cardSlot(menu).mayPlace(ModItems.ACCESSORIES_MODULE.toStack())
                    && MyotusAPI.terminalModules().slots(menu, ModItems.ACCESSORIES_MODULE.get()).isEmpty(),
                    "Without Accessories the card is not installable and no optional provider loads");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void accessoriesUsesOrderedFunctionalSlotsAndNativeRestrictions(GameTestHelper helper) {
        if (ModList.get().isLoaded("accessories")) AccessoriesChecks.validation(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void accessoriesRebindsReplacementAndPreservesGearOnCardRemoval(GameTestHelper helper) {
        if (ModList.get().isLoaded("accessories")) AccessoriesChecks.replacementAndRemoval(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void accessoriesShrinkDisablesStaleSlotsAndClosesMenu(GameTestHelper helper) {
        if (ModList.get().isLoaded("accessories")) AccessoriesChecks.shrink(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void accessoriesRejectsSameCountIdentifierReplacement(GameTestHelper helper) {
        if (ModList.get().isLoaded("accessories")) AccessoriesChecks.identifierReplacement(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void accessoriesCloneCopiesVanillaStackAndProtectsWirelessHost(GameTestHelper helper) {
        if (ModList.get().isLoaded("accessories")) AccessoriesChecks.cloneAndHostLock(helper);
        helper.succeed();
    }

    /** Keep optional types out of the outer class, which is scanned with Accessories absent too. */
    private static final class AccessoriesChecks {
        private static final String PRIMARY = "appliedterminalmodules_test_a";
        private static final String OTHER = "appliedterminalmodules_test_z";
        private static final io.wispforest.accessories.api.Accessory RESTRICTED =
                new io.wispforest.accessories.api.Accessory() {
                    @Override
                    public boolean canEquip(ItemStack stack, io.wispforest.accessories.api.slot.SlotReference reference) {
                        return false;
                    }

                    @Override
                    public boolean canUnequip(ItemStack stack, io.wispforest.accessories.api.slot.SlotReference reference) {
                        return false;
                    }

                    @Override
                    public int maxStackSize(ItemStack stack) { return 2; }
                };

        private record Fixture(ServerPlayer player, MEStorageMenu menu,
                io.wispforest.accessories.api.AccessoriesCapability capability,
                Map<String, io.wispforest.accessories.api.AccessoriesContainer> containers,
                io.wispforest.accessories.impl.AccessoriesContainerImpl primary) {}

        private static io.wispforest.accessories.api.slot.SlotType type(String name, int amount) {
            return new io.wispforest.accessories.impl.SlotTypeImpl(name,
                    io.wispforest.accessories.api.slot.SlotType.EMPTY_SLOT_ICON, 0, amount,
                    Set.of(ResourceLocation.fromNamespaceAndPath("accessories", "all")),
                    io.wispforest.accessories.api.DropRule.DEFAULT);
        }

        @SuppressWarnings("unchecked")
        private static void withSlots(GameTestHelper helper, Consumer<Fixture> check) {
            var typeLoader = io.wispforest.accessories.data.SlotTypeLoader.INSTANCE;
            var entityLoader = io.wispforest.accessories.data.EntitySlotLoader.INSTANCE;
            var originalTypes = field(typeLoader, "server");
            var originalEntities = field(entityLoader, "server");
            var registry = (Map<net.minecraft.world.item.Item, io.wispforest.accessories.api.Accessory>)
                    field(null, io.wispforest.accessories.api.AccessoriesAPI.class, "REGISTER");
            var originalAccessory = registry.get(Items.DIAMOND);
            boolean hadAccessory = registry.containsKey(Items.DIAMOND);
            try {
                var primary = type(PRIMARY, 2);
                // Deliberately reversed insertion order: equal-priority slot types must sort by name.
                var types = new LinkedHashMap<String, io.wispforest.accessories.api.slot.SlotType>();
                types.put(OTHER, type(OTHER, 1));
                types.put(PRIMARY, primary);
                var allTypes = new LinkedHashMap<>((Map<String, io.wispforest.accessories.api.slot.SlotType>) originalTypes);
                allTypes.putAll(types);
                setField(typeLoader, "server", allTypes);
                var entityTypes = new LinkedHashMap<>((Map<EntityType<?>, Map<String,
                        io.wispforest.accessories.api.slot.SlotType>>) originalEntities);
                entityTypes.put(EntityType.PLAYER, types);
                setField(entityLoader, "server", entityTypes);
                io.wispforest.accessories.impl.AccessoriesCapabilityImpl.clearValidationCache(false);
                var player = TestTerminals.player(helper);
                var capability = io.wispforest.accessories.api.AccessoriesCapability.get(player);
                helper.assertTrue(capability != null, "Player must have an Accessories capability");
                var containers = (Map<String, io.wispforest.accessories.api.AccessoriesContainer>)
                        field(capability.getHolder(), "slotContainers");
                var container = (io.wispforest.accessories.impl.AccessoriesContainerImpl)
                        capability.getContainers().get(PRIMARY);
                helper.assertTrue(container != null, "Synthetic native Accessories containers initialize");
                io.wispforest.accessories.api.AccessoriesAPI.registerAccessory(Items.DIAMOND, RESTRICTED);
                check.accept(new Fixture(player, TestTerminals.menu(helper, player), capability, containers, container));
            } finally {
                setField(typeLoader, "server", originalTypes);
                setField(entityLoader, "server", originalEntities);
                io.wispforest.accessories.impl.AccessoriesCapabilityImpl.clearValidationCache(false);
                if (hadAccessory) registry.put(Items.DIAMOND, originalAccessory);
                else registry.remove(Items.DIAMOND);
            }
        }

        private static void validation(GameTestHelper helper) {
            withSlots(helper, fixture -> {
                var menu = fixture.menu();
                var slots = menu.getSlots(ModuleSemantics.ACCESSORIES);
                helper.assertTrue(slots.size() == 3 && slots.stream().noneMatch(Slot::isActive),
                        "Three functional slots, without cosmetics, remain registered but inactive without the card");
                fixture.primary().getAccessories().setItem(0, new ItemStack(Items.IRON_INGOT));
                fixture.primary().getAccessories().setItem(1, new ItemStack(Items.GOLD_INGOT));
                fixture.primary().getCosmeticAccessories().setItem(0, new ItemStack(Items.EMERALD));
                fixture.containers().get(OTHER).getAccessories().setItem(0, new ItemStack(Items.REDSTONE));
                TestTerminals.install(menu, ModItems.ACCESSORIES_MODULE.get());
                helper.assertTrue(slots.get(0).getItem().is(Items.IRON_INGOT)
                        && slots.get(1).getItem().is(Items.GOLD_INGOT)
                        && slots.get(2).getItem().is(Items.REDSTONE)
                        && new AccessoriesModuleProvider().isLayoutValid(menu),
                        "Equal-priority types sort by identifier, then functional slot index");
                var slot = slots.getFirst();
                helper.assertTrue(slot.mayPlace(new ItemStack(Items.STICK))
                        && !slot.mayPlace(new ItemStack(Items.DIAMOND))
                        && slot.getMaxStackSize(new ItemStack(Items.DIAMOND)) == 2,
                        "Native Accessories canEquip and maxStackSize remain authoritative");
                fixture.primary().getAccessories().setItem(0, new ItemStack(Items.DIAMOND));
                helper.assertTrue(!slot.mayPickup(fixture.player()) && slot.remove(1).isEmpty(),
                        "Accessory-specific canUnequip blocks direct removal");
                var cursed = new ItemStack(Items.IRON_HELMET);
                cursed.enchant(fixture.player().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.BINDING_CURSE), 1);
                fixture.primary().getAccessories().setItem(0, cursed);
                menu.clicked(slot.index, 0, ClickType.PICKUP, fixture.player());
                menu.quickMoveStack(fixture.player(), slot.index);
                helper.assertTrue(!slot.mayPickup(fixture.player()) && slot.remove(1).isEmpty()
                        && ItemStack.matches(slot.getItem(), cursed) && menu.getCarried().isEmpty(),
                        "Binding curse blocks pickup, shift-click and direct removal");
            });
        }

        private static void replacementAndRemoval(GameTestHelper helper) {
            withSlots(helper, fixture -> {
                var menu = fixture.menu();
                var slots = menu.getSlots(ModuleSemantics.ACCESSORIES);
                int count = menu.slots.size();
                int index = slots.getFirst().index;
                TestTerminals.install(menu, ModItems.ACCESSORIES_MODULE.get());
                fixture.primary().getAccessories().setItem(0, new ItemStack(Items.IRON_INGOT));
                helper.assertTrue(slots.getFirst().getItem().is(Items.IRON_INGOT), "Bind initial live inventory");
                var replacement = new io.wispforest.accessories.impl.AccessoriesContainerImpl(fixture.capability(), type(PRIMARY, 2));
                replacement.getAccessories().setItem(0, new ItemStack(Items.GOLD_INGOT));
                fixture.containers().put(PRIMARY, replacement);
                helper.assertTrue(new AccessoriesModuleProvider().isLayoutValid(menu)
                        && slots.getFirst().getItem().is(Items.GOLD_INGOT),
                        "Same-layout full container replacement rebinds permanent slots");
                slots.getFirst().set(new ItemStack(Items.REDSTONE));
                helper.assertTrue(replacement.getAccessories().getItem(0).is(Items.REDSTONE)
                        && fixture.primary().getAccessories().getItem(0).is(Items.IRON_INGOT),
                        "Writes after replacement affect only the new live inventory");
                TestTerminals.cardSlot(menu).set(ItemStack.EMPTY);
                helper.assertTrue(slots.stream().noneMatch(Slot::isActive)
                        && slots.getFirst().remove(1).isEmpty() && !slots.getFirst().mayPlace(new ItemStack(Items.STICK)),
                        "Card removal disables stale slot IDs without deleting equipment");
                slots.getFirst().set(new ItemStack(Items.LAPIS_LAZULI));
                helper.assertTrue(replacement.getAccessories().getItem(0).is(Items.LAPIS_LAZULI)
                        && menu.slots.size() == count && slots.getFirst().index == index,
                        "Authoritative updates still write live contents with the card removed; packet indices stay fixed");
                TestTerminals.install(menu, ModItems.ACCESSORIES_MODULE.get());
                helper.assertTrue(slots.stream().allMatch(Slot::isActive), "Reinstall activates unchanged slots");
            });
        }

        private static void shrink(GameTestHelper helper) {
            withSlots(helper, fixture -> {
                var menu = fixture.menu();
                var slots = menu.getSlots(ModuleSemantics.ACCESSORIES);
                TestTerminals.install(menu, ModItems.ACCESSORIES_MODULE.get());
                fixture.primary().getAccessories().setItem(0, new ItemStack(Items.IRON_INGOT));
                var oldInventory = fixture.primary().getAccessories();
                fixture.primary().addTransientModifier(new AttributeModifier(
                        ResourceLocation.fromNamespaceAndPath(AppliedTerminalModules.MOD_ID, "test_shrink"),
                        -1, AttributeModifier.Operation.ADD_VALUE));
                helper.assertTrue(fixture.primary().getSize() == 1
                        && fixture.primary().getAccessories() != oldInventory
                        && slots.getFirst().getItem().is(Items.IRON_INGOT)
                        && !slots.get(1).isActive() && slots.get(1).getItem().isEmpty()
                        && slots.get(1).remove(1).isEmpty() && !new AccessoriesModuleProvider().isLayoutValid(menu),
                        "Native resize rebinds remaining inventory and disables the removed slot safely");
                TestTerminals.broadcast(helper, menu);
                helper.assertTrue(fixture.player().containerMenu == fixture.player().inventoryMenu,
                        "Changed Accessories slot count closes the menu instead of changing packet indices");
            });
        }

        private static void identifierReplacement(GameTestHelper helper) {
            withSlots(helper, fixture -> {
                var menu = fixture.menu();
                var slots = menu.getSlots(ModuleSemantics.ACCESSORIES);
                TestTerminals.install(menu, ModItems.ACCESSORIES_MODULE.get());
                fixture.primary().getAccessories().setItem(0, new ItemStack(Items.IRON_INGOT));
                String replacementName = PRIMARY + "_replacement";
                var replacementType = type(replacementName, 2);
                var types = io.wispforest.accessories.data.SlotTypeLoader.INSTANCE.getSlotTypes(false);
                types.put(replacementName, replacementType);
                var entityTypes = io.wispforest.accessories.data.EntitySlotLoader.getEntitySlots(fixture.player());
                entityTypes.remove(PRIMARY);
                entityTypes.put(replacementName, replacementType);
                fixture.containers().remove(PRIMARY);
                var replacement = new io.wispforest.accessories.impl.AccessoriesContainerImpl(fixture.capability(), replacementType);
                replacement.getAccessories().setItem(0, new ItemStack(Items.GOLD_INGOT));
                fixture.containers().put(replacementName, replacement);
                helper.assertTrue(!new AccessoriesModuleProvider().isLayoutValid(menu)
                        && !slots.getFirst().isActive() && slots.getFirst().getItem().isEmpty()
                        && slots.getFirst().remove(1).isEmpty()
                        && replacement.getAccessories().getItem(0).is(Items.GOLD_INGOT),
                        "Same-count identifier replacement invalidates layout and cannot mutate replacement gear");
                TestTerminals.broadcast(helper, menu);
                helper.assertTrue(fixture.player().containerMenu == fixture.player().inventoryMenu,
                        "Identifier replacement requests reopening");
            });
        }

        private static void cloneAndHostLock(GameTestHelper helper) {
            withSlots(helper, fixture -> {
                var menu = fixture.menu();
                TestTerminals.install(menu, ModItems.ACCESSORIES_MODULE.get());
                var slot = menu.getSlots(ModuleSemantics.ACCESSORIES).getFirst();
                var source = new ItemStack(Items.EMERALD);
                source.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        net.minecraft.network.chat.Component.literal("Accessories clone"));
                fixture.primary().getAccessories().setItem(0, source);
                fixture.player().getAbilities().instabuild = true;
                menu.clicked(slot.index, 2, ClickType.CLONE, fixture.player());
                helper.assertTrue(menu.getCarried() != source
                        && ItemStack.isSameItemSameComponents(menu.getCarried(), source)
                        && menu.getCarried().getCount() == source.getMaxStackSize() && source.getCount() == 1,
                        "Creative clone preserves components, copies at vanilla item max, and does not unequip");
                menu.setCarried(ItemStack.EMPTY);
                var hostStack = new ItemStack(Items.STICK);
                fixture.primary().getAccessories().setItem(0, hostStack);
                var lockedMenu = TestTerminals.menu(helper, fixture.player(), new LockedHost(fixture.player(), hostStack));
                TestTerminals.install(lockedMenu, ModItems.ACCESSORIES_MODULE.get());
                var locked = lockedMenu.getSlots(ModuleSemantics.ACCESSORIES).getFirst();
                for (var click : new ClickType[]{ClickType.PICKUP, ClickType.QUICK_MOVE, ClickType.SWAP,
                        ClickType.CLONE, ClickType.THROW, ClickType.PICKUP_ALL}) {
                    lockedMenu.clicked(locked.index, click == ClickType.CLONE ? 2 : 0, click, fixture.player());
                }
                helper.assertTrue(!locked.mayPickup(fixture.player()) && !locked.mayPlace(new ItemStack(Items.STICK))
                        && locked.remove(1).isEmpty() && lockedMenu.getCarried().isEmpty()
                        && locked.getItem() == hostStack
                        && new AccessoriesModuleProvider().getCloneStack(((TerminalModuleSlot) locked).getDelegate()).isEmpty(),
                        "An equipped wireless menu host cannot be picked up, shifted, swapped, thrown, or cloned");
            });
        }

        private static final class LockedHost extends TestTerminals.TestHost {
            private ItemStack stack;

            private LockedHost(ServerPlayer player, ItemStack stack) {
                super(player);
                this.stack = stack;
            }

            @Override
            public ItemStack getItemStack() { return stack == null ? super.getItemStack() : stack; }
        }

        private static Object field(Object owner, String name) { return field(owner, owner.getClass(), name); }

        private static Object field(Object owner, Class<?> type, String name) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot prepare native Accessories fixture: " + name, error);
            }
        }

        private static void setField(Object owner, String name, Object value) {
            try {
                var field = owner.getClass().getDeclaredField(name);
                field.setAccessible(true);
                field.set(owner, value);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot restore native Accessories fixture: " + name, error);
            }
        }
    }
}
