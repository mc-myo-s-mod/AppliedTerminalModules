package me.myogoo.appliedterminalmodules.gametest;

import me.myogoo.appliedterminalmodules.AppliedTerminalModules;
import me.myogoo.appliedterminalmodules.init.ModItems;
import me.myogoo.appliedterminalmodules.menu.ModuleSemantics;
import me.myogoo.myotus.api.MyotusAPI;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AppliedTerminalModules.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EquipmentModuleGameTests {
    private EquipmentModuleGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void equipmentUsesLiveInventoryAndVanillaRules(GameTestHelper helper) {
        var player = TestTerminals.player(helper);
        var menu = TestTerminals.menu(helper, player);
        var slots = menu.getSlots(ModuleSemantics.EQUIPMENT);
        helper.assertTrue(slots.size() == 5 && slots.stream().noneMatch(Slot::isActive),
                "Five permanent equipment slots must start disabled");
        TestTerminals.install(menu, ModItems.EQUIPMENT_MODULE.get());
        helper.assertTrue(slots.stream().allMatch(Slot::isActive), "Installing the card activates all five slots");
        Slot helmet = slots.getFirst();
        helper.assertTrue(helmet.mayPlace(new ItemStack(Items.IRON_HELMET))
                && !helmet.mayPlace(new ItemStack(Items.STICK))
                && !helmet.mayPlace(new ItemStack(Items.IRON_CHESTPLATE)) && helmet.getMaxStackSize() == 1,
                "Armor slots retain their native validation and stack limit");
        helmet.setByPlayer(new ItemStack(Items.IRON_HELMET));
        helper.assertTrue(player.getInventory().getItem(39).is(Items.IRON_HELMET),
                "Equipment writes directly to the player's live armor inventory");
        slots.getLast().setByPlayer(new ItemStack(Items.SHIELD));
        helper.assertTrue(player.getOffhandItem().is(Items.SHIELD), "Offhand writes to the actual offhand");
        var cursed = new ItemStack(Items.IRON_HELMET);
        cursed.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.BINDING_CURSE), 1);
        helmet.set(cursed);
        menu.clicked(helmet.index, 0, ClickType.PICKUP, player);
        menu.quickMoveStack(player, helmet.index);
        helper.assertTrue(!helmet.mayPickup(player) && ItemStack.matches(helmet.getItem(), cursed)
                && menu.getCarried().isEmpty(), "Binding curse blocks ordinary pickup and shift-click");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cardRemovalLocksSlotsWithoutDeletingEquipment(GameTestHelper helper) {
        var player = TestTerminals.player(helper);
        var menu = TestTerminals.menu(helper, player);
        TestTerminals.install(menu, ModItems.EQUIPMENT_MODULE.get());
        var slots = menu.getSlots(ModuleSemantics.EQUIPMENT);
        int originalCount = menu.slots.size();
        slots.getFirst().setByPlayer(new ItemStack(Items.IRON_HELMET));
        slots.getLast().setByPlayer(new ItemStack(Items.SHIELD));
        TestTerminals.cardSlot(menu).set(ItemStack.EMPTY);
        for (Slot slot : slots) {
            helper.assertTrue(!slot.isActive() && !slot.mayPickup(player) && slot.remove(1).isEmpty(),
                    "A removed module cannot be used through stale packet slot IDs");
        }
        helper.assertTrue(player.getInventory().getItem(39).is(Items.IRON_HELMET)
                && player.getOffhandItem().is(Items.SHIELD) && menu.slots.size() == originalCount,
                "Removing a module preserves gear and all packet slot indices");
        TestTerminals.install(menu, ModItems.EQUIPMENT_MODULE.get());
        menu.quickMoveStack(player, slots.getLast().index);
        helper.assertTrue(player.getOffhandItem().isEmpty() && player.getInventory().countItem(Items.SHIELD) == 1,
                "Shift-click returns one offhand item to the player's inventory");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void curiosAvailabilityMatchesInstalledDependency(GameTestHelper helper) {
        var player = TestTerminals.player(helper);
        var menu = TestTerminals.menu(helper, player);
        boolean loaded = ModList.get().isLoaded("curios");
        helper.assertTrue(ModItems.CURIOS_MODULE.get().isAvailable() == loaded,
                "Curios card availability follows the optional dependency");
        if (!loaded) {
            helper.assertTrue(!TestTerminals.cardSlot(menu).mayPlace(ModItems.CURIOS_MODULE.toStack())
                    && MyotusAPI.terminalModules().slots(menu, ModItems.CURIOS_MODULE.get()).isEmpty(),
                    "Without Curios the card is not installable and no Curios slot provider loads");
        }
        helper.succeed();
    }
}
