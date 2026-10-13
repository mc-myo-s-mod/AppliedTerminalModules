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
import me.myogoo.appliedterminalmodules.menu.CraftingRecipeTransfer;
import me.myogoo.appliedterminalmodules.menu.WorkstationKind;
import me.myogoo.appliedterminalmodules.menu.WorkstationModuleProvider;
import me.myogoo.myotus.api.MyotusAPI;
import me.myogoo.myotus.menu.MyoSlotSemantics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AppliedTerminalModules.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkstationTransferGameTests {
    private static final WorkstationKind STONE = WorkstationKind.STONECUTTING;
    private static final WorkstationKind SMITH = WorkstationKind.SMITHING;
    private static final String SLAB = "stone_slab_from_stone_stonecutting";
    private static final String UPGRADE = "netherite_sword_smithing";

    private WorkstationTransferGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void stonecuttingSelectsRequestedRecipeAndTransfersExactlyOneSet(GameTestHelper helper) {
        var fixture = workstation(helper, STONE);
        fixture.player().getInventory().setItem(0, new ItemStack(Items.STONE, 4));
        transfer(helper, fixture, SLAB);
        var slots = slots(fixture, STONE);
        assertStack(helper, slots.getFirst().getItem(), Items.STONE, 1);
        assertStack(helper, slots.getLast().getItem(), Items.STONE_SLAB, 2);
        helper.assertTrue(id(SLAB).equals(WorkstationModuleProvider.selectedRecipe(fixture.menu()))
                        && fixture.player().getInventory().countItem(Items.STONE) == 3
                        && fixture.player().getInventory().countItem(Items.STONE_SLAB) == 0
                        && fixture.menu().getCarried().isEmpty(),
                "The requested ID selects a virtual output without taking it or filling a stack of inputs");
        var before = snapshot(fixture);
        for (int i = 0; i < 3; i++) transfer(helper, fixture, SLAB);
        assertUnchanged(helper, fixture, before, "Repeated one-set stonecutting requests");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void smithingUsesNativeThreeInputPredicatesAndPreservesBaseComponents(GameTestHelper helper) {
        var fixture = workstation(helper, SMITH);
        var holder = helper.getLevel().getRecipeManager().byKey(id(UPGRADE)).orElseThrow();
        helper.assertTrue(holder.value() instanceof SmithingRecipe
                        && CraftingRecipeTransfer.workstationInputs(holder).size() == 3,
                "Vanilla smithing must resolve three inputs without guessed ingredient getters");
        fixture.player().getInventory().setItem(0, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 4));
        var sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Transfer preserves this"));
        sword.setDamageValue(25);
        var originalSword = sword.copy();
        fixture.player().getInventory().setItem(1, sword);
        fixture.player().getInventory().setItem(2, new ItemStack(Items.NETHERITE_INGOT, 4));
        transfer(helper, fixture, UPGRADE);
        var slots = slots(fixture, SMITH);
        assertStack(helper, slots.get(0).getItem(), Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1);
        helper.assertTrue(ItemStack.matches(slots.get(1).getItem(), originalSword)
                        && slots.get(1).getItem().getDamageValue() == 25,
                "The base's components remain intact during input transfer");
        assertStack(helper, slots.get(2).getItem(), Items.NETHERITE_INGOT, 1);
        assertStack(helper, slots.getLast().getItem(), Items.NETHERITE_SWORD, 1);
        helper.assertTrue("Transfer preserves this".equals(slots.getLast().getItem().getHoverName().getString())
                        && fixture.player().getInventory().countItem(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) == 3
                        && fixture.player().getInventory().countItem(Items.NETHERITE_INGOT) == 3
                        && fixture.player().getInventory().countItem(Items.NETHERITE_SWORD) == 0,
                "Only one template/addition is transferred and the smithing result remains a preview");
        var before = snapshot(fixture);
        transfer(helper, fixture, UPGRADE);
        assertUnchanged(helper, fixture, before, "Repeated smithing request");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void smithingTrimUsesRealRecipeAndPartialTransferDoesNotConsumeAnOutput(GameTestHelper helper) {
        var fixture = workstation(helper, SMITH);
        fixture.player().getInventory().setItem(0, new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 2));
        String recipe = "sentry_armor_trim_smithing_template_smithing_trim";
        transfer(helper, fixture, recipe);
        var slots = slots(fixture, SMITH);
        assertStack(helper, slots.getFirst().getItem(), Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 1);
        helper.assertTrue(slots.get(1).getItem().isEmpty() && slots.get(2).getItem().isEmpty()
                        && slots.getLast().getItem().isEmpty(),
                "A partial request fills only available inputs and creates no output");
        fixture.player().getInventory().setItem(1, new ItemStack(Items.DIAMOND_CHESTPLATE));
        fixture.player().getInventory().setItem(2, new ItemStack(Items.DIAMOND, 3));
        transfer(helper, fixture, recipe);
        helper.assertTrue(slots.getLast().getItem().is(Items.DIAMOND_CHESTPLATE)
                        && slots.getLast().getItem().has(DataComponents.TRIM)
                        && !slots.get(1).getItem().has(DataComponents.TRIM)
                        && fixture.player().getInventory().countItem(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE) == 1
                        && fixture.player().getInventory().countItem(Items.DIAMOND) == 2,
                "The native trim preview is computed without applying it to or consuming the owned base");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void invalidRecipeCardContainerAndPlayerRequestsNeverMutateInputs(GameTestHelper helper) {
        for (String rejection : List.of("no card", "wrong card", "removed card", "stale id", "closed menu",
                "invalid menu", "missing recipe", "crafting recipe", "smelting recipe", "dead", "spectator", "disconnected")) {
            var fixture = workstation(helper, STONE);
            fixture.player().getInventory().setItem(0, new ItemStack(Items.STONE, 4));
            slots(fixture, STONE).getFirst().set(new ItemStack(Items.DIAMOND, 2));
            int containerId = fixture.menu().containerId;
            var recipeId = id(SLAB);
            switch (rejection) {
                case "no card", "removed card" -> TestTerminals.remove(fixture);
                case "wrong card" -> TestTerminals.install(fixture, WorkstationKind.ANVIL.card());
                case "stale id" -> containerId++;
                case "closed menu" -> fixture.player().containerMenu = fixture.player().inventoryMenu;
                case "invalid menu" -> fixture.menu().setValidMenu(false);
                case "missing recipe" -> recipeId = id("atm_missing_workstation_recipe");
                case "crafting recipe" -> recipeId = id("oak_planks");
                case "smelting recipe" -> recipeId = id("iron_ingot_from_smelting_iron_ore");
                case "dead" -> fixture.player().setHealth(0);
                case "spectator" -> fixture.player().setGameMode(GameType.SPECTATOR);
                case "disconnected" -> fixture.player().disconnect();
                default -> throw new AssertionError(rejection);
            }
            var before = snapshot(fixture);
            CraftingRecipeTransfer.transferWorkstation(fixture.player(), containerId, recipeId);
            assertUnchanged(helper, fixture, before, rejection);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void workstationTransferCannotUseWrongRecipeCardOrMutateOtherModuleInputs(GameTestHelper helper) {
        var fixture = workstation(helper, STONE);
        fixture.player().getInventory().setItem(0, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        fixture.player().getInventory().setItem(1, new ItemStack(Items.DIAMOND_SWORD));
        fixture.player().getInventory().setItem(2, new ItemStack(Items.NETHERITE_INGOT));
        var before = snapshot(fixture);
        transfer(helper, fixture, UPGRADE);
        assertUnchanged(helper, fixture, before, "Stonecutting card cannot authorize smithing");
        TestTerminals.install(fixture, SMITH.card());
        fixture.player().getInventory().setItem(3, new ItemStack(Items.STONE));
        before = snapshot(fixture);
        transfer(helper, fixture, SLAB);
        assertUnchanged(helper, fixture, before, "Smithing card cannot authorize stonecutting");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void poweredNetworkTransferConservesItemsAndChargesBothDirections(GameTestHelper helper) {
        var fixture = network(helper, STONE, 16, 64, true);
        var host = host(fixture);
        host.contents.put(AEItemKey.of(Items.STONE), 3L);
        slots(fixture, STONE).getFirst().setByPlayer(new ItemStack(Items.DIAMOND, 2));
        double power = host.power;
        transfer(helper, fixture, SLAB);
        assertStack(helper, slots(fixture, STONE).getFirst().getItem(), Items.STONE, 1);
        helper.assertTrue(host.count(Items.STONE) == 2 && host.count(Items.DIAMOND) == 2
                        && host.wrongSourceCalls == 0 && host.inserted == 2 && host.extracted == 1
                        && Math.abs(power - host.power - PowerMultiplier.CONFIG.multiply(3)) < 0.000001,
                "Powered insert/extract conserve all items and spend AE using the menu's player source");
        helper.assertTrue(fixture.player().getInventory().countItem(Items.STONE_SLAB) == 0,
                "Network recipe transfer never collects the result");
        var before = snapshot(fixture);
        var network = Map.copyOf(host.contents);
        power = host.power;
        transfer(helper, fixture, SLAB);
        assertUnchanged(helper, fixture, before, "Repeated filled network request");
        helper.assertTrue(network.equals(host.contents) && host.power == power,
                "A repeat does not extract another set or spend more power");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void networkFiltersPowerPermissionsAndLockedPlayerSlotsAreRespected(GameTestHelper helper) {
        for (String reason : List.of("view cell", "no power", "disconnected", "permission denied", "visible network")) {
            var fixture = network(helper, STONE, 16, reason.equals("no power") ? 0 : 64,
                    !reason.equals("disconnected"));
            var host = host(fixture);
            host.allowed = !reason.equals("permission denied");
            host.contents.put(AEItemKey.of(Items.STONE), 3L);
            var cell = AEItems.VIEW_CELL.stack();
            AEItems.VIEW_CELL.get().getConfigInventory(cell).setStack(0,
                    new GenericStack(AEItemKey.of(reason.equals("view cell") ? Items.DIAMOND : Items.STONE), 1));
            host.viewCells.setItemDirect(0, cell);
            fixture.menu().lockPlayerInventorySlot(0);
            fixture.player().getInventory().setItem(0, new ItemStack(Items.STONE, 2));
            fixture.player().getInventory().setItem(1, new ItemStack(Items.STONE));
            double power = host.power;
            transfer(helper, fixture, SLAB);
            assertStack(helper, slots(fixture, STONE).getFirst().getItem(), Items.STONE, 1);
            assertStack(helper, fixture.player().getInventory().getItem(0), Items.STONE, 2);
            boolean network = reason.equals("visible network");
            helper.assertTrue(host.count(Items.STONE) == (network ? 2 : 3)
                            && host.extracted == (network ? 1 : 0) && host.wrongSourceCalls == 0
                            && fixture.player().getInventory().getItem(1).isEmpty() == !network
                            && (network ? host.power < power : host.power == power),
                    reason + " must preserve hidden/denied ME contents and skip the locked player source");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void overflowStaysInInputAndNeverMergesIntoLockedInventory(GameTestHelper helper) {
        for (int scenario = 0; scenario < 4; scenario++) {
            int capacity = scenario % 2 == 0 ? 0 : 2;
            var fixture = network(helper, STONE, capacity, 64, true);
            if (scenario >= 2) fixture.player().setGameMode(GameType.CREATIVE);
            var inventory = fixture.player().getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            inventory.setItem(0, new ItemStack(Items.DIAMOND, 63));
            fixture.menu().lockPlayerInventorySlot(0);
            var slots = slots(fixture, STONE);
            slots.getFirst().setByPlayer(new ItemStack(Items.DIAMOND, 3));
            transfer(helper, fixture, SLAB);
            assertStack(helper, inventory.getItem(0), Items.DIAMOND, 63);
            assertStack(helper, slots.getFirst().getItem(), Items.DIAMOND, capacity == 0 ? 3 : 1);
            helper.assertTrue(slots.getFirst().getItem().getCount() + host(fixture).count(Items.DIAMOND) == 3
                            && slots.getLast().getItem().isEmpty(),
                    "Full survival/creative inventories preserve blocked mismatches without loss or a false preview");
            var before = snapshot(fixture);
            var network = Map.copyOf(host(fixture).contents);
            transfer(helper, fixture, SLAB);
            assertUnchanged(helper, fixture, before, "Blocked overflow retry");
            helper.assertTrue(network.equals(host(fixture).contents), "Retry cannot change a full ME inventory");
        }
        helper.succeed();
    }

    private static TestTerminals.Fixture workstation(GameTestHelper helper, WorkstationKind kind) {
        var fixture = TestTerminals.create(helper);
        TestTerminals.install(fixture, kind.card());
        return fixture;
    }

    private static TestTerminals.Fixture network(GameTestHelper helper, WorkstationKind kind,
            int capacity, double power, boolean connected) {
        var player = TestTerminals.player(helper);
        var host = new StorageHost(player, capacity, power, connected);
        var menu = TestTerminals.menu(helper, player, host);
        host.source = menu.getActionSource();
        TestTerminals.install(menu, kind.card());
        return new TestTerminals.Fixture(player, menu, List.copyOf(menu.getSlots(MyoSlotSemantics.MYO_UPGRADE_SLOT)));
    }

    private static StorageHost host(TestTerminals.Fixture fixture) { return (StorageHost) fixture.menu().getHost(); }

    private static List<Slot> slots(TestTerminals.Fixture fixture, WorkstationKind kind) {
        return MyotusAPI.terminalModules().slots(fixture.menu(), kind.card());
    }

    private static ResourceLocation id(String recipe) { return ResourceLocation.withDefaultNamespace(recipe); }

    private static void transfer(GameTestHelper helper, TestTerminals.Fixture fixture, String recipe) {
        try {
            CraftingRecipeTransfer.transferWorkstation(fixture.player(), fixture.menu().containerId, id(recipe));
        } catch (RuntimeException error) {
            // This embedded player has not negotiated S2C state payloads; mutation finishes before synchronization.
            helper.assertTrue(error.getMessage() != null && error.getMessage().contains("may not be sent to the client"),
                    "Unexpected embedded-player error: " + error);
        }
    }

    private static List<ItemStack> snapshot(TestTerminals.Fixture fixture) {
        List<ItemStack> result = new ArrayList<>();
        var inventory = fixture.player().getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) result.add(inventory.getItem(i).copy());
        fixture.menu().slots.forEach(slot -> result.add(slot.getItem().copy()));
        result.add(fixture.menu().getCarried().copy());
        return result;
    }

    private static void assertUnchanged(GameTestHelper helper, TestTerminals.Fixture fixture,
            List<ItemStack> before, String reason) {
        var after = snapshot(fixture);
        helper.assertTrue(before.size() == after.size(), reason + " changed the slot layout");
        for (int i = 0; i < before.size(); i++) {
            helper.assertTrue(ItemStack.matches(before.get(i), after.get(i)), reason + " changed inventory/slot " + i);
        }
    }

    private static void assertStack(GameTestHelper helper, ItemStack stack, Item item, int count) {
        helper.assertTrue(stack.is(item) && stack.getCount() == count, "Expected " + count + " " + item + ", got " + stack);
    }

    private static final class StorageHost extends TestTerminals.TestHost implements MEStorage, IEnergySource, IViewCellStorage {
        private final Map<AEKey, Long> contents = new LinkedHashMap<>();
        private final AppEngInternalInventory viewCells = new AppEngInternalInventory(5);
        private final int capacity;
        private final boolean connected;
        private double power;
        private IActionSource source;
        private boolean allowed = true;
        private int wrongSourceCalls;
        private long inserted;
        private long extracted;

        private StorageHost(ServerPlayer player, int capacity, double power, boolean connected) {
            super(player);
            this.capacity = capacity;
            this.power = power;
            this.connected = connected;
        }

        @Override public MEStorage getInventory() { return this; }
        @Override public ILinkStatus getLinkStatus() { return connected ? ILinkStatus.ofConnected() : ILinkStatus.ofDisconnected(); }
        @Override public InternalInventory getViewCellStorage() { return viewCells; }
        @Override public Component getDescription() { return Component.literal("Workstation transfer bounded storage"); }

        private long count(Item item) { return contents.getOrDefault(AEItemKey.of(item), 0L); }

        private boolean permits(IActionSource actionSource) {
            if (actionSource != source || actionSource.player().orElse(null) != getPlayer()) {
                wrongSourceCalls++;
                return false;
            }
            return allowed;
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource actionSource) {
            MEStorage.checkPreconditions(key, amount, mode, actionSource);
            if (!permits(actionSource)) return 0;
            long count = Math.min(amount, Math.max(0, capacity - contents.values().stream().mapToLong(Long::longValue).sum()));
            if (mode == Actionable.MODULATE && count > 0) {
                contents.merge(key, count, Long::sum);
                inserted += count;
            }
            return count;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource actionSource) {
            MEStorage.checkPreconditions(key, amount, mode, actionSource);
            if (!permits(actionSource)) return 0;
            long count = Math.min(amount, contents.getOrDefault(key, 0L));
            if (mode == Actionable.MODULATE && count > 0) {
                contents.compute(key, (ignored, stored) -> stored == count ? null : stored - count);
                extracted += count;
            }
            return count;
        }

        @Override public void getAvailableStacks(KeyCounter out) { contents.forEach(out::add); }

        @Override
        public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
            double count = Math.min(power, multiplier.multiply(amount));
            if (mode == Actionable.MODULATE) power -= count;
            return multiplier.divide(count);
        }
    }
}
