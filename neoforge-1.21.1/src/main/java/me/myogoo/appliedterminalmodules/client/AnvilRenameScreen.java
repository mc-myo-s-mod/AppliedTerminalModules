package me.myogoo.appliedterminalmodules.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.widgets.ConfirmableTextField;
import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.menu.ModuleSemantics;
import me.myogoo.appliedterminalmodules.network.WorkstationPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Uses AE2's sub-screen lifecycle so entering a name never closes the terminal menu. */
final class AnvilRenameScreen extends AESubScreen<MEStorageMenu, AEBaseScreen<MEStorageMenu>> {
    private final ConfirmableTextField name;

    private AnvilRenameScreen(AEBaseScreen<MEStorageMenu> parent) {
        super(parent, "/screens/appliedterminalmodules/anvil_rename.json");
        setTextContent("dialog_title", Component.translatable("gui.appliedterminalmodules.anvil.rename"));
        name = new ConfirmableTextField(style, Minecraft.getInstance().font, 0, 0, 0, 0);
        name.setMaxLength(50);
        name.setValue(menu.getSlots(ModuleSemantics.ANVIL).getFirst().getItem().getHoverName().getString());
        name.setOnConfirm(this::apply);
        widgets.add("name", name);
        widgets.add("apply", Button.builder(CommonComponents.GUI_DONE, button -> apply()).build());
        widgets.add("cancel", Button.builder(CommonComponents.GUI_CANCEL, button -> returnToParent()).build());
    }

    @SuppressWarnings("unchecked")
    static void open(AEBaseScreen<?> parent) {
        parent.switchToScreen(new AnvilRenameScreen((AEBaseScreen<MEStorageMenu>) parent));
    }

    @Override
    protected void init() {
        super.init();
        setInitialFocus(name);
        name.setHighlightPos(0);
    }

    private void apply() {
        PacketDistributor.sendToServer(new WorkstationPackets.Rename(menu.containerId, name.getValue()));
        returnToParent();
    }

    @Override public void onClose() { returnToParent(); }
}
