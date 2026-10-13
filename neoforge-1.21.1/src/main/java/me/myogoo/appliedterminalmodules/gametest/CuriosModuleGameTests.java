package me.myogoo.appliedterminalmodules.gametest;

import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.AppliedTerminalModules;
import me.myogoo.appliedterminalmodules.compat.curios.CuriosModuleProvider;
import me.myogoo.appliedterminalmodules.init.ModItems;
import me.myogoo.appliedterminalmodules.menu.ModuleSemantics;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.function.Consumer;

@GameTestHolder(AppliedTerminalModules.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CuriosModuleGameTests {
    private CuriosModuleGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void curiosRetainsNativeValidationAndBindingCurse(GameTestHelper helper) {
        if (ModList.get().isLoaded("curios")) CuriosChecks.validation(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void curiosRebindsFullReplacementAndPreservesGearOnRemoval(GameTestHelper helper) {
        if (ModList.get().isLoaded("curios")) CuriosChecks.replacementAndRemoval(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void curiosRejectsSameCountIdentifierReplacement(GameTestHelper helper) {
        if (ModList.get().isLoaded("curios")) CuriosChecks.identifierReplacement(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void curiosShrinkDisablesStaleSlotsAndClosesMenu(GameTestHelper helper) {
        if (ModList.get().isLoaded("curios")) CuriosChecks.shrink(helper);
        helper.succeed();
    }

    /** Optional dependency types are resolved only after the outer tests check the loaded guard. */
    private static final class CuriosChecks {
        private static final String IDENTIFIER = "appliedterminalmodules_curios_test";

        private record Fixture(ServerPlayer player, MEStorageMenu menu,
                top.theillusivec4.curios.api.type.capability.ICuriosItemHandler inventory,
                top.theillusivec4.curios.common.inventory.CurioStacksHandler handler) {}

        private static void withSlots(GameTestHelper helper, Consumer<Fixture> check) {
            var originalManager = top.theillusivec4.curios.common.data.CuriosEntityManager.SERVER;
            var slotType = new top.theillusivec4.curios.common.slottype.SlotType.Builder(IDENTIFIER)
                    .size(2).build();
            // Bare Curios has no player slots and otherwise supplies no real inventory capability.
            top.theillusivec4.curios.common.data.CuriosEntityManager.SERVER =
                    new top.theillusivec4.curios.common.data.CuriosEntityManager() {
                        @Override
                        public Map<String, top.theillusivec4.curios.api.type.ISlotType> getEntitySlots(EntityType<?> type) {
                            return type == EntityType.PLAYER ? Map.of(IDENTIFIER, slotType)
                                    : originalManager.getEntitySlots(type);
                        }
                    };
            try {
                var player = TestTerminals.player(helper);
                var inventory = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).orElseThrow();
                var handler = new top.theillusivec4.curios.common.inventory.CurioStacksHandler(inventory,
                        IDENTIFIER, 2, true, false, true,
                        top.theillusivec4.curios.api.type.capability.ICurio.DropRule.DEFAULT);
                inventory.setCurios(Map.of(IDENTIFIER, handler));
                check.accept(new Fixture(player, TestTerminals.menu(helper, player), inventory, handler));
            } finally {
                top.theillusivec4.curios.common.data.CuriosEntityManager.SERVER = originalManager;
            }
        }

        private static void validation(GameTestHelper helper) {
            withSlots(helper, fixture -> {
                var player = fixture.player();
                var menu = fixture.menu();
                var slots = menu.getSlots(ModuleSemantics.CURIOS);
                helper.assertTrue(slots.size() == 2 && slots.stream().noneMatch(Slot::isActive),
                        "Two permanent Curios slots must start disabled without the addon card");
                TestTerminals.install(menu, ModItems.CURIOS_MODULE.get());
                helper.assertTrue(slots.stream().allMatch(Slot::isActive)
                        && new CuriosModuleProvider().isLayoutValid(menu),
                        "The addon card activates the real Curios handler's two slots");
                Slot slot = slots.getFirst();
                helper.assertTrue(!slot.mayPlace(new ItemStack(Items.STICK)),
                        "Native Curios validation rejects an ordinary item in the synthetic slot type");
                var cursed = new ItemStack(Items.IRON_HELMET);
                cursed.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.BINDING_CURSE), 1);
                fixture.handler().getStacks().setStackInSlot(0, cursed);
                menu.clicked(slot.index, 0, ClickType.PICKUP, player);
                menu.quickMoveStack(player, slot.index);
                helper.assertTrue(!slot.mayPickup(player) && slot.remove(1).isEmpty()
                        && ItemStack.matches(fixture.handler().getStacks().getStackInSlot(0), cursed)
                        && menu.getCarried().isEmpty(),
                        "Binding curse prevents pickup, shift-click and direct removal without deleting gear");
            });
        }

        private static void replacementAndRemoval(GameTestHelper helper) {
            withSlots(helper, fixture -> {
                var menu = fixture.menu();
                var slots = menu.getSlots(ModuleSemantics.CURIOS);
                int originalCount = menu.slots.size();
                int firstIndex = slots.getFirst().index;
                int secondIndex = slots.getLast().index;
                fixture.handler().getStacks().setStackInSlot(0, new ItemStack(Items.DIAMOND));
                fixture.handler().getStacks().setStackInSlot(1, new ItemStack(Items.IRON_INGOT));
                TestTerminals.install(menu, ModItems.CURIOS_MODULE.get());
                helper.assertTrue(slots.getFirst().getItem().is(Items.DIAMOND)
                        && slots.getLast().getItem().is(Items.IRON_INGOT),
                        "Both module slots bind to the original live handler before full replacement");
                var replacement = new top.theillusivec4.curios.common.inventory.CurioStacksHandler(fixture.inventory(),
                        IDENTIFIER, 2, true, false, true,
                        top.theillusivec4.curios.api.type.capability.ICurio.DropRule.DEFAULT);
                replacement.getStacks().setStackInSlot(0, new ItemStack(Items.EMERALD));
                replacement.getStacks().setStackInSlot(1, new ItemStack(Items.LAPIS_LAZULI));
                fixture.inventory().setCurios(Map.of(IDENTIFIER, replacement));
                helper.assertTrue(new CuriosModuleProvider().isLayoutValid(menu)
                        && slots.getFirst().getItem().is(Items.EMERALD)
                        && slots.getLast().getItem().is(Items.LAPIS_LAZULI),
                        "Same-layout full replacement rebinds both permanent slots");
                slots.getFirst().set(new ItemStack(Items.GOLD_INGOT));
                slots.getLast().set(new ItemStack(Items.REDSTONE));
                helper.assertTrue(replacement.getStacks().getStackInSlot(0).is(Items.GOLD_INGOT)
                        && replacement.getStacks().getStackInSlot(1).is(Items.REDSTONE)
                        && fixture.handler().getStacks().getStackInSlot(0).is(Items.DIAMOND)
                        && fixture.handler().getStacks().getStackInSlot(1).is(Items.IRON_INGOT),
                        "Writes after replacement update only the new handler");
                TestTerminals.cardSlot(menu).set(ItemStack.EMPTY);
                for (Slot slot : slots) {
                    helper.assertTrue(!slot.isActive() && !slot.mayPickup(fixture.player())
                            && !slot.mayPlace(new ItemStack(Items.STICK)) && slot.remove(1).isEmpty(),
                            "Removing the addon card disables every stale Curios slot ID");
                }
                helper.assertTrue(replacement.getStacks().getStackInSlot(0).is(Items.GOLD_INGOT)
                        && replacement.getStacks().getStackInSlot(1).is(Items.REDSTONE)
                        && menu.slots.size() == originalCount && slots.getFirst().index == firstIndex
                        && slots.getLast().index == secondIndex,
                        "Card removal preserves both equipped items and all packet slot indices");
                TestTerminals.install(menu, ModItems.CURIOS_MODULE.get());
                helper.assertTrue(slots.stream().allMatch(Slot::isActive)
                        && new CuriosModuleProvider().isLayoutValid(menu),
                        "Reinstalling the card reactivates the same replacement handler without rebuilding slots");
            });
        }

        private static void identifierReplacement(GameTestHelper helper) {
            withSlots(helper, fixture -> {
                var menu = fixture.menu();
                var slots = menu.getSlots(ModuleSemantics.CURIOS);
                TestTerminals.install(menu, ModItems.CURIOS_MODULE.get());
                helper.assertTrue(new CuriosModuleProvider().isLayoutValid(menu),
                        "The original two-slot layout is valid");
                String otherIdentifier = IDENTIFIER + "_replacement";
                var replacement = new top.theillusivec4.curios.common.inventory.CurioStacksHandler(fixture.inventory(),
                        otherIdentifier, 2, true, false, true,
                        top.theillusivec4.curios.api.type.capability.ICurio.DropRule.DEFAULT);
                replacement.getStacks().setStackInSlot(0, new ItemStack(Items.EMERALD));
                fixture.inventory().setCurios(Map.of(otherIdentifier, replacement));
                helper.assertTrue(replacement.getStacks().getSlots() == slots.size()
                        && !new CuriosModuleProvider().isLayoutValid(menu)
                        && slots.stream().noneMatch(Slot::isActive),
                        "Replacing slot identifiers invalidates the layout even when the total count is unchanged");
                helper.assertTrue(slots.getFirst().getItem().isEmpty() && slots.getFirst().remove(1).isEmpty()
                        && replacement.getStacks().getStackInSlot(0).is(Items.EMERALD),
                        "Stale slot identifiers cannot read or mutate replacement equipment");
                TestTerminals.broadcast(helper, menu);
                helper.assertTrue(fixture.player().containerMenu == fixture.player().inventoryMenu,
                        "Broadcasting an invalid layout closes the terminal instead of changing slot indices");
            });
        }

        private static void shrink(GameTestHelper helper) {
            withSlots(helper, fixture -> {
                var menu = fixture.menu();
                var slots = menu.getSlots(ModuleSemantics.CURIOS);
                int originalCount = menu.slots.size();
                TestTerminals.install(menu, ModItems.CURIOS_MODULE.get());
                fixture.handler().getStacks().setStackInSlot(0, new ItemStack(Items.DIAMOND));
                helper.assertTrue(new CuriosModuleProvider().isLayoutValid(menu),
                        "The initial two-slot layout is valid before shrinking");
                fixture.handler().getStacks().shrink(1);
                helper.assertTrue(fixture.handler().getStacks().getSlots() == 1
                        && !new CuriosModuleProvider().isLayoutValid(menu) && !slots.getLast().isActive()
                        && slots.getLast().getItem().isEmpty() && slots.getLast().remove(1).isEmpty()
                        && fixture.handler().getStacks().getStackInSlot(0).is(Items.DIAMOND)
                        && menu.slots.size() == originalCount,
                        "Shrinking disables the removed slot, preserves remaining gear and keeps packet indices fixed");
                TestTerminals.broadcast(helper, menu);
                helper.assertTrue(fixture.player().containerMenu == fixture.player().inventoryMenu,
                        "A shrunken Curios layout requests reopening by closing the terminal");
            });
        }
    }
}
