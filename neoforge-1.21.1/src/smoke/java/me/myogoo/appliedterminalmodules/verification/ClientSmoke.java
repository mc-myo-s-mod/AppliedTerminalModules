package me.myogoo.appliedterminalmodules.verification;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.mojang.logging.LogUtils;
import guideme.Guides;
import guideme.PageAnchor;
import guideme.color.SymbolicColor;
import guideme.document.block.LytVisitor;
import guideme.document.flow.LytFlowContent;
import guideme.indices.ItemIndex;
import guideme.indices.CategoryIndex;
import guideme.internal.screen.GuideScreen;
import me.myogoo.appliedterminalmodules.AppliedTerminalModules;
import me.myogoo.appliedterminalmodules.gametest.ViewerSmoke;
import me.myogoo.appliedterminalmodules.gametest.PanelSmoke;
import me.myogoo.appliedterminalmodules.gametest.WorkstationPanelSmoke;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Development source set only; opt in with -Pclient_smoke=true. Never included in normal builds. */
@EventBusSubscriber(modid = AppliedTerminalModules.MOD_ID, value = Dist.CLIENT)
public final class ClientSmoke {
    private static final String[] LANGUAGES = {"en_us", "ko_kr"};
    private static final String[] PAGES = {"index", "equipment_module", "crafting_module", "curios_module",
            "stonecutting_module", "anvil_module", "charging_module", "smithing_module", "grindstone_module",
            "accessories_module"};
    private static boolean started;
    private static boolean viewerChecked;
    private static boolean panelsChecked;
    private static boolean workstationsChecked;
    private static int ticks, languageIndex, pageIndex, pageTicks;
    private static CompletableFuture<Void> reload;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("appliedterminalmodules.clientSmoke")) return;
        var client = Minecraft.getInstance();
        if (++ticks > 3600) throw new IllegalStateException("ATM client smoke timed out");
        if (!started) {
            if (!(client.screen instanceof TitleScreen) || client.getOverlay() != null) return;
            started = true;
            client.options.pauseOnLostFocus = false;
            client.createWorldOpenFlows().openWorld("ATM-Smoke", () -> {
                throw new IllegalStateException("Cannot open isolated ATM-Smoke world");
            });
            return;
        }
        if (client.level == null || client.player == null || client.getOverlay() != null) return;
        if (!viewerChecked) {
            viewerChecked = ViewerSmoke.check();
            if (!viewerChecked) {
                if (ticks % 100 == 0) LogUtils.getLogger().info("ATM smoke waiting for JEI runtime");
                return;
            }
        }
        if (!panelsChecked) {
            panelsChecked = PanelSmoke.tick();
            if (!panelsChecked) return;
        }
        if (!workstationsChecked) {
            workstationsChecked = WorkstationPanelSmoke.tick();
            if (!workstationsChecked) return;
        }
        if (reload == null && pageTicks == 0 && pageIndex == 0) {
            client.getLanguageManager().setSelected(LANGUAGES[languageIndex]);
            reload = client.reloadResourcePacks();
            pageTicks = -1;
            return;
        }
        if (reload != null) {
            if (!reload.isDone()) return;
            reload.join();
            reload = null;
            pageTicks = 0;
        }
        var guide = Guides.getById(ResourceLocation.fromNamespaceAndPath("ae2", "guide"));
        if (guide == null) throw new IllegalStateException("AE2 guide missing");
        var id = ResourceLocation.fromNamespaceAndPath(AppliedTerminalModules.MOD_ID, PAGES[pageIndex] + ".md");
        if (pageTicks++ == 0) {
            if (pageIndex == 0) {
                var expected = Arrays.stream(PAGES).skip(1)
                        .map(path -> ResourceLocation.fromNamespaceAndPath(AppliedTerminalModules.MOD_ID, path + ".md"))
                        .collect(Collectors.toSet());
                var category = guide.getNavigationTree().getNodeById(id);
                var indexed = guide.getIndex(CategoryIndex.class).get("applied terminal modules").stream()
                        .map(PageAnchor::pageId).collect(Collectors.toSet());
                if (category == null || !guide.getNavigationTree().getRootNodes().contains(category)
                        || !category.title().equals("Applied Terminal Modules")
                        || !category.children().stream().map(node -> node.pageId()).collect(Collectors.toSet()).equals(expected)
                        || !indexed.equals(expected)) {
                    throw new IllegalStateException("AE2 module category or child pages missing: " + indexed);
                }
                var text = guide.getPage(id).document().getTextContent();
                if (category.children().stream().anyMatch(node -> !text.contains(node.title()))) {
                    throw new IllegalStateException("AE2 module category links did not render");
                }
                LogUtils.getLogger().info("ATM AE2 CATEGORY PASS: {} / root category and nine indexed module links", LANGUAGES[languageIndex]);
            }
            if (pageIndex > 0) {
                var entry = guide.getIndex(ItemIndex.class).get(
                        ResourceLocation.fromNamespaceAndPath(AppliedTerminalModules.MOD_ID, PAGES[pageIndex]));
                if (entry == null || !entry.pageId().equals(id)) {
                    throw new IllegalStateException("Missing card guide index: " + id);
                }
            }
            var parsed = guide.getParsedPage(id);
            if (parsed == null || !parsed.getLanguage().equals(LANGUAGES[languageIndex])) {
                throw new IllegalStateException("Missing translated guide page: " + id);
            }
            var document = guide.getPage(id).document();
            if (document.getTextContent().contains("Failed to parse GuideME page")) {
                throw new IllegalStateException("Invalid guide Markdown: " + id);
            }
            document.visit(new LytVisitor() {
                @Override
                public Result beforeFlowContent(LytFlowContent content) {
                    if (content.getStyle().color() == SymbolicColor.ERROR_TEXT) {
                        throw new IllegalStateException("Guide compilation error: " + id + " / " + document.getTextContent());
                    }
                    return Result.CONTINUE;
                }
            });
            client.setScreen(GuideScreen.openNew(guide, PageAnchor.page(id)));
        }
        if (!(client.screen instanceof GuideScreen)) throw new IllegalStateException("Guide screen did not open");
        if (pageTicks < 12) return;
        try (var screenshot = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            var viewer = System.getProperty("appliedterminalmodules.recipeViewer", "none");
            Path path = Path.of("../build/reports/client-smoke", viewer,
                    LANGUAGES[languageIndex] + "-" + PAGES[pageIndex] + ".png");
            Files.createDirectories(path.getParent());
            screenshot.writeToFile(path);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot save guide screenshot", e);
        }
        LogUtils.getLogger().info("ATM GUIDE PAGE PASS: {} / {}", LANGUAGES[languageIndex], id);
        pageTicks = 0;
        if (++pageIndex < PAGES.length) return;
        pageIndex = 0;
        if (++languageIndex < LANGUAGES.length) return;
        LogUtils.getLogger().info("ATM CLIENT SMOKE PASS: twenty guide pages rendered with recipes and card item indices");
        client.stop();
    }
}
