---
navigation:
  parent: index.md
  title: Accessories Module
  icon: appliedterminalmodules:accessories_module
  position: 90
categories:
  - applied terminal modules
item_ids:
  - appliedterminalmodules:accessories_module
---

# Accessories Module

<ItemImage id="appliedterminalmodules:accessories_module" scale={3} />

This module requires **Accessories**. When `accessories_compat_layer` is installed, the module also exposes the Curios-compatible inventory provided by Accessories. In that setup the Curios card is hidden, so use this single module for those slots. Without the bridge, this module accesses Accessories' native slots, while the Curios Module accesses Curios' native slots; both can be used when both mods are installed.

## Access your accessories

Install the card in a [Myotus terminal card slot](index.md) to manage the accessory slots supplied by Accessories and other installed mods. The module uses slots provided by Accessories, including its Curios-compatible inventory when the bridge is enabled; it does not add accessory slots.

Accessories determines which items each slot accepts and whether an equipped item may be removed. Removing the card or closing the terminal leaves equipped items in their slots.

## Recipe

With Accessories installed, the shapeless recipe uses an AE2 Advanced Card, an Ender Pearl and an AE2 Calculation Processor.

<Recipe id="appliedterminalmodules:accessories_module" fallbackText="This recipe is available only when Accessories is installed and the recipe is enabled in this pack." />
