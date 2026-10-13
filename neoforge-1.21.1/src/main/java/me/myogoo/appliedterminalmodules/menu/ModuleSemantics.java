package me.myogoo.appliedterminalmodules.menu;

import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;

public final class ModuleSemantics {
    public static final SlotSemantic EQUIPMENT = SlotSemantics.register("appliedterminalmodules_equipment", false);
    public static final SlotSemantic CRAFTING = SlotSemantics.register("appliedterminalmodules_crafting", false);
    public static final SlotSemantic CURIOS = SlotSemantics.register("appliedterminalmodules_curios", false);
    public static final SlotSemantic ACCESSORIES = SlotSemantics.register("appliedterminalmodules_accessories", false);
    public static final SlotSemantic STONECUTTING = SlotSemantics.register("appliedterminalmodules_stonecutting", false);
    public static final SlotSemantic ANVIL = SlotSemantics.register("appliedterminalmodules_anvil", false);
    public static final SlotSemantic CHARGING = SlotSemantics.register("appliedterminalmodules_charging", false);
    public static final SlotSemantic SMITHING = SlotSemantics.register("appliedterminalmodules_smithing", false);
    public static final SlotSemantic GRINDSTONE = SlotSemantics.register("appliedterminalmodules_grindstone", false);

    private ModuleSemantics() {}
}
