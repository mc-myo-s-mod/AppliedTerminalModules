package me.myogoo.appliedterminalmodules.item;

import java.util.List;

import me.myogoo.myotus.api.ITerminalModuleCard;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;

public class ModuleItem extends Item implements ITerminalModuleCard {
    private final String requiredMod;

    public ModuleItem() { this(null); }

    public ModuleItem(String requiredMod) {
        super(new Properties().stacksTo(1));
        this.requiredMod = requiredMod;
    }

    @Override
    public boolean isAvailable() {
        return (requiredMod == null || ModList.get().isLoaded(requiredMod)) && !usesAccessoriesBridge();
    }

    private boolean usesAccessoriesBridge() {
        return "curios".equals(requiredMod) && ModList.get().isLoaded("accessories_compat_layer");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.appliedterminalmodules.install").withStyle(ChatFormatting.DARK_GRAY));
        if (!isAvailable()) {
            tooltip.add(Component.translatable(usesAccessoriesBridge() ? "tooltip.appliedterminalmodules.curios_bridge"
                    : "tooltip.appliedterminalmodules.requires_" + requiredMod)
                    .withStyle(ChatFormatting.RED));
        }
    }
}
