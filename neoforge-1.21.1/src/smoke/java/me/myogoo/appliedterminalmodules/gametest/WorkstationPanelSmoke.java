package me.myogoo.appliedterminalmodules.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import appeng.client.Point;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.StyleManager;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantic;
import appeng.menu.me.common.MEStorageMenu;
import com.mojang.logging.LogUtils;
import me.myogoo.appliedterminalmodules.client.ChargingModulePanel;
import me.myogoo.appliedterminalmodules.client.ModulePanelPositions;
import me.myogoo.appliedterminalmodules.client.WorkstationModulePanel;
import me.myogoo.appliedterminalmodules.init.ModItems;
import me.myogoo.appliedterminalmodules.menu.ModuleSemantics;
import me.myogoo.appliedterminalmodules.menu.WorkstationModuleProvider;
import me.myogoo.myotus.client.TerminalEquipmentPanel;
import me.myogoo.myotus.client.gui.config.CustomConfigTabScreen;
import me.myogoo.myotus.config.MyotusConfig;
import me.myogoo.myotus.impl.ConfigManager;
import me.myogoo.myotus.menu.MyoSlotSemantics;
import me.myogoo.myotus.mixin.ae2.WidgetContainerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

/** Client-only fixtures and native AE2 UI input; this does not test server item movement. */
public final class WorkstationPanelSmoke {
    private record PanelCase(String name, Item card, SlotSemantic semantic, int slotCount,
            ModConfigSpec.ConfigValue<List<Integer>> position) {}

    private static List<PanelCase> cases;
    private static final List<Rect2i> defaults = new ArrayList<>();
    private static TestScreen screen;
    private static MEStorageMenu menu;
    private static AbstractContainerMenu previousMenu;
    private static Screen previousScreen;
    private static List<List<Integer>> previousPositions;
    private static List<ItemStack> contents;
    private static int previousScale, phase, phaseTicks, index;
    private static boolean previousAnimation, previousSnap, cycle, initialized, complete;
    private static long animationStarted;
    private static Rect2i moved;
    private static CompletableFuture<List<ItemStack>> accessoriesSync;

    private WorkstationPanelSmoke() {}

    public static boolean tick() {
        if (complete) return true;
        var client = Minecraft.getInstance();
        try {
            if (!initialized) begin();
            phaseTicks++;
            switch (phase) {
                case 0 -> {
                    if (phaseTicks < 12) return false;
                    verifySlots(index);
                    defaults.set(index, panel(index).getBounds());
                    if (index == 0) verifyStonecutting();
                    screenshot(cycle ? cases.get(index).name() + "-default" : index == 0 ? "all-default" : cases.get(index).name() + "-before-drag");
                    startDrag();
                    next(1);
                }
                case 1 -> {
                    // Catch-up ticks are not elapsed animation time.
                    if (System.nanoTime() - animationStarted < 250_000_000L) return false;
                    var panel = panel(index);
                    panel.updateBeforeRender();
                    require(!panel.isMoving() && same(moved, panel.getBounds()), "Animated drag did not arrive: " + cases.get(index).name());
                    require(cases.get(index).position().get().equals(List.of(moved.getX(), moved.getY())),
                            "Outside release did not persist " + cases.get(index).name());
                    verifySlots(index);
                    unchanged();
                    screenshot(cases.get(index).name() + "-dragged");
                    if (++index < cases.size()) {
                        if (cycle) activate(index);
                        next(0);
                    } else {
                        if (cycle) activate(1);
                        next(2);
                    }
                }
                case 2 -> {
                    if (phaseTicks < 12) return false;
                    contents = snapshot();
                    var dialog = openRename();
                    var field = nameField(dialog);
                    require(field.isFocused(), "Anvil name field did not receive initial focus");
                    field.setValue("");
                    String name = "ATM Smoke Rename";
                    for (char character : name.toCharArray()) {
                        require(dialog.charTyped(character, 0), "Anvil field did not consume typed character");
                    }
                    require(field.getValue().equals(name), "Anvil name field lost typed text");
                    next(3);
                }
                case 3 -> {
                    if (phaseTicks < 12) return false;
                    screenshot("anvil-rename");
                    require(client.screen instanceof AESubScreen<?, ?>, "Anvil dialog closed before Escape");
                    client.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    returnedFromRename();
                    next(4);
                }
                case 4 -> {
                    if (phaseTicks < 12) return false;
                    var dialog = openRename();
                    require(nameField(dialog).isFocused(), "Reopened anvil field did not receive focus");
                    nameField(dialog).setValue("Cancelled Smoke Rename");
                    next(5);
                }
                case 5 -> {
                    if (phaseTicks < 12) return false;
                    screenshot("anvil-cancel");
                    var cancel = client.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                            .filter(button -> button.getMessage().getString().equals(CommonComponents.GUI_CANCEL.getString()))
                            .findFirst().orElseThrow();
                    client.screen.mouseClicked(cancel.getX() + cancel.getWidth() / 2.0,
                            cancel.getY() + cancel.getHeight() / 2.0, 0);
                    returnedFromRename();
                    next(6);
                }
                case 6 -> {
                    if (phaseTicks < 12) return false;
                    require(cases.stream().allMatch(value -> !value.position().get().isEmpty()),
                            "A workstation drag did not populate its saved config");
                    var tab = ConfigManager.INSTANCE.getVisibleTabs(menu).stream()
                            .filter(value -> value.id().toString().equals("myotus:config")).findFirst().orElseThrow();
                    var settings = new CustomConfigTabScreen<>(screen, tab);
                    client.setScreen(settings);
                    String label = Component.translatable("gui.myotus.terminal_module.reset_positions").getString();
                    settings.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                            .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow().onPress();
                    require(cases.stream().allMatch(value -> value.position().get().isEmpty()),
                            "Position Reset missed a new workstation config registration");
                    client.setScreen(screen);
                    index = 0;
                    if (cycle) activate(index);
                    next(7);
                }
                case 7 -> {
                    if (phaseTicks < 12) return false;
                    if (cycle) {
                        verifyReset(index);
                        screenshot(cases.get(index).name() + "-reset");
                        if (++index < cases.size()) {
                            activate(index);
                            next(7);
                            return false;
                        }
                    } else {
                        for (int i = 0; i < cases.size(); i++) verifyReset(i);
                        screenshot("all-reset");
                    }
                    unchanged();
                    LogUtils.getLogger().info("ATM WORKSTATION PANEL SMOKE PASS: five native-slot layouts, recipe previews separate from slots, shared drag animation and outside release, anvil focused text/escape/cancel, registered Position Reset; client fixtures only, no server item movement");
                    if (ModList.get().isLoaded("accessories")) {
                        accessoriesSync = AccessoriesSmoke.sync();
                        next(8);
                        return false;
                    }
                    restore();
                    complete = true;
                    return true;
                }
                case 8 -> {
                    require(phaseTicks < 200, "Accessories pre-open sync did not complete");
                    if (phaseTicks < 12 || !accessoriesSync.isDone()) return false;
                    accessoriesSync.join();
                    menu = new MEStorageMenu(MEStorageMenu.TYPE, 73, client.player.getInventory(),
                            new TestTerminals.TestHost(client.player));
                    client.player.containerMenu = menu;
                    var cards = menu.getSlots(MyoSlotSemantics.MYO_UPGRADE_SLOT);
                    cards.getFirst().set(ModItems.ACCESSORIES_MODULE.toStack());
                    if (ModItems.CURIOS_MODULE.get().isAvailable() && cards.size() > 1) {
                        cards.get(1).set(ModItems.CURIOS_MODULE.toStack());
                    }
                    openScreen();
                    contents = snapshot();
                    next(9);
                }
                case 9 -> {
                    if (phaseTicks < 12) return false;
                    var slots = menu.getSlots(ModuleSemantics.ACCESSORIES);
                    var expected = accessoriesSync.join();
                    require(slots.size() == expected.size(), "Accessories panel count differs from native functional inventory");
                    for (int i = 0; i < slots.size(); i++) {
                        require(slots.get(i).isActive() && ItemStack.matches(slots.get(i).getItem(), expected.get(i)),
                                "Accessories native slot differs from synchronized server contents at " + i);
                    }
                    boolean curiosInstalled = ModItems.CURIOS_MODULE.get().isAvailable()
                            && menu.getSlots(MyoSlotSemantics.MYO_UPGRADE_SLOT).size() > 1;
                    var panels = screen.composites().values().stream().filter(TerminalEquipmentPanel.class::isInstance)
                            .map(TerminalEquipmentPanel.class::cast).toList();
                    require(panels.size() == (curiosInstalled ? 2 : 1), "An installed optional card did not create its panel");
                    long emptyCount = (slots.isEmpty() ? 1 : 0)
                            + (curiosInstalled && menu.getSlots(ModuleSemantics.CURIOS).isEmpty() ? 1 : 0);
                    require(panels.stream().filter(panel -> menu.slots.stream().noneMatch(panel::ownsSlot)).count() == emptyCount,
                            "Empty optional inventories created phantom native slots");
                    String accessoriesUi = verifyOptionalPanel(ModuleSemantics.ACCESSORIES);
                    String curiosUi = curiosInstalled ? verifyOptionalPanel(ModuleSemantics.CURIOS) : "not installed";
                    if (ModList.get().isLoaded("accessories_compat_layer")) {
                        require(menu.getSlots(ModuleSemantics.CURIOS).isEmpty(), "Bridge exposed duplicate Curios inventory slots");
                    }
                    unchanged();
                    screenshot("accessories-optional");
                    LogUtils.getLogger().info("ATM ACCESSORIES PANEL SMOKE PASS: pre-open sync requested, {} functional slots; Accessories UI={}; Curios UI={}; no server item movement",
                            slots.size(), accessoriesUi, curiosUi);
                    restore();
                    complete = true;
                    return true;
                }
                default -> throw new IllegalStateException("Unknown workstation smoke phase");
            }
            return false;
        } catch (RuntimeException | Error error) {
            if (initialized) restore();
            throw error;
        }
    }

    private static void begin() {
        var client = Minecraft.getInstance();
        cases = List.of(
                new PanelCase("stonecutting", ModItems.STONECUTTING_MODULE.get(), ModuleSemantics.STONECUTTING, 2, ModulePanelPositions.STONECUTTING),
                new PanelCase("anvil", ModItems.ANVIL_MODULE.get(), ModuleSemantics.ANVIL, 3, ModulePanelPositions.ANVIL),
                new PanelCase("charging", ModItems.CHARGING_MODULE.get(), ModuleSemantics.CHARGING, 1, ModulePanelPositions.CHARGING),
                new PanelCase("smithing", ModItems.SMITHING_MODULE.get(), ModuleSemantics.SMITHING, 4, ModulePanelPositions.SMITHING),
                new PanelCase("grindstone", ModItems.GRINDSTONE_MODULE.get(), ModuleSemantics.GRINDSTONE, 3, ModulePanelPositions.GRINDSTONE));
        previousMenu = client.player.containerMenu;
        previousScreen = client.screen;
        previousScale = client.options.guiScale().get();
        previousAnimation = MyotusConfig.CLIENT.animatePanelMovement.get();
        previousSnap = MyotusConfig.CLIENT.snapPanelMovement.get();
        previousPositions = settings().stream().map(value -> List.copyOf(value.get())).toList();
        initialized = true;
        settings().forEach(value -> value.set(List.of()));
        MyotusConfig.CLIENT.animatePanelMovement.set(true);
        MyotusConfig.CLIENT.snapPanelMovement.set(false);
        client.options.guiScale().set(1);
        client.resizeDisplay();
        menu = new MEStorageMenu(MEStorageMenu.TYPE, 72, client.player.getInventory(), new TestTerminals.TestHost(client.player));
        menu.addSlotListener(new ContainerListener() {
            @Override public void slotChanged(AbstractContainerMenu container, int slot, ItemStack stack) {}
            @Override public void dataChanged(AbstractContainerMenu container, int id, int value) {}
        });
        client.player.containerMenu = menu;
        var cards = menu.getSlots(MyoSlotSemantics.MYO_UPGRADE_SLOT);
        require(!cards.isEmpty(), "No Myotus module card slots");
        cycle = cards.size() < cases.size();
        for (int i = 0; i < cases.size(); i++) {
            require(menu.getSlots(cases.get(i).semantic()).size() == cases.get(i).slotCount(),
                    "Missing permanent workstation slots: " + cases.get(i).name());
            defaults.add(null);
            if (!cycle) cards.get(i).set(new ItemStack(cases.get(i).card()));
        }
        menu.getSlots(ModuleSemantics.STONECUTTING).getFirst().set(new ItemStack(Items.STONE));
        var recipes = WorkstationModuleProvider.recipes(menu);
        require(!recipes.isEmpty(), "Client stonecutting recipes for stone are missing");
        var preview = recipes.getFirst();
        WorkstationModuleProvider.applyClientState(menu, preview.id(), 3);
        menu.getSlots(ModuleSemantics.STONECUTTING).getLast().set(preview.value().getResultItem(client.player.registryAccess()).copy());
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(40);
        menu.getSlots(ModuleSemantics.ANVIL).getFirst().set(sword);
        menu.getSlots(ModuleSemantics.ANVIL).get(1).set(new ItemStack(Items.IRON_INGOT));
        var renamed = new ItemStack(Items.IRON_SWORD);
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Preview only"));
        menu.getSlots(ModuleSemantics.ANVIL).getLast().set(renamed);
        menu.getSlots(ModuleSemantics.CHARGING).getFirst().set(AEItems.WIRELESS_TERMINAL.stack());
        var smithing = menu.getSlots(ModuleSemantics.SMITHING);
        smithing.get(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        smithing.get(1).set(new ItemStack(Items.DIAMOND_SWORD));
        smithing.get(2).set(new ItemStack(Items.NETHERITE_INGOT));
        smithing.get(3).set(new ItemStack(Items.NETHERITE_SWORD));
        menu.getSlots(ModuleSemantics.GRINDSTONE).getFirst().set(new ItemStack(Items.IRON_SWORD));
        menu.getSlots(ModuleSemantics.GRINDSTONE).getLast().set(new ItemStack(Items.IRON_SWORD));
        if (cycle) activate(0);
        else openScreen();
    }

    private static void activate(int i) {
        var cards = menu.getSlots(MyoSlotSemantics.MYO_UPGRADE_SLOT);
        cards.forEach(slot -> slot.set(ItemStack.EMPTY));
        cards.getFirst().set(new ItemStack(cases.get(i).card()));
        openScreen();
        contents = snapshot();
    }

    private static void openScreen() {
        screen = new TestScreen(menu);
        Minecraft.getInstance().setScreen(screen);
    }

    private static TerminalEquipmentPanel panel(int i) {
        var first = menu.getSlots(cases.get(i).semantic()).getFirst();
        return screen.composites().values().stream()
                .filter(value -> value instanceof WorkstationModulePanel || value instanceof ChargingModulePanel)
                .map(TerminalEquipmentPanel.class::cast).filter(value -> value.ownsSlot(first)).findFirst().orElseThrow();
    }

    private static void verifySlots(int i) {
        var panel = panel(i);
        panel.updateBeforeRender();
        var bounds = panel.getBounds();
        require(panel.isVisible() && bounds.getX() > -1000, "Workstation panel not placed: " + cases.get(i).name());
        for (var slot : menu.getSlots(cases.get(i).semantic())) {
            require(slot.isActive() && new Point(slot.x, slot.y).isIn(bounds)
                    && new Point(slot.x + 15, slot.y + 15).isIn(bounds), "Native slot outside " + cases.get(i).name());
            require(!panel.onMouseDown(new Point(slot.x + 2, slot.y + 2), 0),
                    "Stationary native slot click consumed by " + cases.get(i).name());
        }
    }

    private static void verifyStonecutting() {
        var bounds = panel(0).getBounds();
        var point = new Point(bounds.getX() + 8, bounds.getY() + 48);
        require(WorkstationModuleProvider.selectedRecipe(menu).equals(WorkstationModuleProvider.recipes(menu).getFirst().id()),
                "Client selected recipe preview missing");
        require(screen.slotAt(point) == null && menu.getSlots(ModuleSemantics.STONECUTTING).size() == 2,
                "Recipe preview cell became a native menu slot");
        require(panel(0).getTooltip(point.getX(), point.getY()) != null, "Recipe preview tooltip missing");
        require(menu.getCarried().isEmpty(), "Recipe preview changed cursor contents without input");
    }

    private static void startDrag() {
        var panel = panel(index);
        var start = panel.getBounds();
        int dx = screen.getGuiLeft() + start.getX() >= 60 ? -60 : 0;
        int dy = dx != 0 ? 0 : screen.getGuiTop() + start.getY() >= 60 ? -60 : 60;
        require(screen.getGuiTop() + start.getY() + dy >= 0
                && screen.getGuiTop() + start.getY() + dy + start.getHeight() <= screen.height,
                "No bounded drag destination for " + cases.get(index).name());
        moved = new Rect2i(start.getX() + dx, start.getY() + dy, start.getWidth(), start.getHeight());
        contents = snapshot();
        int mx = screen.getGuiLeft() + start.getX() + 8, my = screen.getGuiTop() + start.getY() + 8;
        require(screen.mouseClicked(mx, my, 0), "Title did not start native screen drag");
        require(screen.mouseDragged(mx + dx, my + dy, 0, dx, dy), "Native screen lost drag capture");
        animationStarted = System.nanoTime();
        require(panel.isMoving() && !same(moved, panel.getBounds()),
                "Workstation drag did not animate: " + cases.get(index).name()
                        + ", start=" + start.getX() + "," + start.getY()
                        + ", target=" + moved.getX() + "," + moved.getY()
                        + ", actual=" + panel.getBounds().getX() + "," + panel.getBounds().getY()
                        + ", moving=" + panel.isMoving() + ", animation=" + MyotusConfig.CLIENT.animatePanelMovement.get());
        var slot = menu.getSlots(cases.get(index).semantic()).getFirst();
        require(panel.onMouseDown(new Point(slot.x + 2, slot.y + 2), 0), "Moving workstation slot accepted input");
        screen.mouseReleased(-20, -20, 0);
    }

    private static AESubScreen<?, ?> openRename() {
        var bounds = panel(1).getBounds();
        screen.mouseClicked(screen.getGuiLeft() + bounds.getX() + 60,
                screen.getGuiTop() + bounds.getY() + 52, 0);
        require(Minecraft.getInstance().screen instanceof AESubScreen<?, ?>,
                "Anvil name button did not open an AE2 sub-screen");
        var dialog = (AESubScreen<?, ?>) Minecraft.getInstance().screen;
        require(dialog.getParent() == screen && dialog.getMenu() == menu
                && Minecraft.getInstance().player.containerMenu == menu, "Rename opened a different or closed menu");
        return dialog;
    }

    private static EditBox nameField(AESubScreen<?, ?> dialog) {
        return dialog.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    }

    private static void returnedFromRename() {
        require(Minecraft.getInstance().screen == screen && Minecraft.getInstance().player.containerMenu == menu,
                "Anvil cancel/Escape closed the menu instead of returning to its parent");
        unchanged();
    }

    private static void verifyReset(int i) {
        verifySlots(i);
        require(same(defaults.get(i), panel(i).getBounds()), "Position Reset did not restore " + cases.get(i).name());
    }

    private static String verifyOptionalPanel(SlotSemantic semantic) {
        var slots = menu.getSlots(semantic);
        var panels = screen.composites().values().stream().filter(TerminalEquipmentPanel.class::isInstance)
                .map(TerminalEquipmentPanel.class::cast).toList();
        if (slots.isEmpty()) {
            // With both inventories empty, neither panel has a semantic-identifying slot. Check
            // every empty panel; the caller checks their count against the installed cards.
            for (var panel : panels) {
                if (menu.slots.stream().anyMatch(panel::ownsSlot)) continue;
                panel.updateBeforeRender();
                var bounds = panel.getBounds();
                var point = new Point(bounds.getX() + bounds.getWidth() / 2, bounds.getY() + bounds.getHeight() / 2);
                require(panel.isVisible() && panel.getTooltip(point.getX(), point.getY()) != null
                        && screen.slotAt(point) == null, "Empty optional panel lacks its no-slots state or exposes a phantom slot");
            }
            return "empty panel/no-slots tooltip (slot rendering not tested)";
        }
        var first = slots.getFirst();
        var panel = panels.stream().filter(value -> value.ownsSlot(first)).findFirst().orElseThrow();
        panel.updateBeforeRender();
        require(panel.isVisible() && new Point(first.x, first.y).isIn(panel.getBounds()),
                "Optional native panel is not visible");
        var point = new Point(first.x + 2, first.y + 2);
        require(!panel.onMouseDown(point, 0) && screen.slotAt(point) == first,
                "Optional panel stole the native slot click");
        return "visible native slots/click pass";
    }

    /** Optional classes are resolved only after the Accessories loaded guard. */
    private static final class AccessoriesSmoke {
        private static CompletableFuture<List<ItemStack>> sync() {
            var client = Minecraft.getInstance();
            var server = client.getSingleplayerServer();
            require(server != null, "Accessories smoke requires the integrated server");
            var playerId = client.player.getUUID();
            return server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                require(player != null, "Accessories smoke server player is missing");
                new me.myogoo.appliedterminalmodules.compat.accessories.AccessoriesModuleProvider().beforeMenuOpen(player);
                var capability = io.wispforest.accessories.api.AccessoriesCapability.get(player);
                if (capability == null) return List.<ItemStack>of();
                var containers = capability.getContainers();
                List<ItemStack> items = new ArrayList<>();
                io.wispforest.accessories.data.EntitySlotLoader.getEntitySlots(player).values().stream()
                        .sorted(java.util.Comparator.comparingInt(io.wispforest.accessories.api.slot.SlotType::order)
                                .thenComparing(io.wispforest.accessories.api.slot.SlotType::name))
                        .forEach(type -> {
                            var container = containers.get(type.name());
                            if (container == null) return;
                            var inventory = container.getAccessories();
                            for (int i = 0; i < inventory.getContainerSize(); i++) items.add(inventory.getItem(i).copy());
                        });
                return List.copyOf(items);
            });
        }
    }

    private static List<ItemStack> snapshot() { return menu.slots.stream().map(slot -> slot.getItem().copy()).toList(); }

    private static void unchanged() {
        require(menu.slots.size() == contents.size(), "UI changed permanent packet slot count");
        for (int i = 0; i < contents.size(); i++) {
            require(ItemStack.matches(contents.get(i), menu.slots.get(i).getItem()), "UI mutated fixture contents at slot " + i);
        }
        require(menu.getCarried().isEmpty(), "UI placed fixture contents on cursor");
    }

    private static List<ModConfigSpec.ConfigValue<List<Integer>>> settings() {
        return List.of(ModulePanelPositions.EQUIPMENT, ModulePanelPositions.CRAFTING, ModulePanelPositions.CURIOS,
                ModulePanelPositions.ACCESSORIES, ModulePanelPositions.STONECUTTING, ModulePanelPositions.ANVIL,
                ModulePanelPositions.CHARGING, ModulePanelPositions.SMITHING, ModulePanelPositions.GRINDSTONE);
    }

    private static void restore() {
        for (int i = 0; i < settings().size(); i++) settings().get(i).set(previousPositions.get(i));
        ModulePanelPositions.SPEC.save();
        MyotusConfig.CLIENT.animatePanelMovement.set(previousAnimation);
        MyotusConfig.CLIENT.snapPanelMovement.set(previousSnap);
        var client = Minecraft.getInstance();
        client.player.containerMenu = previousMenu;
        client.setScreen(previousScreen);
        client.options.guiScale().set(previousScale);
        client.resizeDisplay();
        initialized = false;
    }

    private static void next(int value) { phase = value; phaseTicks = 0; }
    private static boolean same(Rect2i a, Rect2i b) { return a.getX() == b.getX() && a.getY() == b.getY(); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private static void screenshot(String name) {
        var client = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            Path path = Path.of("../build/reports/client-smoke",
                    System.getProperty("appliedterminalmodules.recipeViewer", "none"), "workstations", name + ".png");
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
        } catch (Exception error) { throw new IllegalStateException("Cannot save workstation smoke screenshot", error); }
    }

    private static final class TestScreen extends MEStorageScreen<MEStorageMenu> {
        private TestScreen(MEStorageMenu menu) {
            super(menu, Minecraft.getInstance().player.getInventory(), Component.literal("Workstation verification"),
                    StyleManager.loadStyleDoc("/screens/terminals/terminal.json"));
        }

        private Map<String, ICompositeWidget> composites() {
            return ((WidgetContainerAccessor) widgets).myotus$getCompositeWidgets();
        }

        private Slot slotAt(Point point) {
            return menu.slots.stream().filter(Slot::isActive)
                    .filter(slot -> isHovering(slot, getGuiLeft() + point.getX(), getGuiTop() + point.getY()))
                    .findFirst().orElse(null);
        }
    }
}
