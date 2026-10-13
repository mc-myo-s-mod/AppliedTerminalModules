package me.myogoo.appliedterminalmodules.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import appeng.client.Point;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.StyleManager;
import appeng.menu.me.common.MEStorageMenu;
import com.mojang.logging.LogUtils;
import me.myogoo.appliedterminalmodules.client.CraftingModulePanel;
import me.myogoo.appliedterminalmodules.client.ModulePanelPositions;
import me.myogoo.appliedterminalmodules.init.ModItems;
import me.myogoo.appliedterminalmodules.menu.ModuleSemantics;
import me.myogoo.myotus.client.gui.config.CustomConfigTabScreen;
import me.myogoo.myotus.config.MyotusConfig;
import me.myogoo.myotus.impl.ConfigManager;
import me.myogoo.myotus.menu.MyoSlotSemantics;
import me.myogoo.myotus.mixin.ae2.WidgetContainerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Real AE2 screen input, config button, layout and texture smoke; excluded from release JARs. */
public final class PanelSmoke {
    private static TestScreen screen;
    private static MEStorageMenu menu;
    private static AbstractContainerMenu previousMenu;
    private static List<List<Integer>> previousPositions;
    private static List<Rect2i> defaults;
    private static Rect2i moved;
    private static int ticks;
    private static int previousScale;
    private static boolean previousAnimation, previousSnap;
    private static long animationStarted;

    public static boolean tick() {
        var client = Minecraft.getInstance();
        if (ticks++ == 0) {
            previousScale = client.options.guiScale().get();
            client.options.guiScale().set(1);
            client.resizeDisplay();
            previousMenu = client.player.containerMenu;
            previousAnimation = MyotusConfig.CLIENT.animatePanelMovement.get();
            previousSnap = MyotusConfig.CLIENT.snapPanelMovement.get();
            MyotusConfig.CLIENT.animatePanelMovement.set(true);
            MyotusConfig.CLIENT.snapPanelMovement.set(false);
            previousPositions = settings().stream().map(value -> List.copyOf(value.get())).toList();
            settings().forEach(value -> value.set(List.of()));
            menu = new MEStorageMenu(MEStorageMenu.TYPE, 71, client.player.getInventory(),
                    new TestTerminals.TestHost(client.player));
            menu.addSlotListener(new ContainerListener() {
                @Override public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) {}
                @Override public void dataChanged(AbstractContainerMenu menu, int id, int value) {}
            });
            client.player.containerMenu = menu;
            var cards = menu.getSlots(MyoSlotSemantics.MYO_UPGRADE_SLOT);
            cards.get(0).set(ModItems.EQUIPMENT_MODULE.toStack());
            cards.get(1).set(ModItems.CRAFTING_MODULE.toStack());
            cards.get(2).set(ModItems.CURIOS_MODULE.toStack());
            // Include the last grid cell and output in the visual regression capture.
            menu.getSlots(ModuleSemantics.CRAFTING).get(8).set(new ItemStack(Items.DIAMOND));
            menu.getSlots(ModuleSemantics.CRAFTING).get(9).set(new ItemStack(Items.GOLD_INGOT, 4));
            screen = new TestScreen(menu);
            client.setScreen(screen);
        }
        if (ticks == 12) {
            defaults = panels().stream().map(ICompositeWidget::getBounds).toList();
            var panel = crafting();
            var start = panel.getBounds();
            require(start.getX() > -1000, "Crafting panel not placed");
            screenshot("default");
            require(!panel.onMouseDown(new Point(start.getX() + 8, start.getY() + 25), 0),
                    "Crafting slot click was consumed by dragging");
            int mx = screen.getGuiLeft() + start.getX() + 8;
            int my = screen.getGuiTop() + start.getY() + 8;
            screen.mouseClicked(mx, my, 0);
            // Moving into the terminal must be rejected without losing capture.
            screen.mouseDragged(screen.getGuiLeft() + 8, screen.getGuiTop() + 8, 0, 0, 0);
            require(same(start, panel.getBounds()), "Drag overlapped terminal slots");
            moved = new Rect2i(start.getX(), start.getY() - 60, start.getWidth(), start.getHeight());
            screen.mouseDragged(mx, my - 60, 0, 0, -60);
            animationStarted = System.nanoTime();
            require(!same(moved, panel.getBounds()), "Crafting panel teleported instead of animating");
            var output = menu.getSlots(ModuleSemantics.CRAFTING).getLast();
            require(panel.onMouseDown(new Point(output.x + 1, output.y + 1), 0),
                    "Moving output slot allowed interaction");
            screen.mouseReleased(-20, -20, 0);
            require(ModulePanelPositions.CRAFTING.get().equals(List.of(moved.getX(), moved.getY())),
                    "Release outside panel did not save position");
        }
        if (ticks == 18) {
            // Client ticks can catch up in one frame; animation uses wall time rather than tick count.
            if (System.nanoTime() - animationStarted < 250_000_000L) {
                ticks--;
                return false;
            }
            crafting().updateBeforeRender();
            require(same(moved, crafting().getBounds()), "Crafting animation did not arrive: "
                    + crafting().getBounds().getX() + "," + crafting().getBounds().getY()
                    + " expected " + moved.getX() + "," + moved.getY());
            for (var slot : menu.getSlots(ModuleSemantics.CRAFTING)) {
                require(new Point(slot.x, slot.y).isIn(crafting().getBounds()), "Grid/output slot left panel");
                require(!crafting().onMouseDown(new Point(slot.x + 1, slot.y + 1), 0),
                        "Stationary crafting slot consumed input");
            }
            // Seed other panels away from their default; all must respond to the same reset button.
            ModulePanelPositions.EQUIPMENT.set(List.of(-120, 24));
            ModulePanelPositions.CURIOS.set(List.of(-160, 24));
            screen = new TestScreen(menu);
            client.setScreen(screen);
        }
        if (ticks == 24) {
            require(same(moved, crafting().getBounds()), "Reopening lost crafting position");
            screenshot("dragged");
            int width = screen.width;
            int height = screen.height;
            screen.resize(client, width / 2, height / 2);
            crafting().updateBeforeRender();
            require(ModulePanelPositions.CRAFTING.get().equals(List.of(moved.getX(), moved.getY())),
                    "Small viewport overwrote the saved position");
            screen.resize(client, width, height);
            crafting().updateBeforeRender();
            require(same(moved, crafting().getBounds()), "Restoring viewport lost saved placement");
            var tab = ConfigManager.INSTANCE.getVisibleTabs(menu).stream()
                    .filter(value -> value.id().toString().equals("myotus:config")).findFirst().orElseThrow();
            var settings = new CustomConfigTabScreen<>(screen, tab);
            client.setScreen(settings);
            var label = Component.translatable("gui.myotus.terminal_module.reset_positions").getString();
            var button = settings.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(value -> value.getMessage().getString().equals(label)).findFirst().orElseThrow();
            button.onPress();
            require(settings().stream().allMatch(value -> value.get().isEmpty()), "Position Reset missed addon config");
            client.setScreen(screen);
        }
        if (ticks == 36) {
            var reset = panels();
            for (int i = 0; i < reset.size(); i++) {
                require(same(defaults.get(i), reset.get(i).getBounds()), "Position Reset did not restore panel " + i);
            }
            screenshot("reset");
            MyotusConfig.CLIENT.animatePanelMovement.set(false);
            MyotusConfig.CLIENT.snapPanelMovement.set(true);
            var panel = crafting();
            var start = panel.getBounds();
            var peer = panels().stream().filter(value -> value != panel).findFirst().orElseThrow().getBounds();
            moved = new Rect2i(peer.getX() - start.getWidth(), peer.getY(), start.getWidth(), start.getHeight());
            screen.mouseClicked(screen.getGuiLeft() + start.getX() + 8, screen.getGuiTop() + start.getY() + 8, 0);
            screen.mouseDragged(screen.getGuiLeft() + moved.getX() - 3 + 8,
                    screen.getGuiTop() + moved.getY() + 8, 0, 0, 0);
            require(same(moved, panel.getBounds()), "Crafting panel did not share snap/animation settings");
            screen.mouseReleased(-20, -20, 0);
        }
        if (ticks < 42) return false;
        screenshot("snapped");
        for (int i = 0; i < settings().size(); i++) settings().get(i).set(previousPositions.get(i));
        ModulePanelPositions.SPEC.save();
        MyotusConfig.CLIENT.animatePanelMovement.set(previousAnimation);
        MyotusConfig.CLIENT.snapPanelMovement.set(previousSnap);
        client.player.containerMenu = previousMenu;
        client.setScreen(null);
        client.options.guiScale().set(previousScale);
        client.resizeDisplay();
        LogUtils.getLogger().info("ATM PANEL SMOKE PASS: native AE2 textures, ten slots, animated title drag, moving output input guard, blocked overlap, outside release, reopen, actual Position Reset, shared snap/animation settings");
        return true;
    }

    private static List<net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<List<Integer>>> settings() {
        return List.of(ModulePanelPositions.EQUIPMENT, ModulePanelPositions.CRAFTING, ModulePanelPositions.CURIOS);
    }

    private static List<ICompositeWidget> panels() {
        return screen.composites().entrySet().stream().filter(entry -> entry.getKey().startsWith("myotus_terminal_module_"))
                .sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
    }

    private static CraftingModulePanel crafting() {
        return panels().stream().filter(CraftingModulePanel.class::isInstance).map(CraftingModulePanel.class::cast)
                .findFirst().orElseThrow();
    }

    private static boolean same(Rect2i a, Rect2i b) { return a.getX() == b.getX() && a.getY() == b.getY(); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private static void screenshot(String name) {
        var client = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            Path path = Path.of("../build/reports/client-smoke/panels",
                    System.getProperty("appliedterminalmodules.recipeViewer", "none"), name + ".png");
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private static final class TestScreen extends MEStorageScreen<MEStorageMenu> {
        TestScreen(MEStorageMenu menu) {
            super(menu, Minecraft.getInstance().player.getInventory(), Component.literal("Panel verification"),
                    StyleManager.loadStyleDoc("/screens/terminals/terminal.json"));
        }
        Map<String, ICompositeWidget> composites() {
            return ((WidgetContainerAccessor) widgets).myotus$getCompositeWidgets();
        }
    }
}
