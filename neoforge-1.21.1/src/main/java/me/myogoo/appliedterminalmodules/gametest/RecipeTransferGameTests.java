package me.myogoo.appliedterminalmodules.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.implementations.blockentities.IViewCellStorage;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ILinkStatus;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEItems;
import appeng.util.inv.AppEngInternalInventory;
import me.myogoo.appliedterminalmodules.AppliedTerminalModules;
import me.myogoo.appliedterminalmodules.init.ModItems;
import me.myogoo.appliedterminalmodules.menu.CraftingRecipeTransfer;
import me.myogoo.myotus.api.MyotusAPI;
import me.myogoo.myotus.menu.MyoSlotSemantics;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AppliedTerminalModules.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeTransferGameTests {
    private RecipeTransferGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void shapedRecipeUsesThreeColumnAlignment(GameTestHelper helper) {
        var fixture = crafting(helper);
        fixture.player().getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
        transfer(fixture, "crafting_table");
        var grid = slots(fixture);
        for (int i = 0; i < 9; i++) {
            if (i == 0 || i == 1 || i == 3 || i == 4) assertStack(helper, grid.get(i).getItem(), Items.OAK_PLANKS, 1);
            else helper.assertTrue(grid.get(i).getItem().isEmpty(), "A 2x2 recipe must leave its third column empty");
        }
        assertStack(helper, grid.getLast().getItem(), Items.CRAFTING_TABLE, 1);
        helper.assertTrue(total(fixture, Items.OAK_PLANKS) == 4, "Alignment must conserve all four ingredients");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void shapelessRecipeReturnsOldInputsAndKeepsPreviewVirtual(GameTestHelper helper) {
        var fixture = crafting(helper);
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id("oak_planks")).orElseThrow().value()
                instanceof ShapelessRecipe, "This test requires the vanilla shapeless plank recipe");
        fixture.player().getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 2));
        slots(fixture).get(8).setByPlayer(new ItemStack(Items.DIAMOND, 3));
        transfer(fixture, "oak_planks");
        assertStack(helper, slots(fixture).getFirst().getItem(), Items.OAK_LOG, 1);
        helper.assertTrue(slots(fixture).subList(1, 9).stream().allMatch(slot -> slot.getItem().isEmpty()),
                "Shapeless transfer uses the first position and clears old mismatches");
        assertStack(helper, slots(fixture).getLast().getItem(), Items.OAK_PLANKS, 4);
        helper.assertTrue(total(fixture, Items.OAK_LOG) == 2 && total(fixture, Items.DIAMOND) == 3
                        && total(fixture, Items.OAK_PLANKS) == 0,
                "Returning mismatches must conserve inputs without putting the preview in real inventories");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void partialRepeatedIngredientsAndRepeatedRequestsConserveCounts(GameTestHelper helper) {
        var fixture = crafting(helper);
        slots(fixture).getFirst().setByPlayer(new ItemStack(Items.OAK_PLANKS, 5));
        fixture.player().getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
        transfer(fixture, "chest");
        helper.assertTrue(slots(fixture).subList(0, 9).stream().filter(Slot::hasItem).count() == 3
                        && !slots(fixture).getLast().getItem().is(Items.CHEST) && total(fixture, Items.OAK_PLANKS) == 7,
                "Two available planks fill two repeated ingredients without consuming the existing pile");
        assertStack(helper, slots(fixture).getLast().getItem(), Items.OAK_SLAB, 6);
        assertStack(helper, slots(fixture).getFirst().getItem(), Items.OAK_PLANKS, 5);
        var partial = snapshot(fixture);
        for (int i = 0; i < 3; i++) transfer(fixture, "chest");
        assertUnchanged(helper, fixture, partial, "Repeated partial requests");
        fixture.player().getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 5));
        transfer(fixture, "chest");
        assertStack(helper, slots(fixture).getLast().getItem(), Items.CHEST, 1);
        helper.assertTrue(total(fixture, Items.OAK_PLANKS) == 12, "Completing the grid must conserve all twelve planks");
        var complete = snapshot(fixture);
        transfer(fixture, "chest");
        assertUnchanged(helper, fixture, complete, "Repeated complete request");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rejectedRequestsNeverMutateAnyInventory(GameTestHelper helper) {
        var noncrafting = helper.getLevel().getRecipeManager().byKey(id("iron_ingot_from_smelting_iron_ore"))
                .orElseThrow();
        helper.assertTrue(!(noncrafting.value() instanceof CraftingRecipe), "Expected a real noncrafting recipe");
        for (String rejection : List.of("no card", "removed same tick", "stale id", "closed menu",
                "invalid menu", "missing recipe", "noncrafting recipe", "special recipe", "dead player", "spectator")) {
            var fixture = crafting(helper);
            fixture.player().getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 3));
            slots(fixture).getFirst().setByPlayer(new ItemStack(Items.DIAMOND, 2));
            int containerId = fixture.menu().containerId;
            var recipeId = id("oak_planks");
            switch (rejection) {
                case "no card" -> {
                    fixture = TestTerminals.create(helper);
                    fixture.player().getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 3));
                    slots(fixture).getFirst().set(new ItemStack(Items.DIAMOND, 2));
                }
                case "removed same tick" -> TestTerminals.remove(fixture);
                case "stale id" -> containerId++;
                case "closed menu" -> fixture.player().containerMenu = fixture.player().inventoryMenu;
                case "invalid menu" -> fixture.menu().setValidMenu(false);
                case "missing recipe" -> recipeId = id("atm_missing_recipe");
                case "noncrafting recipe" -> recipeId = noncrafting.id();
                case "special recipe" -> recipeId = helper.getLevel().getRecipeManager().byKey(id("repair_item")).orElseThrow().id();
                case "dead player" -> fixture.player().setHealth(0);
                case "spectator" -> fixture.player().setGameMode(GameType.SPECTATOR);
                default -> throw new AssertionError(rejection);
            }
            var before = snapshot(fixture);
            CraftingRecipeTransfer.transfer(fixture.player(), containerId, recipeId);
            assertUnchanged(helper, fixture, before, rejection);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fullInventoriesKeepBlockedGridAndPartialStorageRemainder(GameTestHelper helper) {
        // Exercise full and partially accepting ME storage in both survival and creative.
        for (int scenario = 0; scenario < 4; scenario++) {
            int capacity = scenario % 2 == 0 ? 64 : 2;
            var fixture = network(helper, capacity, 64, true);
            if (scenario >= 2) {
                fixture.player().setGameMode(GameType.CREATIVE);
                helper.assertTrue(fixture.player().getAbilities().instabuild, "Creative overflow regression requires instabuild");
            }
            var host = host(fixture);
            if (capacity == 64) host.contents.put(AEItemKey.of(Items.COBBLESTONE), 64L);
            for (int i = 0; i < fixture.player().getInventory().getContainerSize(); i++) {
                fixture.player().getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            }
            slots(fixture).getFirst().setByPlayer(new ItemStack(Items.DIAMOND, 3));
            var before = snapshot(fixture);
            transfer(fixture, "oak_planks");
            assertStack(helper, slots(fixture).getFirst().getItem(), Items.DIAMOND, capacity == 64 ? 3 : 1);
            helper.assertTrue(total(fixture, Items.DIAMOND) + host.count(Items.DIAMOND) == 3,
                    "A full player inventory must leave every unstorable diamond in the grid");
            if (capacity == 64) assertUnchanged(helper, fixture, before, "Full player and ME inventories");
            else helper.assertTrue(host.count(Items.DIAMOND) == 2, "Bounded storage accepts only two diamonds");
            var blocked = snapshot(fixture);
            var stored = Map.copyOf(host.contents);
            transfer(fixture, "oak_planks");
            assertUnchanged(helper, fixture, blocked, "Retry with blocked remainder");
            helper.assertTrue(host.contents.equals(stored) && host.wrongSourceCalls == 0,
                    "Retry must preserve full storage and keep the player's action source");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void poweredStorageUsesPlayerSourceAndConservesBothDirections(GameTestHelper helper) {
        var fixture = network(helper, 16, 64, true);
        var host = host(fixture);
        host.contents.put(AEItemKey.of(Items.OAK_LOG), 2L);
        slots(fixture).getFirst().setByPlayer(new ItemStack(Items.DIAMOND, 3));
        double beforePower = host.power;
        transfer(fixture, "oak_planks");
        assertStack(helper, slots(fixture).getFirst().getItem(), Items.OAK_LOG, 1);
        assertStack(helper, slots(fixture).getLast().getItem(), Items.OAK_PLANKS, 4);
        helper.assertTrue(host.count(Items.OAK_LOG) == 1 && host.count(Items.DIAMOND) == 3
                        && total(fixture, Items.OAK_LOG) + host.count(Items.OAK_LOG) == 2
                        && total(fixture, Items.DIAMOND) + host.count(Items.DIAMOND) == 3,
                "Actual powered insertion and extraction must conserve the player, grid and ME inventories");
        helper.assertTrue(host.wrongSourceCalls == 0 && host.modulatedInsert == 3 && host.modulatedExtract == 1
                        && Math.abs(beforePower - host.power - PowerMultiplier.CONFIG.multiply(4)) < 0.000001,
                "Both storage directions require the menu's player source and charge actual AE energy");
        var before = snapshot(fixture);
        var stored = Map.copyOf(host.contents);
        beforePower = host.power;
        transfer(fixture, "oak_planks");
        assertUnchanged(helper, fixture, before, "Repeat ME request");
        helper.assertTrue(stored.equals(host.contents) && host.power == beforePower,
                "A repeated request must not extract another item or charge more energy");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void noEnergyDisconnectedAndDeniedStorageFallBackToPlayer(GameTestHelper helper) {
        for (String reason : List.of("no energy", "disconnected", "permission denied")) {
            var fixture = network(helper, 16, reason.equals("no energy") ? 0 : 64, !reason.equals("disconnected"));
            var host = host(fixture);
            host.allowed = !reason.equals("permission denied");
            host.contents.put(AEItemKey.of(Items.OAK_LOG), 2L);
            fixture.player().getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 1));
            double power = host.power;
            transfer(fixture, "oak_planks");
            assertStack(helper, slots(fixture).getFirst().getItem(), Items.OAK_LOG, 1);
            helper.assertTrue(fixture.player().getInventory().countItem(Items.OAK_LOG) == 0
                            && host.count(Items.OAK_LOG) == 2 && host.power == power && host.wrongSourceCalls == 0,
                    reason + " must fall back to player ingredients without bypassing storage or power checks");
            helper.assertTrue(reason.equals("disconnected") ? host.storageCalls == 0 : host.storageCalls > 0,
                    "Disconnected storage must not be queried; connected storage must be checked");
            if (reason.equals("permission denied")) helper.assertTrue(host.deniedCalls > 0, "Storage enforces its source permission");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void viewCellFiltersOnlyNetworkIngredients(GameTestHelper helper) {
        for (boolean listed : List.of(false, true)) {
            var fixture = network(helper, 16, 64, true);
            var host = host(fixture);
            host.contents.put(AEItemKey.of(Items.OAK_LOG), 2L);
            var cell = AEItems.VIEW_CELL.stack();
            AEItems.VIEW_CELL.get().getConfigInventory(cell).setStack(0,
                    new GenericStack(AEItemKey.of(listed ? Items.OAK_LOG : Items.DIAMOND), 1));
            host.viewCells.setItemDirect(0, cell);
            if (!listed) fixture.player().getInventory().setItem(0, new ItemStack(Items.OAK_LOG));
            transfer(fixture, "oak_planks");
            assertStack(helper, slots(fixture).getFirst().getItem(), Items.OAK_LOG, 1);
            helper.assertTrue(host.count(Items.OAK_LOG) == (listed ? 1 : 2)
                            && host.modulatedExtract == (listed ? 1 : 0) && host.wrongSourceCalls == 0,
                    "A real configured view cell must exclude hidden ME ingredients while allowing player fallback");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lockedSourceSlotIsNeverUsed(GameTestHelper helper) {
        var fixture = crafting(helper);
        fixture.menu().lockPlayerInventorySlot(0);
        fixture.player().getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 2));
        var before = snapshot(fixture);
        transfer(fixture, "oak_planks");
        assertUnchanged(helper, fixture, before, "Only matching source is locked");
        fixture.player().getInventory().setItem(1, new ItemStack(Items.OAK_LOG));
        transfer(fixture, "oak_planks");
        assertStack(helper, fixture.player().getInventory().getItem(0), Items.OAK_LOG, 2);
        assertStack(helper, slots(fixture).getFirst().getItem(), Items.OAK_LOG, 1);
        helper.assertTrue(fixture.player().getInventory().getItem(1).isEmpty() && total(fixture, Items.OAK_LOG) == 3,
                "Fallback must skip the locked earlier slot and consume only the unlocked ingredient");
        var blocked = crafting(helper);
        blocked.menu().lockPlayerInventorySlot(0);
        for (int i = 0; i < 36; i++) blocked.player().getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        blocked.player().getInventory().setItem(0, new ItemStack(Items.DIAMOND, 63));
        slots(blocked).getFirst().setByPlayer(new ItemStack(Items.DIAMOND, 2));
        var lockedDestination = snapshot(blocked);
        transfer(blocked, "oak_planks");
        assertUnchanged(helper, blocked, lockedDestination, "Returning inputs must not merge into a locked slot");
        helper.succeed();
    }

    private static TestTerminals.Fixture crafting(GameTestHelper helper) {
        var fixture = TestTerminals.create(helper);
        TestTerminals.install(fixture, ModItems.CRAFTING_MODULE.get());
        return fixture;
    }

    private static TestTerminals.Fixture network(GameTestHelper helper, int capacity, double power, boolean connected) {
        var player = TestTerminals.player(helper);
        var host = new StorageHost(player, capacity, power, connected);
        var menu = TestTerminals.menu(helper, player, host);
        host.source = menu.getActionSource();
        TestTerminals.install(menu, ModItems.CRAFTING_MODULE.get());
        return new TestTerminals.Fixture(player, menu, List.copyOf(menu.getSlots(MyoSlotSemantics.MYO_UPGRADE_SLOT)));
    }

    private static StorageHost host(TestTerminals.Fixture fixture) { return (StorageHost) fixture.menu().getHost(); }

    private static ResourceLocation id(String path) { return ResourceLocation.withDefaultNamespace(path); }

    private static void transfer(TestTerminals.Fixture fixture, String recipe) {
        CraftingRecipeTransfer.transfer(fixture.player(), fixture.menu().containerId, id(recipe));
    }

    private static List<Slot> slots(TestTerminals.Fixture fixture) {
        return MyotusAPI.terminalModules().slots(fixture.menu(), ModItems.CRAFTING_MODULE.get());
    }

    // The result slot is a preview, not an owned item, so it must not contribute to conservation counts.
    private static int total(TestTerminals.Fixture fixture, Item item) {
        return fixture.player().getInventory().countItem(item) + slots(fixture).subList(0, 9).stream()
                .map(Slot::getItem).filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static List<ItemStack> snapshot(TestTerminals.Fixture fixture) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < fixture.player().getInventory().getContainerSize(); i++) {
            result.add(fixture.player().getInventory().getItem(i).copy());
        }
        fixture.menu().slots.forEach(slot -> result.add(slot.getItem().copy()));
        result.add(fixture.menu().getCarried().copy());
        return result;
    }

    private static void assertUnchanged(GameTestHelper helper, TestTerminals.Fixture fixture,
            List<ItemStack> before, String reason) {
        var after = snapshot(fixture);
        helper.assertTrue(before.size() == after.size(), reason + " changed the menu layout");
        for (int i = 0; i < before.size(); i++) {
            helper.assertTrue(ItemStack.matches(before.get(i), after.get(i)), reason + " mutated inventory/slot " + i);
        }
    }

    private static void assertStack(GameTestHelper helper, ItemStack stack, Item item, int count) {
        helper.assertTrue(stack.is(item) && stack.getCount() == count, "Expected " + count + " " + item + ", got " + stack);
    }

    /** Bounded storage that rejects any source except this menu's player and charges simulated/modulated AE power. */
    private static final class StorageHost extends TestTerminals.TestHost implements MEStorage, IEnergySource, IViewCellStorage {
        private final Map<AEKey, Long> contents = new LinkedHashMap<>();
        private final AppEngInternalInventory viewCells = new AppEngInternalInventory(5);
        private final int capacity;
        private final boolean connected;
        private double power;
        private IActionSource source;
        private boolean allowed = true;
        private int storageCalls;
        private int wrongSourceCalls;
        private int deniedCalls;
        private long modulatedInsert;
        private long modulatedExtract;

        private StorageHost(ServerPlayer player, int capacity, double power, boolean connected) {
            super(player);
            this.capacity = capacity;
            this.power = power;
            this.connected = connected;
        }

        @Override
        public MEStorage getInventory() { return this; }

        @Override
        public ILinkStatus getLinkStatus() { return connected ? ILinkStatus.ofConnected() : ILinkStatus.ofDisconnected(); }

        @Override
        public InternalInventory getViewCellStorage() { return viewCells; }

        @Override
        public Component getDescription() { return Component.literal("ATM GameTest bounded storage"); }

        private long count(Item item) { return contents.getOrDefault(AEItemKey.of(item), 0L); }

        private boolean permits(IActionSource actionSource) {
            storageCalls++;
            if (actionSource != source || actionSource.player().orElse(null) != getPlayer()) {
                wrongSourceCalls++;
                return false;
            }
            if (!allowed) { deniedCalls++; return false; }
            return true;
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource actionSource) {
            MEStorage.checkPreconditions(key, amount, mode, actionSource);
            if (!permits(actionSource)) return 0;
            long inserted = Math.min(amount, capacity - contents.values().stream().mapToLong(Long::longValue).sum());
            if (mode == Actionable.MODULATE && inserted > 0) {
                contents.merge(key, inserted, Long::sum);
                modulatedInsert += inserted;
            }
            return inserted;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource actionSource) {
            MEStorage.checkPreconditions(key, amount, mode, actionSource);
            if (!permits(actionSource)) return 0;
            long extracted = Math.min(amount, contents.getOrDefault(key, 0L));
            if (mode == Actionable.MODULATE && extracted > 0) {
                contents.compute(key, (ignored, count) -> count == extracted ? null : count - extracted);
                modulatedExtract += extracted;
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) { contents.forEach(out::add); }

        @Override
        public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
            double extracted = Math.min(power, multiplier.multiply(amount));
            if (mode == Actionable.MODULATE) power -= extracted;
            return multiplier.divide(extracted);
        }
    }
}
