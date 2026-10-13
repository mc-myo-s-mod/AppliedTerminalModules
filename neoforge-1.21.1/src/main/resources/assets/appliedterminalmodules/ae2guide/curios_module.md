---
navigation:
  parent: index.md
  title: Curios Module
  icon: appliedterminalmodules:curios_module
  position: 30
categories:
  - applied terminal modules
item_ids:
  - appliedterminalmodules:curios_module
---

# Curios Module

<ItemImage id="appliedterminalmodules:curios_module" scale={3} />

This module requires **Curios**. When `accessories_compat_layer` is installed, Accessories provides the Curios-compatible inventory; the Curios card and recipe are hidden to prevent a duplicate panel. Use the [Accessories Module](accessories_module.md) for those slots. Without the bridge, Curios uses its own native slots and module. Native Curios and native Accessories slots are both supported separately when both mods are installed without the bridge.

## Access your accessories

When available, install the card in a [Myotus terminal card slot](index.md) to access your player's visible Curios equipment slots from the terminal. The available slot types and their number come from your Curios setup and the other mods in your pack; the module does not add accessory slots.

Use the slots to equip or remove accessories. Curios decides which items each slot accepts and whether an equipped item may be removed.

The panel shows up to **five slots at a time**. With more slots, scroll the mouse wheel over the panel or use its scrollbar to reach the rest. If there are no visible Curios slots, the panel has no equipment slots to show.

Removing the module card or closing the terminal leaves your accessories equipped in Curios. To unequip an accessory, take it out of its equipment slot while the module is installed.

## Recipe

With Curios installed and `accessories_compat_layer` absent, the shapeless recipe uses an AE2 Advanced Card, an Ender Pearl and an AE2 Logic Processor.

<Recipe id="appliedterminalmodules:curios_module" fallbackText="This recipe is available only when Curios is installed, the Accessories Curios bridge is absent, and the recipe is enabled in this pack." />
