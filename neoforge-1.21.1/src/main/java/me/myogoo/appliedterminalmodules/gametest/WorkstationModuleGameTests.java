package me.myogoo.appliedterminalmodules.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import appeng.menu.SlotSemantics;
import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.AppliedTerminalModules;
import me.myogoo.appliedterminalmodules.init.ModItems;
import me.myogoo.appliedterminalmodules.menu.WorkstationKind;
import me.myogoo.appliedterminalmodules.menu.WorkstationModuleProvider;
import me.myogoo.myotus.api.MyotusAPI;
import me.myogoo.myotus.api.terminal.TerminalModuleSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AppliedTerminalModules.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkstationModuleGameTests {
    private WorkstationModuleGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void permanentLayoutsAndViewersAreIndependent(GameTestHelper helper) {
        var first = TestTerminals.create(helper);
        var second = TestTerminals.create(helper);
        int total = first.menu().slots.size();
        for (var kind : WorkstationKind.values()) {
            var slots = slots(first.menu(), kind);
            helper.assertTrue(slots.size() == kind.inputCount() + 1 && slots.stream().noneMatch(Slot::isActive),
                    "Each absent workstation has deterministic disabled input/output slots: " + kind);
            TestTerminals.install(first, kind.card());
            helper.assertTrue(slots.stream().allMatch(Slot::isActive) && !slots.getLast().mayPlace(new ItemStack(Items.STONE)),
                    "Installing activates the native slots, with a read-only result: " + kind);
            helper.assertTrue(new WorkstationModuleProvider(kind)
                            .getCloneStack(((TerminalModuleSlot) slots.getLast()).getDelegate()).isEmpty(),
                    "Output clone stacks are always empty: " + kind);
            slots.getFirst().set(new ItemStack(Items.STONE));
            helper.assertTrue(slots(second.menu(), kind).stream().allMatch(slot -> slot.getItem().isEmpty()),
                    "Inputs cannot be shared between terminal viewers");
            WorkstationModuleProvider.returnInputs(first.menu(), kind);
            TestTerminals.remove(first);
        }
        helper.assertTrue(first.menu().slots.size() == total, "Installing and removing never changes packet slot indices");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void stonecuttingUsesRecipeIdsAndConsumesOneInput(GameTestHelper helper) {
        var fixture = workstation(helper, WorkstationKind.STONECUTTING);
        var slots = slots(fixture.menu(), WorkstationKind.STONECUTTING);
        slots.getFirst().setByPlayer(new ItemStack(Items.STONE, 2));
        helper.assertTrue(slots.getLast().getItem().isEmpty(), "An input alone must not select a recipe");
        var recipe = slabRecipe(fixture.menu());
        helper.assertTrue(!WorkstationModuleProvider.selectRecipe(fixture.menu(), ResourceLocation.withDefaultNamespace("missing")),
                "An unknown ID cannot select an output");
        embeddedPacket(helper, () -> WorkstationModuleProvider.selectRecipe(fixture.menu(), recipe));
        helper.assertTrue(recipe.equals(WorkstationModuleProvider.selectedRecipe(fixture.menu())),
                "The server selection retains the recipe ID");
        fixture.menu().clicked(slots.getLast().index, 1, ClickType.PICKUP, fixture.player());
        assertItem(helper, fixture.menu().getCarried(), Items.STONE_SLAB, 2);
        helper.assertTrue(slots.getFirst().getItem().getCount() == 1, "One complete output consumes one input");
        assertItem(helper, slots.getLast().getItem(), Items.STONE_SLAB, 2);
        fixture.menu().setCarried(ItemStack.EMPTY);
        assertItem(helper, slots.getLast().safeTake(2, 2, fixture.player()), Items.STONE_SLAB, 2);
        helper.assertTrue(slots.getFirst().getItem().isEmpty() && slots.getLast().getItem().isEmpty()
                        && WorkstationModuleProvider.selectedRecipe(fixture.menu()) == null,
                "The last take clears both input and selected output");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void stonecuttingRejectsPartialTakesAndPreservesNumberKeyRules(GameTestHelper helper) {
        var fixture = workstation(helper, WorkstationKind.STONECUTTING);
        var slots = slots(fixture.menu(), WorkstationKind.STONECUTTING);
        slots.getFirst().setByPlayer(new ItemStack(Items.STONE, 2));
        embeddedPacket(helper, () -> WorkstationModuleProvider.selectRecipe(fixture.menu(), slabRecipe(fixture.menu())));
        var output = slots.getLast();
        helper.assertTrue(output.remove(1).isEmpty() && output.safeTake(1, Integer.MAX_VALUE, fixture.player()).isEmpty()
                        && output.tryRemove(2, 1, fixture.player()).isEmpty(),
                "Partial direct, shift-capacity and cursor-capacity takes cannot extract a preview");
        fixture.menu().setCarried(new ItemStack(Items.STONE_SLAB, 63));
        fixture.menu().clicked(output.index, 0, ClickType.PICKUP, fixture.player());
        assertItem(helper, fixture.menu().getCarried(), Items.STONE_SLAB, 63);
        helper.assertTrue(slots.getFirst().getItem().getCount() == 2, "Insufficient cursor room preserves all inputs");
        fixture.menu().setCarried(ItemStack.EMPTY);
        for (int i = 0; i < 36; i++) fixture.player().getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        fixture.player().getInventory().setItem(0, new ItemStack(Items.STONE_SLAB, 63));
        fixture.menu().quickMoveStack(fixture.player(), output.index);
        helper.assertTrue(slots.getFirst().getItem().getCount() == 2, "One free inventory space cannot consume a two-item recipe");
        fixture.player().getInventory().setItem(0, ItemStack.EMPTY);
        fixture.menu().clicked(output.index, 0, ClickType.SWAP, fixture.player());
        assertItem(helper, fixture.player().getInventory().getItem(0), Items.STONE_SLAB, 2);
        helper.assertTrue(slots.getFirst().getItem().getCount() == 1, "Number-key transfer invokes native consumption once");
        fixture.player().getAbilities().instabuild = true;
        fixture.menu().clicked(output.index, 2, ClickType.CLONE, fixture.player());
        helper.assertTrue(fixture.menu().getCarried().isEmpty(), "Creative cloning cannot copy a workstation result");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void anvilRepairUsesNativeExperienceAndMaterialCost(GameTestHelper helper) {
        var fixture = workstation(helper, WorkstationKind.ANVIL);
        var slots = slots(fixture.menu(), WorkstationKind.ANVIL);
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(100);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Keep this name"));
        slots.getFirst().setByPlayer(sword);
        slots.get(1).setByPlayer(new ItemStack(Items.IRON_INGOT, 5));
        int cost = WorkstationModuleProvider.anvilCost(fixture.menu());
        helper.assertTrue(cost > 0 && !slots.getLast().mayPickup(fixture.player()), "Survival repair requires enough levels");
        helper.assertTrue(slots.getLast().safeTake(1, 1, fixture.player()).isEmpty(), "A low-level player cannot consume the preview");
        fixture.player().experienceLevel = 30;
        var result = slots.getLast().safeTake(1, 1, fixture.player());
        helper.assertTrue(result.is(Items.IRON_SWORD) && result.getDamageValue() == 0
                        && "Keep this name".equals(result.getHoverName().getString()),
                "Native material repair preserves the original custom name");
        helper.assertTrue(fixture.player().experienceLevel == 30 - cost && slots.getFirst().getItem().isEmpty()
                        && slots.get(1).getItem().getCount() == 3 && slots.getLast().getItem().isEmpty(),
                "Two ingots and the actual displayed level cost are consumed exactly once");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void anvilRenameAndTooExpensiveRulesRemainVanilla(GameTestHelper helper) {
        var fixture = workstation(helper, WorkstationKind.ANVIL);
        var slots = slots(fixture.menu(), WorkstationKind.ANVIL);
        fixture.player().experienceLevel = 100;
        slots.getFirst().setByPlayer(new ItemStack(Items.DIAMOND_SWORD));
        helper.assertTrue(!WorkstationModuleProvider.rename(fixture.menu(), "x".repeat(51)), "The native name length limit is enforced");
        embeddedPacket(helper, () -> WorkstationModuleProvider.rename(fixture.menu(), "Module sword"));
        helper.assertTrue(WorkstationModuleProvider.anvilCost(fixture.menu()) == 1, "Plain renaming costs one level");
        var renamed = slots.getLast().safeTake(1, 1, fixture.player());
        helper.assertTrue("Module sword".equals(renamed.getHoverName().getString()) && fixture.player().experienceLevel == 99,
                "Renaming applies the server-approved name and consumes its level");
        var expensive = new ItemStack(Items.DIAMOND_SWORD);
        expensive.setDamageValue(100);
        expensive.set(DataComponents.REPAIR_COST, 40);
        slots.getFirst().setByPlayer(expensive);
        slots.get(1).setByPlayer(new ItemStack(Items.DIAMOND));
        helper.assertTrue(WorkstationModuleProvider.anvilCost(fixture.menu()) >= 40 && slots.getLast().getItem().isEmpty(),
                "Survival repairs costing forty or more levels have no usable result");
        fixture.player().getAbilities().instabuild = true;
        slots.get(1).setByPlayer(new ItemStack(Items.DIAMOND, 2));
        helper.assertTrue(slots.getLast().mayPickup(fixture.player()) && slots.getLast().safeTake(1, 1, fixture.player()).is(Items.DIAMOND_SWORD)
                        && fixture.player().experienceLevel == 99,
                "Creative repairs bypass the cap and do not consume experience levels");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void anvilEventsRemainNativeAndIdleTicksDoNotRecompute(GameTestHelper helper) {
        var fixture = workstation(helper, WorkstationKind.ANVIL);
        fixture.player().experienceLevel = 10;
        var updates = new AtomicInteger();
        var repairs = new AtomicInteger();
        Consumer<AnvilUpdateEvent> update = event -> {
            if (event.getPlayer() != fixture.player()) return;
            updates.incrementAndGet();
            event.setOutput(new ItemStack(Items.STONE, 2));
            event.setCost(3);
            event.setMaterialCost(1);
        };
        Consumer<AnvilRepairEvent> repair = event -> {
            if (event.getEntity() != fixture.player()) return;
            repairs.incrementAndGet();
            event.setBreakChance(1.0F);
        };
        NeoForge.EVENT_BUS.addListener(update);
        NeoForge.EVENT_BUS.addListener(repair);
        try {
            var slots = slots(fixture.menu(), WorkstationKind.ANVIL);
            slots.getFirst().setByPlayer(new ItemStack(Items.STICK));
            slots.get(1).setByPlayer(new ItemStack(Items.IRON_INGOT, 2));
            int before = updates.get();
            for (int i = 0; i < 40; i++) embeddedPacket(helper, () -> WorkstationModuleProvider.refresh(fixture.menu(), WorkstationKind.ANVIL));
            helper.assertTrue(updates.get() == before, "Idle refresh must not repeat NeoForge recipe events");
            assertItem(helper, slots.getLast().getItem(), Items.STONE, 2);
            var position = fixture.player().blockPosition();
            helper.getLevel().setBlockAndUpdate(position, Blocks.DAMAGED_ANVIL.defaultBlockState());
            assertItem(helper, slots.getLast().safeTake(2, 2, fixture.player()), Items.STONE, 2);
            helper.assertTrue(fixture.player().experienceLevel == 7 && repairs.get() == 1
                            && slots.get(1).getItem().getCount() == 1
                            && helper.getLevel().getBlockState(position).is(Blocks.DAMAGED_ANVIL),
                    "Native event cost/consumption runs, while NULL access never damages the terminal block");
        } finally {
            NeoForge.EVENT_BUS.unregister(update);
            NeoForge.EVENT_BUS.unregister(repair);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void smithingRequiresAndConsumesAllThreeNativeIngredients(GameTestHelper helper) {
        var fixture = workstation(helper, WorkstationKind.SMITHING);
        var slots = slots(fixture.menu(), WorkstationKind.SMITHING);
        helper.assertTrue(!slots.getFirst().mayPlace(new ItemStack(Items.STICK))
                        && !slots.get(1).mayPlace(new ItemStack(Items.STICK)) && !slots.get(2).mayPlace(new ItemStack(Items.STICK)),
                "Native smithing slot predicates reject unrelated ingredients");
        var base = new ItemStack(Items.DIAMOND_SWORD);
        base.setDamageValue(42);
        base.set(DataComponents.CUSTOM_NAME, Component.literal("Upgrade me"));
        slots.get(1).setByPlayer(base);
        slots.get(2).setByPlayer(new ItemStack(Items.NETHERITE_INGOT, 2));
        helper.assertTrue(slots.getLast().getItem().isEmpty(), "Upgrading requires a smithing template");
        slots.getFirst().setByPlayer(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2));
        var result = slots.getLast().safeTake(1, 1, fixture.player());
        helper.assertTrue(result.is(Items.NETHERITE_SWORD) && result.getDamageValue() == 42
                        && "Upgrade me".equals(result.getHoverName().getString()),
                "Vanilla assembly preserves the base item's components");
        helper.assertTrue(slots.getFirst().getItem().getCount() == 1 && slots.get(1).getItem().isEmpty()
                        && slots.get(2).getItem().getCount() == 1 && slots.getLast().getItem().isEmpty(),
                "One template, one base and one addition are consumed, clearing the result");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void grindstoneRemovesEnchantmentsKeepsCursesAndAwardsExperience(GameTestHelper helper) {
        var fixture = workstation(helper, WorkstationKind.GRINDSTONE);
        var slots = slots(fixture.menu(), WorkstationKind.GRINDSTONE);
        var sword = new ItemStack(Items.IRON_SWORD);
        var enchantments = fixture.player().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var sharpness = enchantments.getOrThrow(Enchantments.SHARPNESS);
        var curse = enchantments.getOrThrow(Enchantments.VANISHING_CURSE);
        sword.enchant(sharpness, 2);
        sword.enchant(curse, 1);
        sword.set(DataComponents.REPAIR_COST, 31);
        slots.getFirst().setByPlayer(sword);
        int before = experience(fixture);
        var result = slots.getLast().safeTake(1, 1, fixture.player());
        var resultEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(result);
        helper.assertTrue(result.is(Items.IRON_SWORD) && resultEnchantments.getLevel(sharpness) == 0
                        && resultEnchantments.getLevel(curse) == 1 && result.getOrDefault(DataComponents.REPAIR_COST, 0) == 1,
                "Grindstone removes ordinary enchantments, preserves curses and recalculates repair cost");
        helper.assertTrue(slots.stream().allMatch(slot -> slot.getItem().isEmpty()) && experience(fixture) > before,
                "Actual server access consumes inputs and spawns experience at the viewer");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void grindstoneEventsCanControlExperienceAndRemainingInputs(GameTestHelper helper) {
        var fixture = workstation(helper, WorkstationKind.GRINDSTONE);
        var takes = new AtomicInteger();
        Consumer<GrindstoneEvent.OnTakeItem> listener = event -> {
            if (event.getPlayer() != fixture.player()) return;
            helper.assertTrue(event.getContainerAccess().evaluate((level, position) -> position.equals(fixture.player().blockPosition()), false),
                    "The native event receives the actual server location");
            takes.incrementAndGet();
            event.setXp(7);
            event.setNewTopItem(new ItemStack(Items.STICK));
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            var slots = slots(fixture.menu(), WorkstationKind.GRINDSTONE);
            var sword = new ItemStack(Items.IRON_SWORD);
            sword.enchant(fixture.player().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 1);
            slots.getFirst().setByPlayer(sword);
            int before = experience(fixture);
            helper.assertTrue(slots.getLast().safeTake(1, 1, fixture.player()).is(Items.IRON_SWORD)
                            && slots.getFirst().getItem().is(Items.STICK) && takes.get() == 1 && experience(fixture) - before == 7,
                    "Native take-event remainders and experience overrides remain intact");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void grindstoneMultiItemResultCannotBePartiallyExtracted(GameTestHelper helper) {
        var fixture = workstation(helper, WorkstationKind.GRINDSTONE);
        var slots = slots(fixture.menu(), WorkstationKind.GRINDSTONE);
        var book = new ItemStack(Items.BOOK);
        book.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        book.enchant(fixture.player().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 1);
        slots.getFirst().setByPlayer(book.copy());
        slots.get(1).setByPlayer(book.copy());
        assertItem(helper, slots.getLast().getItem(), Items.BOOK, 2);
        helper.assertTrue(slots.getLast().safeTake(1, 2, fixture.player()).isEmpty()
                        && slots.getLast().remove(1).isEmpty() && slots.getLast().tryRemove(2, 1, fixture.player()).isEmpty(),
                "Two-book results reject all partial-capacity extraction routes");
        assertItem(helper, slots.getLast().safeTake(2, 2, fixture.player()), Items.BOOK, 2);
        helper.assertTrue(slots.stream().allMatch(slot -> slot.getItem().isEmpty()), "The full batch consumes both enchanted inputs once");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void removalImmediateCloseReturnsInputsExactlyOnce(GameTestHelper helper) {
        for (var kind : WorkstationKind.values()) {
            var fixture = workstation(helper, kind);
            var slots = slots(fixture.menu(), kind);
            slots.getFirst().set(new ItemStack(Items.STONE, 3));
            TestTerminals.remove(fixture);
            fixture.menu().clicked(slots.getFirst().index, 0, ClickType.PICKUP, fixture.player());
            fixture.menu().clicked(slots.getLast().index, 0, ClickType.PICKUP, fixture.player());
            helper.assertTrue(fixture.menu().getCarried().isEmpty(), "Removed cards immediately reject stale input/output clicks");
            fixture.menu().removed(fixture.player());
            NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Close(fixture.player(), fixture.menu()));
            helper.assertTrue(fixture.player().getInventory().countItem(Items.STONE) == 3
                            && slots.stream().allMatch(slot -> slot.getItem().isEmpty()),
                    "Immediate removal/close must return inputs even without the first card tick: " + kind);
            WorkstationModuleProvider.returnInputs(fixture.menu(), kind);
            WorkstationModuleProvider.returnInputs(fixture.menu(), kind);
            helper.assertTrue(fixture.player().getInventory().countItem(Items.STONE) == 3
                            && slots.stream().allMatch(slot -> slot.getItem().isEmpty()),
                    "Close fallback returns each viewer's inputs once, never the preview: " + kind);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void actionsRejectRemovedCardsStaleMenusAndClientStateOnServer(GameTestHelper helper) {
        var fixture = workstation(helper, WorkstationKind.STONECUTTING);
        var slots = slots(fixture.menu(), WorkstationKind.STONECUTTING);
        slots.getFirst().setByPlayer(new ItemStack(Items.STONE));
        var recipe = slabRecipe(fixture.menu());
        TestTerminals.remove(fixture);
        helper.assertTrue(!WorkstationModuleProvider.selectRecipe(fixture.menu(), recipe), "Removed cards reject selection packets");
        TestTerminals.install(fixture, WorkstationKind.STONECUTTING.card());
        var otherMenu = TestTerminals.menu(helper, fixture.player());
        helper.assertTrue(otherMenu != fixture.menu() && !WorkstationModuleProvider.selectRecipe(fixture.menu(), recipe),
                "A previous menu cannot process an action for the current player");
        WorkstationModuleProvider.applyClientState(fixture.menu(), recipe, 100);
        helper.assertTrue(WorkstationModuleProvider.selectedRecipe(fixture.menu()) == null,
                "Client-only presentation state cannot select a server output");
        var anvil = workstation(helper, WorkstationKind.ANVIL);
        slots(anvil.menu(), WorkstationKind.ANVIL).getFirst().setByPlayer(new ItemStack(Items.IRON_SWORD));
        TestTerminals.remove(anvil);
        helper.assertTrue(!WorkstationModuleProvider.rename(anvil.menu(), "stale"), "Removed anvil cards reject rename packets");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pickAllCollectsInventoryItemsWithoutTakingWorkstationPreviews(GameTestHelper helper) {
        // Smithing's vanilla results are unstackable; these three expose the actual pick-all consumption route.
        for (var kind : List.of(WorkstationKind.STONECUTTING, WorkstationKind.ANVIL, WorkstationKind.GRINDSTONE)) {
            var fixture = workstation(helper, kind);
            fixture.player().experienceLevel = 20;
            var slots = slots(fixture.menu(), kind);
            switch (kind) {
                case STONECUTTING -> {
                    slots.getFirst().setByPlayer(new ItemStack(Items.STONE, 2));
                    embeddedPacket(helper, () -> WorkstationModuleProvider.selectRecipe(fixture.menu(), slabRecipe(fixture.menu())));
                }
                case ANVIL -> {
                    slots.getFirst().setByPlayer(new ItemStack(Items.IRON_INGOT, 5));
                    embeddedPacket(helper, () -> WorkstationModuleProvider.rename(fixture.menu(), "Pick-all ingots"));
                }
                case GRINDSTONE -> {
                    var book = new ItemStack(Items.BOOK);
                    book.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                    book.enchant(fixture.player().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                            .getOrThrow(Enchantments.SHARPNESS), 1);
                    slots.getFirst().setByPlayer(book.copy());
                    slots.get(1).setByPlayer(book.copy());
                }
                default -> throw new AssertionError(kind);
            }
            var output = slots.getLast();
            var preview = output.getItem().copy();
            helper.assertTrue(!preview.isEmpty() && preview.getMaxStackSize() > 1, "Use a stackable native result: " + kind);
            var inputs = slots.subList(0, kind.inputCount()).stream().map(slot -> slot.getItem().copy()).toList();
            int cost = WorkstationModuleProvider.anvilCost(fixture.menu());
            int xp = experience(fixture);
            var ordinary = fixture.menu().getSlots(SlotSemantics.PLAYER_INVENTORY).getFirst();
            var empty = fixture.menu().getSlots(SlotSemantics.PLAYER_HOTBAR).getLast();
            ordinary.setByPlayer(preview.copyWithCount(3));
            fixture.menu().setCarried(preview.copyWithCount(1));
            fixture.menu().clicked(empty.index, 0, ClickType.PICKUP_ALL, fixture.player());
            helper.assertTrue(ItemStack.matches(fixture.menu().getCarried(), preview.copyWithCount(4)) && ordinary.getItem().isEmpty(),
                    "Pick-all still collects three matching ordinary inventory items: " + kind);
            helper.assertTrue(ItemStack.matches(output.getItem(), preview)
                            && fixture.player().experienceLevel == 20 && experience(fixture) == xp
                            && WorkstationModuleProvider.anvilCost(fixture.menu()) == cost,
                    "Pick-all must leave the preview, levels, orbs and displayed cost unchanged: " + kind);
            for (int i = 0; i < inputs.size(); i++) {
                helper.assertTrue(ItemStack.matches(slots.get(i).getItem(), inputs.get(i)),
                        "Pick-all cannot consume workstation input " + i + ": " + kind);
            }
            helper.assertTrue(!fixture.menu().canTakeItemForPickAll(preview, output)
                            && fixture.menu().canTakeItemForPickAll(preview, ordinary),
                    "The exclusion applies to module results while preserving ordinary inventory collection");
            fixture.menu().clicked(output.index, 0, ClickType.PICKUP, fixture.player());
            helper.assertTrue(ItemStack.matches(fixture.menu().getCarried(), preview.copyWithCount(4 + preview.getCount())),
                    "Normal pickup can still acquire a complete result after pick-all: " + kind);
            helper.assertTrue(kind == WorkstationKind.STONECUTTING
                            ? slots.getFirst().getItem().getCount() == 1
                            : slots.subList(0, kind.inputCount()).stream().allMatch(slot -> slot.getItem().isEmpty()),
                    "Normal pickup invokes native ingredient consumption: " + kind);
            if (kind == WorkstationKind.ANVIL) helper.assertTrue(fixture.player().experienceLevel == 20 - cost,
                    "Only the explicit anvil output pickup spends levels");
            if (kind == WorkstationKind.GRINDSTONE) helper.assertTrue(experience(fixture) > xp,
                    "Only the explicit grindstone output pickup awards experience");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pickAllAlsoExcludesTheExistingCraftingModuleResult(GameTestHelper helper) {
        var fixture = TestTerminals.create(helper);
        TestTerminals.install(fixture, ModItems.CRAFTING_MODULE.get());
        var slots = MyotusAPI.terminalModules().slots(fixture.menu(), ModItems.CRAFTING_MODULE.get());
        slots.getFirst().setByPlayer(new ItemStack(Items.OAK_LOG, 3));
        var ordinary = fixture.menu().getSlots(SlotSemantics.PLAYER_INVENTORY).getFirst();
        var empty = fixture.menu().getSlots(SlotSemantics.PLAYER_HOTBAR).getLast();
        ordinary.setByPlayer(new ItemStack(Items.OAK_PLANKS, 3));
        fixture.menu().setCarried(new ItemStack(Items.OAK_PLANKS));
        fixture.menu().clicked(empty.index, 0, ClickType.PICKUP_ALL, fixture.player());
        assertItem(helper, fixture.menu().getCarried(), Items.OAK_PLANKS, 4);
        assertItem(helper, slots.getLast().getItem(), Items.OAK_PLANKS, 4);
        helper.assertTrue(ordinary.getItem().isEmpty() && slots.getFirst().getItem().getCount() == 3
                        && !fixture.menu().canTakeItemForPickAll(fixture.menu().getCarried(), slots.getLast()),
                "Pick-all collects inventory planks without consuming logs or collecting the crafting preview");
        fixture.menu().clicked(slots.getLast().index, 0, ClickType.PICKUP, fixture.player());
        assertItem(helper, fixture.menu().getCarried(), Items.OAK_PLANKS, 8);
        assertItem(helper, slots.getLast().getItem(), Items.OAK_PLANKS, 4);
        helper.assertTrue(slots.getFirst().getItem().getCount() == 2, "Normal crafting pickup still consumes one recipe");
        helper.succeed();
    }

    private static TestTerminals.Fixture workstation(GameTestHelper helper, WorkstationKind kind) {
        var player = TestTerminals.player(helper);
        var position = helper.absolutePos(BlockPos.ZERO);
        player.setPos(position.getX() + 0.5, position.getY() + 1, position.getZ() + 0.5);
        var menu = TestTerminals.menu(helper, player);
        TestTerminals.install(menu, kind.card());
        return new TestTerminals.Fixture(player, menu, List.of(TestTerminals.cardSlot(menu)));
    }

    private static List<Slot> slots(MEStorageMenu menu, WorkstationKind kind) {
        return MyotusAPI.terminalModules().slots(menu, kind.card());
    }

    private static ResourceLocation slabRecipe(MEStorageMenu menu) {
        return WorkstationModuleProvider.recipes(menu).stream()
                .filter(recipe -> recipe.value().getResultItem(menu.getPlayer().registryAccess()).is(Items.STONE_SLAB))
                .findFirst().orElseThrow().id();
    }

    private static int experience(TestTerminals.Fixture fixture) {
        return fixture.player().serverLevel().getEntitiesOfClass(ExperienceOrb.class, fixture.player().getBoundingBox().inflate(2))
                .stream().mapToInt(orb -> {
                    var data = new CompoundTag();
                    orb.addAdditionalSaveData(data);
                    return orb.getValue() * data.getInt("Count");
                }).sum();
    }

    private static void assertItem(GameTestHelper helper, ItemStack stack, net.minecraft.world.item.Item item, int count) {
        helper.assertTrue(stack.is(item) && stack.getCount() == count, "Expected " + count + " " + item + ", got " + stack);
    }

    private static void embeddedPacket(GameTestHelper helper, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException error) {
            helper.assertTrue(error.getMessage() != null && error.getMessage().contains("may not be sent to the client"),
                    "Unexpected embedded-player error: " + error);
        }
    }
}
