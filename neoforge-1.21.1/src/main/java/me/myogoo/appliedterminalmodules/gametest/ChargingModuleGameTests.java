package me.myogoo.appliedterminalmodules.gametest;

import java.util.function.Consumer;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ILinkStatus;
import appeng.core.AEConfig;
import appeng.core.definitions.AEItems;
import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.AppliedTerminalModules;
import me.myogoo.appliedterminalmodules.init.ModItems;
import me.myogoo.appliedterminalmodules.menu.ChargingModuleProvider;
import me.myogoo.myotus.api.MyotusAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AppliedTerminalModules.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = AppliedTerminalModules.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ChargingModuleGameTests {
    private ChargingModuleGameTests() {}

    @SubscribeEvent
    public static void registerTestEnergy(RegisterCapabilitiesEvent event) {
        // Only marked stacks in the GameTest server get this synthetic FE capability.
        if (GameTestHooks.isGametestServer()) {
            event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, context) ->
                    stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains("atm_fe_test")
                            ? new TestEnergy(stack) : null, Items.BLAZE_ROD);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void aeToolChargesAtChargerRateAndPaysExactEnergy(GameTestHelper helper) {
        powered(helper, fixture -> {
            ItemStack staff = AEItems.CHARGED_STAFF.stack();
            fixture.slot().setByPlayer(staff);
            double before = stored(fixture.energy());
            tick(fixture.menu());
            var power = (IAEItemPowerStorage) staff.getItem();
            double charged = power.getAECurrentPower(staff);
            double expected = Math.min(before,
                    Math.min(800, power.getChargeRate(staff) * AEConfig.instance().getChargerChargeRate()));
            close(helper, charged, expected, "AE tool follows the charger rate");
            helper.assertTrue(charged > 0, "Connected ME energy must actually charge the tool");
            close(helper, before - stored(fixture.energy()), charged, "Exactly the delivered AE is spent");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void nearlyFullAndFullAEToolOnlyConsumesMissingCharge(GameTestHelper helper) {
        powered(helper, fixture -> {
            ItemStack staff = AEItems.CHARGED_STAFF.stack();
            var power = (IAEItemPowerStorage) staff.getItem();
            power.injectAEPower(staff, power.getAEMaxPower(staff) - 0.25, Actionable.MODULATE);
            fixture.slot().setByPlayer(staff);
            double before = stored(fixture.energy());
            tick(fixture.menu());
            close(helper, before - stored(fixture.energy()), 0.25, "Near-full AE item pays only for its missing charge");
            close(helper, power.getAECurrentPower(staff), power.getAEMaxPower(staff), "Item reaches full charge");
            double full = stored(fixture.energy());
            tick(fixture.menu());
            close(helper, stored(fixture.energy()), full, "Full AE item consumes no energy");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void feCapabilityChargesAtConfiguredConversionAndFullCostsNothing(GameTestHelper helper) {
        powered(helper, fixture -> {
            ItemStack tool = feTool("normal", 0);
            helper.assertTrue(fixture.slot().mayPlace(tool), "Native FE receiver is a valid charging input");
            fixture.slot().setByPlayer(tool);
            double before = stored(fixture.energy());
            tick(fixture.menu());
            int received = tool.getCapability(Capabilities.EnergyStorage.ITEM).getEnergyStored();
            helper.assertTrue(received > 0 && received <= 64, "FE receive limit is respected");
            close(helper, before - stored(fixture.energy()), PowerUnit.FE.convertTo(PowerUnit.AE, received),
                    "FE consumes the configured amount of ME energy");
            fixture.slot().setByPlayer(feTool("normal", 512));
            double full = stored(fixture.energy());
            tick(fixture.menu());
            close(helper, stored(fixture.energy()), full, "Full FE item consumes no energy");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void refusingAndPartialFEReceiversRefundUnusedPower(GameTestHelper helper) {
        powered(helper, fixture -> {
            double before = stored(fixture.energy());
            ItemStack refusing = feTool("refuse", 0);
            fixture.slot().setByPlayer(refusing);
            tick(fixture.menu());
            helper.assertTrue(refusing.getCapability(Capabilities.EnergyStorage.ITEM).getEnergyStored() == 0,
                    "Receiver that accepts simulation but refuses the actual transfer stays empty");
            close(helper, stored(fixture.energy()), before, "Actual refusal refunds all withdrawn ME power");
            ItemStack partial = feTool("partial", 0);
            fixture.slot().setByPlayer(partial);
            tick(fixture.menu());
            int received = partial.getCapability(Capabilities.EnergyStorage.ITEM).getEnergyStored();
            helper.assertTrue(received > 0 && received <= 3, "Actual FE acceptance, not simulation, is authoritative");
            close(helper, before - stored(fixture.energy()), PowerUnit.FE.convertTo(PowerUnit.AE, received),
                    "Partial receiver pays only for actual accepted FE");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void fractionalGridSupplyCannotCreateFreeFE(GameTestHelper helper) {
        powered(helper, fixture -> {
            var energy = fixture.energy();
            energy.extractAEPower(stored(energy), Actionable.MODULATE, PowerMultiplier.ONE);
            energy.injectPower(PowerUnit.FE.convertTo(PowerUnit.AE, 3.75), Actionable.MODULATE);
            double before = stored(energy);
            ItemStack tool = feTool("normal", 0);
            fixture.slot().setByPlayer(tool);
            tick(fixture.menu());
            int received = tool.getCapability(Capabilities.EnergyStorage.ITEM).getEnergyStored();
            helper.assertTrue(received == 3, "Fractional supply delivers only whole FE units");
            close(helper, before - stored(energy), PowerUnit.FE.convertTo(PowerUnit.AE, received),
                    "Sub-FE remainder stays in the network");
            tick(fixture.menu());
            helper.assertTrue(tool.getCapability(Capabilities.EnergyStorage.ITEM).getEnergyStored() == 3,
                    "A fraction of FE cannot be repeatedly rounded up into free energy");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void nativeInputRejectsTransformsAndOfflineOrRemovedCardsCannotCharge(GameTestHelper helper) {
        powered(helper, fixture -> {
            var slot = fixture.slot();
            helper.assertTrue(!slot.mayPlace(new ItemStack(Items.STONE))
                            && !slot.mayPlace(AEItems.CERTUS_QUARTZ_CRYSTAL.stack())
                            && !slot.mayPlace(new ItemStack(Items.COMPASS))
                            && !slot.mayPlace(feTool("read_only", 0)) && slot.getMaxStackSize() == 1,
                    "Only one native chargeable tool is accepted; recipe transforms and read-only FE are rejected");
            double before = stored(fixture.energy());
            slot.set(new ItemStack(Items.COMPASS));
            tick(fixture.menu());
            helper.assertTrue(slot.getItem().is(Items.COMPASS), "A forcibly inserted compass cannot be transformed");
            close(helper, stored(fixture.energy()), before, "Unchargeable items spend no ME power");
            ItemStack staff = AEItems.CHARGED_STAFF.stack();
            slot.setByPlayer(staff);
            fixture.host().connected = false;
            tick(fixture.menu());
            fixture.host().connected = true;
            fixture.menu().setValidMenu(false);
            tick(fixture.menu());
            fixture.menu().setValidMenu(true);
            TestTerminals.cardSlot(fixture.menu()).set(ItemStack.EMPTY);
            tick(fixture.menu());
            close(helper, ((IAEItemPowerStorage) staff.getItem()).getAECurrentPower(staff), 0,
                    "Disconnected hosts, invalid menus and stale card callbacks cannot charge");
            close(helper, stored(fixture.energy()), before, "Unauthorized/offline charging consumes nothing");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void removalAndCloseReturnOwnedItemExactlyOnce(GameTestHelper helper) {
        var fixture = TestTerminals.create(helper);
        TestTerminals.install(fixture, ModItems.CHARGING_MODULE.get());
        TestTerminals.broadcast(helper, fixture);
        var slot = slot(fixture.menu());
        slot.setByPlayer(AEItems.CHARGED_STAFF.stack());
        TestTerminals.remove(fixture);
        TestTerminals.broadcast(helper, fixture);
        fixture.menu().removed(fixture.player());
        ChargingModuleProvider.returnItem(fixture.menu());
        helper.assertTrue(slot.getItem().isEmpty()
                        && fixture.player().getInventory().countItem(AEItems.CHARGED_STAFF.get()) == 1,
                "Card removal and repeated close return the tool exactly once");
        var second = TestTerminals.create(helper);
        TestTerminals.install(second, ModItems.CHARGING_MODULE.get());
        slot(second.menu()).setByPlayer(AEItems.CHARGED_STAFF.stack());
        TestTerminals.remove(second);
        second.menu().removed(second.player());
        NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Close(second.player(), second.menu()));
        NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Close(second.player(), second.menu()));
        helper.assertTrue(second.player().getInventory().countItem(AEItems.CHARGED_STAFF.get()) == 1,
                "Close-event fallback also returns an item removed before the first broadcast");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fullInventoryDeathAndDisconnectDropInsteadOfLosingTools(GameTestHelper helper) {
        for (int mode = 0; mode < 3; mode++) {
            var fixture = TestTerminals.create(helper);
            var pos = helper.absolutePos(BlockPos.ZERO);
            fixture.player().setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
            var area = fixture.player().getBoundingBox().inflate(3);
            TestTerminals.install(fixture, ModItems.CHARGING_MODULE.get());
            var slot = slot(fixture.menu());
            slot.setByPlayer(AEItems.CHARGED_STAFF.stack());
            int before = droppedTools(helper, area);
            if (mode == 0) {
                var inventory = fixture.player().getInventory();
                for (int i = 0; i < inventory.getContainerSize(); i++) inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            } else if (mode == 1) fixture.player().setHealth(0);
            else {
                fixture.player().disconnect();
                helper.assertTrue(fixture.player().hasDisconnected(), "Test connection must be disconnected");
            }
            ChargingModuleProvider.returnItem(fixture.menu());
            ChargingModuleProvider.returnItem(fixture.menu());
            helper.assertTrue(slot.getItem().isEmpty() && droppedTools(helper, area) - before == 1
                            && fixture.player().getInventory().countItem(AEItems.CHARGED_STAFF.get()) == 0,
                    "Cleanup drops exactly once for full inventory/death/disconnect mode " + mode);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void menusOwnIndependentTransientChargingSlots(GameTestHelper helper) {
        var first = TestTerminals.create(helper);
        var second = TestTerminals.create(helper);
        TestTerminals.install(first, ModItems.CHARGING_MODULE.get());
        TestTerminals.install(second, ModItems.CHARGING_MODULE.get());
        slot(first.menu()).setByPlayer(AEItems.CHARGED_STAFF.stack());
        helper.assertTrue(slot(second.menu()).getItem().isEmpty(), "A second viewer owns a separate slot");
        first.menu().removed(first.player());
        helper.assertTrue(first.player().getInventory().countItem(AEItems.CHARGED_STAFF.get()) == 1
                        && second.player().getInventory().countItem(AEItems.CHARGED_STAFF.get()) == 0,
                "Closing only returns the owning viewer's item");
        helper.succeed();
    }

    private record Powered(ServerPlayer player, MEStorageMenu menu, GridHost host, IEnergyService energy) {
        Slot slot() { return ChargingModuleGameTests.slot(menu); }
    }

    private static void powered(GameTestHelper helper, Consumer<Powered> test) {
        var player = TestTerminals.player(helper);
        var host = new GridHost(player, helper);
        var menu = TestTerminals.menu(helper, player, host);
        TestTerminals.install(menu, ModItems.CHARGING_MODULE.get());
        var energy = host.node.getGrid().getEnergyService();
        energy.injectPower(10000, Actionable.MODULATE);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(host.node.isActive(), "Test ME grid must finish booting"))
                .thenExecute(() -> {
                    try { test.accept(new Powered(player, menu, host, energy)); }
                    finally { host.node.destroy(); }
                }).thenSucceed();
    }

    private static final class GridHost extends TestTerminals.TestHost implements IActionHost {
        private final IManagedGridNode node;
        private boolean connected = true;

        private GridHost(ServerPlayer player, GameTestHelper helper) {
            super(player);
            node = GridHelper.createManagedNode(this, (owner, changedNode) -> {})
                    .setInWorldNode(false).setFlags().setIdlePowerUsage(0);
            node.create(helper.getLevel(), null);
        }

        @Override
        public IGridNode getActionableNode() { return node.getNode(); }

        @Override
        public ILinkStatus getLinkStatus() {
            return connected ? ILinkStatus.ofManagedNode(node) : ILinkStatus.ofDisconnected();
        }
    }

    private static Slot slot(MEStorageMenu menu) {
        var slots = MyotusAPI.terminalModules().slots(menu, ModItems.CHARGING_MODULE.get());
        if (slots.size() != 1) throw new IllegalStateException("Charging module must expose exactly one native slot");
        return slots.getFirst();
    }

    private static void tick(MEStorageMenu menu) {
        ModItems.CHARGING_MODULE.get().onTerminalTick(menu, ModItems.CHARGING_MODULE.toStack());
    }

    private static double stored(IEnergyService energy) {
        return energy.extractAEPower(100000, Actionable.SIMULATE, PowerMultiplier.ONE);
    }

    private static void close(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 0.000001, message + ": " + actual + " != " + expected);
    }

    private static int droppedTools(GameTestHelper helper, net.minecraft.world.phys.AABB area) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
                .filter(entity -> entity.getItem().is(AEItems.CHARGED_STAFF.get()))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static ItemStack feTool(String mode, int energy) {
        var stack = new ItemStack(Items.BLAZE_ROD);
        var tag = new CompoundTag();
        tag.putString("atm_fe_test", mode);
        tag.putInt("energy", energy);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private record TestEnergy(ItemStack stack) implements IEnergyStorage {
        private CompoundTag data() { return stack.get(DataComponents.CUSTOM_DATA).copyTag(); }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            var tag = data();
            String mode = tag.getString("atm_fe_test");
            if (!canReceive() || (!simulate && mode.equals("refuse"))) return 0;
            int limit = !simulate && mode.equals("partial") ? 3 : 64;
            int accepted = Math.max(0, Math.min(amount, Math.min(limit, 512 - getEnergyStored())));
            if (!simulate) {
                tag.putInt("energy", getEnergyStored() + accepted);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            return accepted;
        }

        @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return data().getInt("energy"); }
        @Override public int getMaxEnergyStored() { return 512; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return !data().getString("atm_fe_test").equals("read_only"); }
    }
}
