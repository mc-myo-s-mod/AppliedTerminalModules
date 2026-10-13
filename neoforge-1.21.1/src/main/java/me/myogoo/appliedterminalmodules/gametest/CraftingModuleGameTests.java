package me.myogoo.appliedterminalmodules.gametest;

import java.util.List;

import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.AppliedTerminalModules;
import me.myogoo.appliedterminalmodules.init.ModItems;
import me.myogoo.appliedterminalmodules.menu.CraftingModuleProvider;
import me.myogoo.myotus.api.MyotusAPI;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;

@GameTestHolder(AppliedTerminalModules.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraftingModuleGameTests {
    private CraftingModuleGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pickupConsumesInputsAndRecomputesPreview(GameTestHelper helper) {
        var player = TestTerminals.player(helper);
        var menu = TestTerminals.menu(helper, player);
        TestTerminals.install(menu, ModItems.CRAFTING_MODULE.get());
        var slots = slots(menu);
        slots.get(8).setByPlayer(new ItemStack(Items.OAK_LOG));
        assertPlanks(helper, slots.getLast().getItem(), 4);
        menu.clicked(slots.getLast().index, 1, ClickType.PICKUP, player);
        assertPlanks(helper, menu.getCarried(), 4);
        helper.assertTrue(slots.get(8).getItem().isEmpty() && slots.getLast().getItem().isEmpty(),
                "Right-click must consume a complete recipe and clear the cropped-grid preview");
        menu.clicked(slots.getLast().index, 0, ClickType.PICKUP, player);
        assertPlanks(helper, menu.getCarried(), 4);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void partialCursorAndShiftCapacityNeverConsumeARecipe(GameTestHelper helper) {
        var player = TestTerminals.player(helper);
        var menu = TestTerminals.menu(helper, player);
        TestTerminals.install(menu, ModItems.CRAFTING_MODULE.get());
        var slots = slots(menu);
        slots.getFirst().setByPlayer(new ItemStack(Items.OAK_LOG, 2));
        menu.setCarried(new ItemStack(Items.OAK_PLANKS, 63));
        menu.clicked(slots.getLast().index, 0, ClickType.PICKUP, player);
        assertPlanks(helper, menu.getCarried(), 63);
        helper.assertTrue(slots.getFirst().getItem().getCount() == 2, "A partial cursor must not consume inputs");
        menu.setCarried(ItemStack.EMPTY);
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 62));
        menu.quickMoveStack(player, slots.getLast().index);
        assertPlanks(helper, player.getInventory().getItem(0), 62);
        helper.assertTrue(slots.getFirst().getItem().getCount() == 2, "Partial shift-click room must preserve inputs");
        player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 60));
        menu.quickMoveStack(player, slots.getLast().index);
        assertPlanks(helper, player.getInventory().getItem(0), 64);
        helper.assertTrue(slots.getFirst().getItem().getCount() == 1, "Shift-click consumes exactly one complete recipe");
        assertPlanks(helper, slots.getLast().getItem(), 4);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void extractionMethodsRejectPartialBatches(GameTestHelper helper) {
        var player = TestTerminals.player(helper);
        var menu = TestTerminals.menu(helper, player);
        TestTerminals.install(menu, ModItems.CRAFTING_MODULE.get());
        var slots = slots(menu);
        slots.getFirst().setByPlayer(new ItemStack(Items.OAK_LOG));
        Slot output = slots.getLast();
        helper.assertTrue(output.safeTake(2, Integer.MAX_VALUE, player).isEmpty()
                        && output.tryRemove(4, 2, player).isEmpty() && output.remove(1).isEmpty(),
                "Capacity-limited, partial and direct partial extraction must all reject without consuming inputs");
        helper.assertTrue(slots.getFirst().getItem().getCount() == 1, "Rejected extraction preserves the grid");
        assertPlanks(helper, output.safeTake(4, 4, player), 4);
        helper.assertTrue(slots.getFirst().getItem().isEmpty() && output.getItem().isEmpty(),
                "safeTake calls the consumption callback exactly once");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cakeReturnsThreeBucketsWithoutDuplicatingOutput(GameTestHelper helper) {
        var player = TestTerminals.player(helper);
        var menu = TestTerminals.menu(helper, player);
        TestTerminals.install(menu, ModItems.CRAFTING_MODULE.get());
        var slots = slots(menu);
        for (int i = 0; i < 3; i++) slots.get(i).setByPlayer(new ItemStack(Items.MILK_BUCKET));
        slots.get(3).setByPlayer(new ItemStack(Items.SUGAR));
        slots.get(4).setByPlayer(new ItemStack(Items.EGG));
        slots.get(5).setByPlayer(new ItemStack(Items.SUGAR));
        for (int i = 6; i < 9; i++) slots.get(i).setByPlayer(new ItemStack(Items.WHEAT));
        menu.clicked(slots.getLast().index, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.CAKE) && menu.getCarried().getCount() == 1,
                "Cake output must be transferred exactly once");
        for (int i = 0; i < 3; i++) helper.assertTrue(slots.get(i).getItem().is(Items.BUCKET), "Milk returns its bucket");
        helper.assertTrue(slots.subList(3, 10).stream().allMatch(slot -> slot.getItem().isEmpty()),
                "All other cake ingredients and the preview must be consumed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void numberKeyTransferConsumesACompleteRecipe(GameTestHelper helper) {
        var player = TestTerminals.player(helper);
        var menu = TestTerminals.menu(helper, player);
        TestTerminals.install(menu, ModItems.CRAFTING_MODULE.get());
        var slots = slots(menu);
        slots.getFirst().setByPlayer(new ItemStack(Items.OAK_LOG));
        menu.clicked(slots.getLast().index, 0, ClickType.SWAP, player);
        assertPlanks(helper, player.getInventory().getItem(0), 4);
        helper.assertTrue(slots.getFirst().getItem().isEmpty() && slots.getLast().getItem().isEmpty(),
                "Number-key extraction bypasses remove but must still consume exactly one recipe");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cardRemovalReturnsInputsAndRejectsStaleClicks(GameTestHelper helper) {
        var player = TestTerminals.player(helper);
        var menu = TestTerminals.menu(helper, player);
        TestTerminals.install(menu, ModItems.CRAFTING_MODULE.get());
        TestTerminals.broadcast(helper, menu);
        var slots = slots(menu);
        slots.getFirst().setByPlayer(new ItemStack(Items.OAK_LOG, 3));
        TestTerminals.cardSlot(menu).set(ItemStack.EMPTY);
        menu.clicked(slots.getLast().index, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty(), "Removal must block stale result clicks immediately");
        TestTerminals.broadcast(helper, menu);
        helper.assertTrue(player.getInventory().countItem(Items.OAK_LOG) == 3
                        && slots.stream().allMatch(slot -> slot.getItem().isEmpty()),
                "The removal lifecycle must return each input exactly once and clear the preview");
        CraftingModuleProvider.returnIngredients(menu);
        helper.assertTrue(player.getInventory().countItem(Items.OAK_LOG) == 3, "Repeated cleanup must be harmless");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void closingReturnsInputsAndMenusOwnIndependentGrids(GameTestHelper helper) {
        var player = TestTerminals.player(helper);
        var menu = TestTerminals.menu(helper, player);
        TestTerminals.install(menu, ModItems.CRAFTING_MODULE.get());
        var slots = slots(menu);
        slots.getFirst().setByPlayer(new ItemStack(Items.OAK_LOG, 3));
        var other = TestTerminals.player(helper);
        var otherMenu = TestTerminals.menu(helper, other);
        TestTerminals.install(otherMenu, ModItems.CRAFTING_MODULE.get());
        helper.assertTrue(slots(otherMenu).stream().allMatch(slot -> slot.getItem().isEmpty()),
                "A second viewer must have an independent crafting grid");
        menu.removed(player);
        menu.removed(player);
        helper.assertTrue(player.getInventory().countItem(Items.OAK_LOG) == 3
                        && slots.stream().allMatch(slot -> slot.getItem().isEmpty())
                        && other.getInventory().countItem(Items.OAK_LOG) == 0,
                "Closing returns ingredients once to their owner and never returns a preview as an item");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void removalAndImmediateCloseReturnsInputsWithoutAFirstTick(GameTestHelper helper) {
        var fixture = TestTerminals.create(helper);
        TestTerminals.install(fixture, ModItems.CRAFTING_MODULE.get());
        var slots = slots(fixture.menu());
        slots.getFirst().setByPlayer(new ItemStack(Items.OAK_LOG, 3));
        TestTerminals.remove(fixture);
        fixture.menu().removed(fixture.player());
        NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Close(fixture.player(), fixture.menu()));
        NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Close(fixture.player(), fixture.menu()));
        helper.assertTrue(fixture.player().getInventory().countItem(Items.OAK_LOG) == 3
                        && slots.stream().allMatch(slot -> slot.getItem().isEmpty()),
                "Close-event fallback returns ingredients once even after removal before the first broadcast");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void deadPlayerCleanupDropsInputsInsteadOfRestoringDeadInventory(GameTestHelper helper) {
        var fixture = TestTerminals.create(helper);
        var position = helper.absolutePos(BlockPos.ZERO);
        fixture.player().setPos(position.getX() + 0.5, position.getY() + 1, position.getZ() + 0.5);
        TestTerminals.install(fixture, ModItems.CRAFTING_MODULE.get());
        var slots = slots(fixture.menu());
        slots.getFirst().setByPlayer(new ItemStack(Items.OAK_LOG, 3));
        var area = fixture.player().getBoundingBox().inflate(3);
        int before = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
                .filter(entity -> entity.getItem().is(Items.OAK_LOG)).mapToInt(entity -> entity.getItem().getCount()).sum();
        fixture.player().setHealth(0);
        CraftingModuleProvider.returnIngredients(fixture.menu());
        CraftingModuleProvider.returnIngredients(fixture.menu());
        int after = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
                .filter(entity -> entity.getItem().is(Items.OAK_LOG)).mapToInt(entity -> entity.getItem().getCount()).sum();
        helper.assertTrue(after - before == 3 && fixture.player().getInventory().countItem(Items.OAK_LOG) == 0
                        && slots.stream().allMatch(slot -> slot.getItem().isEmpty()),
                "Death cleanup drops inputs once instead of returning them to an inventory that respawn will discard");
        helper.succeed();
    }

    private static List<Slot> slots(MEStorageMenu menu) {
        return MyotusAPI.terminalModules().slots(menu, ModItems.CRAFTING_MODULE.get());
    }

    private static void assertPlanks(GameTestHelper helper, ItemStack stack, int count) {
        helper.assertTrue(stack.is(Items.OAK_PLANKS) && stack.getCount() == count,
                "Expected " + count + " oak planks, got " + stack);
    }
}
