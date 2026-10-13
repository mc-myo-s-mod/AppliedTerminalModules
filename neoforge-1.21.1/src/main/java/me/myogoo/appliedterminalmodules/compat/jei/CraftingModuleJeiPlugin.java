package me.myogoo.appliedterminalmodules.compat.jei;

import java.util.List;

import appeng.client.gui.AEBaseScreen;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.BasicCellChestMenu;
import appeng.menu.me.items.CraftingTermMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.me.items.WirelessCraftingTermMenu;
import me.myogoo.appliedterminalmodules.AppliedTerminalModules;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

/** Viewer-owned discovery keeps all JEI types out of common and dedicated-server initialization. */
@JeiPlugin
public final class CraftingModuleJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(AppliedTerminalModules.MOD_ID, "crafting_module");
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // JEI 19 looks up the exact class, not assignable superclasses. Universal fallback preserves
        // any category-specific handlers installed by AE2 or another addon.
        for (var menuClass : List.of(MEStorageMenu.class, BasicCellChestMenu.class, CraftingTermMenu.class,
                WirelessCraftingTermMenu.class, PatternEncodingTermMenu.class)) {
            registration.addUniversalRecipeTransferHandler(
                    new CraftingModuleJeiTransferHandler(menuClass, registration.getTransferHelper()));
        }
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // AE2 19.2.17 has no JEI plugin; bridge its existing screen/widget exclusions here.
        registration.addGenericGuiContainerHandler(AEBaseScreen.class, new IGuiContainerHandler<AEBaseScreen<?>>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(AEBaseScreen<?> screen) {
                return screen.getExclusionZones();
            }
        });
    }
}
