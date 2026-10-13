package me.myogoo.appliedterminalmodules.menu;

import appeng.menu.SlotSemantic;
import me.myogoo.appliedterminalmodules.init.ModItems;
import net.minecraft.world.item.Item;

public enum WorkstationKind {
    STONECUTTING, ANVIL, SMITHING, GRINDSTONE;

    public Item card() {
        return switch (this) {
            case STONECUTTING -> ModItems.STONECUTTING_MODULE.get();
            case ANVIL -> ModItems.ANVIL_MODULE.get();
            case SMITHING -> ModItems.SMITHING_MODULE.get();
            case GRINDSTONE -> ModItems.GRINDSTONE_MODULE.get();
        };
    }

    public SlotSemantic semantic() {
        return switch (this) {
            case STONECUTTING -> ModuleSemantics.STONECUTTING;
            case ANVIL -> ModuleSemantics.ANVIL;
            case SMITHING -> ModuleSemantics.SMITHING;
            case GRINDSTONE -> ModuleSemantics.GRINDSTONE;
        };
    }

    public int inputCount() {
        return switch (this) {
            case STONECUTTING -> 1;
            case ANVIL, GRINDSTONE -> 2;
            case SMITHING -> 3;
        };
    }
}
