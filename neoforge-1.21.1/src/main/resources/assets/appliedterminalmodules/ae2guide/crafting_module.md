---
navigation:
  parent: index.md
  title: Crafting Module
  icon: appliedterminalmodules:crafting_module
  position: 20
categories:
  - applied terminal modules
item_ids:
  - appliedterminalmodules:crafting_module
---

# Crafting Module

<ItemImage id="appliedterminalmodules:crafting_module" scale={3} />

Install this card in a [Myotus terminal card slot](index.md) to add a **3×3 crafting grid and a result slot** alongside the terminal.

## Crafting

Place ingredients in the grid, then take the result to craft. Ordinary clicks and Shift-click on the result use the terminal's menu slots. The server checks the recipe and consumes the ingredients when the result is taken.

Results are taken as a complete recipe batch. Shift-click crafts one batch only if the entire result fits in your inventory. To drop a multi-item result, use Ctrl+Q; ordinary Q cannot split a batch.

## JEI and EMI recipe transfer

With a normal **shaped or shapeless crafting recipe that fits within 3×3** open in JEI or EMI, use its **+ / fill** recipe transfer button to move ingredients into the installed crafting module's grid. Special recipes and recipes larger than 3×3 are not supported. The server validates the transfer.

Use **Alt + the recipe transfer button** to target a module through Myotus, including terminals that already have their own crafting grid. The most recently clicked compatible module is preferred. Myotus settings let you change the modifier key.

On terminals with a native crafting or pattern handler, that handler keeps its normal transfer button. In EMI, use the additional **crafting module card +** button above the recipe arrow to explicitly fill this module. In JEI, this module is a fallback for AE2's terminal menus when no recipe-specific handler takes priority; external terminal menus need their own registration.

Each request fills **one recipe set**. Ingredients come from **ME storage first**, then your **main inventory and hotbar**. Player inventory slots locked for the open terminal are excluded.

Items already in the correct grid slots are retained. Items blocking the recipe are returned to ME storage first, then your inventory. If there is no capacity to return them, they stay in the grid. Missing ingredients can leave the grid only partially filled.

Take the result from the module to complete the craft. Transfer does not request autocrafting for missing ingredients, continually refill the grid, or support a maximum transfer mode.

## Removing the card or closing the terminal

The grid belongs to your currently open terminal menu. Its ingredients are not saved in the card or shared with another viewer's grid.

Removing the crafting module card or closing the terminal returns the remaining grid ingredients to your player inventory. Items that do not fit are dropped. The result preview does not become a crafted item when the grid is cleared.

## Recipe

The shapeless recipe uses an AE2 Basic Card and a Crafting Table.

<Recipe id="appliedterminalmodules:crafting_module" />
