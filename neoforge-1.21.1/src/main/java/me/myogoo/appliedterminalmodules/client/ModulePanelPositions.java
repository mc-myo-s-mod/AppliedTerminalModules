package me.myogoo.appliedterminalmodules.client;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Dialog-relative positions; an empty list uses automatic placement. */
public final class ModulePanelPositions {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<List<Integer>> EQUIPMENT;
    public static final ModConfigSpec.ConfigValue<List<Integer>> CRAFTING;
    public static final ModConfigSpec.ConfigValue<List<Integer>> CURIOS;
    public static final ModConfigSpec.ConfigValue<List<Integer>> ACCESSORIES;
    public static final ModConfigSpec.ConfigValue<List<Integer>> STONECUTTING;
    public static final ModConfigSpec.ConfigValue<List<Integer>> ANVIL;
    public static final ModConfigSpec.ConfigValue<List<Integer>> CHARGING;
    public static final ModConfigSpec.ConfigValue<List<Integer>> SMITHING;
    public static final ModConfigSpec.ConfigValue<List<Integer>> GRINDSTONE;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("terminalModules");
        EQUIPMENT = position(builder, "equipmentPanelPosition");
        CRAFTING = position(builder, "craftingPanelPosition");
        CURIOS = position(builder, "curiosPanelPosition");
        ACCESSORIES = position(builder, "accessoriesPanelPosition");
        STONECUTTING = position(builder, "stonecuttingPanelPosition");
        ANVIL = position(builder, "anvilPanelPosition");
        CHARGING = position(builder, "chargingPanelPosition");
        SMITHING = position(builder, "smithingPanelPosition");
        GRINDSTONE = position(builder, "grindstonePanelPosition");
        builder.pop();
        SPEC = builder.build();
    }

    private static ModConfigSpec.ConfigValue<List<Integer>> position(ModConfigSpec.Builder builder, String name) {
        return builder.comment("Panel [x, y] relative to the terminal. Empty uses automatic placement.")
                .define(name, List.<Integer>of(), value -> value instanceof List<?> position
                        && (position.isEmpty() || position.size() == 2
                        && position.stream().allMatch(Integer.class::isInstance)));
    }

    private ModulePanelPositions() {}
}
