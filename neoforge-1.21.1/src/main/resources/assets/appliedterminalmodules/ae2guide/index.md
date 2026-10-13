---
navigation:
  title: Applied Terminal Modules
  icon: appliedterminalmodules:equipment_module
  position: 81
---

# Applied Terminal Modules

Applied Terminal Modules adds equipment, crafting, workstation and optional accessory panels to AE2 terminals through Myotus. This section covers Minecraft 1.21.1 on NeoForge.

## Modules

<CategoryIndex category="applied terminal modules" />

## Install a module card

1. Craft the card for the panel you want. Each module page below includes its recipe.
2. Open a supported AE2 terminal, then open **Myotus's Terminal Upgrade Panel** using its terminal toolbar button or the configured key: <KeyBind id="key.myotus.toggle.upgrade_terminal_panel" />.
3. Place the card in an empty **Myotus terminal card slot**. If View Cell slots are also shown, use the card column, rather than the View Cell column.
4. The module panel appears while the card is installed. Different module types can be used together; only one card of each item type can be installed in a terminal.

To remove a module, take its card out of the same slot. Equipment, Curios and Accessories items remain equipped. Crafting, workstation and charging inputs return to your inventory; overflow is dropped.

If the toolbar button is hidden, enable it in [Myotus's Terminal Settings](myotus:feature/terminal-settings.md), or use the configured panel key. If there is no room for a module panel, reduce the GUI scale or make room around the terminal.

## Move and reset panels

All nine module panels share Myotus's movement animation and edge snapping settings. Clicking a panel brings it to the front when panels overlap. Automatic placement groups the panels to the left of the terminal.

Drag a panel by its handle. Positions are saved on this client and restored when the terminal is reopened. **Position Reset** in Myotus's terminal settings restores automatic placement for all nine panels, including currently uninstalled modules.

## Open the guide

Hover over a module card in your inventory and hold **G**, GuideME's default guide key, to open that card's page in the AE2 guide. Your current guide key is <KeyBind id="key.guideme.guide" />. If you changed the key, check **Options → Controls → Key Binds → GuideME**. Minecraft's language setting selects the Korean pages when set to Korean, with English as the fallback.

You can also open this section directly with `/guidemec ae2:guide open appliedterminalmodules:index.md`. This client command does not require cheats. `/guidemec ae2:guide open` opens the AE2 guide at your previous page.
