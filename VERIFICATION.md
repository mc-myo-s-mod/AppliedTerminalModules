# 1.21.1 verification — 2026-10-06 (Asia/Seoul)

## Five workstation modules and optional Accessories (2026-10-06 14:38 KST)

- Added Stonecutting, Anvil, Charging, Smithing and Grindstone modules. The four
  workstations use Minecraft's native menus and result rules; Charging uses ME power
  for AE2 chargeable items and Forge Energy capabilities. Input stacks return on module
  removal and menu closure. Result slots reject pick-all and partial extraction paths.
- Curios and Accessories are optional, independently guarded integrations. Accessories
  exposes functional slots, uses its native equip checks and requests inventory sync before
  opening. With `accessories_compat_layer`, the Curios card is unavailable to avoid exposing
  the same backing inventory twice. That bridge configuration was source-reviewed only.
- All nine modules use the shared Myotus panel movement/reset behavior. New panels use AE2
  textures and icons. Anvil renaming uses a focused AE2 text field with Done/Cancel/Escape;
  its operation consumes the native XP cost but has no physical anvil durability.
- JEI/EMI and Myotus Alt-transfer now support stonecutting and smithing alongside crafting.
  Transfers use server recipes, ME/player sources and existing access, energy and locked-slot
  checks. They fill one recipe set and do not automatically take output or request autocrafting.
- `compileJava`, `runData` and final root `build` passed. The final release was built without
  `client_smoke`; the ordinary `test` task has no sources. The JAR contains nine item models,
  nine generated recipes, twenty English/Korean AE2 GuideME pages, the result-slot guard mixin,
  and no development smoke classes or embedded optional-mod classes.
- GameTest runs passed: Curios only, 52 entries at 14:17; neither optional mod, 62 entries at
  14:20; Accessories only, 62 entries at 14:26; both, all 62 entries at 14:27. The last ten
  regression cases were added after the Curios-only run. Optional integration cases return
  early when their dependency is absent. Tests cover native workstation results/XP,
  charging/energy accounting, output collection, item return, transfer constraints and
  optional slot behavior. The final both-enabled log is `neoforge-1.21.1/run-gametest/logs/latest.log`.
- Dedicated server with both optional mods reached `Done (0.781s)!` at 14:29:19. Console `stop`
  saved all worlds and exited successfully at 14:29:52.
- Final development client runs passed with JEI at 14:31 and EMI at 14:33, both with Curios and
  Accessories. Checks covered all five new layouts, recipe previews, animated drag and outside
  release, actual Position Reset, rename focus/Escape/Cancel, registered viewer transfer
  preflight and the Myotus selected/sole-module router. Client transfer checks are simulations;
  server item transfer behavior is covered by GameTests.
- Accessories displayed 18 functional slots after a real pre-open synchronization request.
  Its visible slots and click pass-through were checked. The development world had zero
  Curios slots, so that screen check verified the empty panel/tooltip, not Curios item rendering.
  Server Curios slot rules are covered by GameTests. Both languages rendered all twenty guide
  pages and nine indexed module links inside AE2's guide category.
- Retained client logs: `neoforge-1.21.1/build/reports/client-smoke/{jei,emi}/modules-latest.log`.
  Screenshots are in the corresponding `workstations/` folders; default layout, rename and
  optional-slot captures were visually inspected. OpenAL was unavailable, so development
  clients ran without sound. Existing EMI tag-translation and GuideME sprite warnings remained.
- Deployed both the addon and the current Myotus 19.2.1-SNAPSHOT to
  `H:/Minecraft/Instances/ae2-1.21.1/mods` after checking that Minecraft was closed. Myotus is
  required for the new shared widget and recipe-transfer API. Source/deployed SHA-256 matched,
  and all 55 active metadata mod IDs were unique. Backup and hash receipt:
  `.codex-backups/five-terminal-modules-20261006-143827/` under that instance.
  - Addon SHA-256: `4F047C39A81241724CFA200A296DC3828C56BD942034FE97806789FBA74FC295`
  - Myotus SHA-256: `C992D76B4E61BFFCFC4CBCFEEF80098ACBCA4FF754D522622B0D6D96B461B4E4`
- Target Curios/EMI-enabled and JEI-disabled states were preserved; Accessories was not added
  to the target instance. Development versions were Curios 9.5.1, Accessories
  1.1.0-beta.53+1.21.1, JEI 19.19.3.224 and EMI 1.1.22+1.21.1. The target has EMI 1.1.24;
  target-modpack launch/manual gameplay was not performed. Only NeoForge 1.21.1 is implemented.

## AE2 GuideME category update (2026-10-06 13:48 KST)

- Retained the existing `Applied Terminal Modules` root entry inside `ae2:guide` and its
  three child pages. Registered all three pages in GuideME's `applied terminal modules`
  category and placed the native `CategoryIndex` list near the top of both language indexes.
- Development client with EMI passed at 13:47: both languages had the root navigation entry,
  exactly three expected children/category entries and three rendered module links.
  All eight pages, recipes and item-to-page indices also passed. The Korean category screenshot
  was visually checked at `neoforge-1.21.1/build/reports/client-smoke/emi/ko_kr-index.png`.
  Retained log: `neoforge-1.21.1/build/reports/client-smoke/emi/category-latest.log`.
- `compileJava` and release `build` passed. The JAR contains eight guide pages with category
  metadata and no development smoke classes. Production changes are guide resources only;
  server/GameTests and the separate JEI client were not rerun for this update.
- Deployed the addon JAR to `H:/Minecraft/Instances/ae2-1.21.1/mods` after confirming the game
  was closed. SHA-256 matched the build, and all 55 active metadata mod IDs were unique.
  Backup: `.codex-backups/guideme-category-20261006-134831/`. Myotus and viewer states were unchanged.
- Target-modpack launch and manual navigation were not performed. The development client's
  unavailable OpenAL device disabled audio; it did not prevent guide rendering or normal exit.

## Shared crafting panel behavior update (2026-10-05 16:14 KST)

- Replaced the standalone crafting widget's movement/layout code with Myotus's shared panel.
  All three panels now share movement interpolation, snapping, stacking, input capture,
  exclusion zones and automatic left-side placement. The crafting layout keeps AE2 textures
  and adds the same top-edge grip used by the other panels.
- Myotus 19.2.1-SNAPSHOT exposes only the required layout hooks and includes the panel in its
  1.21.1 API classifier. Addon metadata rejects older Myotus versions. The real addon panel
  compiled against the API JAR with the full Myotus implementation excluded from the classpath.
- Myotus `compileJava`, `build` and all 90 unit tests passed. The new regression checks
  constructor safety, interpolated coordinates, all ten rendered/highlighted slots,
  the output-slot input guard, inactive slots and hidden panels.
- Actual AE2 screen smoke checks passed with no viewer (16:07), JEI (16:08) and EMI (16:10):
  animated title drag, blocked terminal overlap, output input blocking during movement,
  outside release, saved position, reopening, viewport resize, the real Position Reset button,
  edge snapping and the shared animation toggle. Each run also rendered eight GuideME pages.
  Screenshots and logs are under `neoforge-1.21.1/build/reports/client-smoke/panels/{none,jei,emi}`.
  The default and snapped captures visibly include the ninth grid item and output item/count.
- The first smoke attempt checked a wall-clock animation using only tick count; client catch-up
  ticks could run before 160 ms elapsed. The check now waits for actual elapsed time; the
  deterministic unit test verifies the exact interpolation midpoint without timing assumptions.
- The release build passed and contains no development smoke classes. Dedicated server startup
  reached `Done (1.057s)!` at 16:11:14; console `stop` saved all dimensions and exited at 16:11:58.
  No crafting/server logic changed, so the previously passing GameTests were not rerun.
- After the user exited the target game, both JARs were deployed to
  `H:/Minecraft/Instances/ae2-1.21.1/mods` at 16:14. Source/deployed SHA-256 matched;
  all 55 active metadata mod IDs were unique. Existing EMI-enabled/JEI-disabled states were preserved.
  Previous JARs are in `.codex-backups/crafting-shared-panel-20261005-161439/` under that instance.
  The manual target-modpack drag experience remains unverified; automated checks above used
  the isolated development client.

## Panel dragging and position reset update (15:10 KST)

- Crafting panel uses AE2 `extra_panels.png`, `Icon.SLOT_BACKGROUND` and `Icon.ARROW_RIGHT`.
  Its title bar supports dragging without consuming native slot clicks.
- All three addon positions use the addon client config and register with Myotus's common
  reset action. Built-in Myotus module removal from the parallel cleanup task is preserved.
- `compileJava` and `build` passed with the updated Maven Local Myotus snapshot. Myotus's
  build/check also passed (89 unit tests, including external position reset and TOML persistence).
- Actual AE2 screen input checks passed without a viewer (15:05), with JEI (15:07), and with
  EMI (15:08): title drag, blocked terminal overlap, release outside the panel, reopen,
  viewport shrink/restore without overwriting the preference, and the actual Myotus settings
  button restoring all module positions. Eight GuideME pages also rendered in each run.
  Screenshots: `neoforge-1.21.1/build/reports/client-smoke/panels/`, including `jei/` and `emi/`.
- The parallel cleanup task verified 26 Myotus and 25 addon GameTests against this Myotus
  implementation. No server crafting logic changed in this update.
- The addon dedicated server reached `Done (0.801s)!` at 15:08:40. Its isolated development
  process was terminated afterward because the launcher's stdin was closed; this was startup
  verification, not a graceful-save test. Client smoke processes exited normally.
- Final JAR contains no `PanelSmoke`, `ViewerSmoke`, `ClientSmoke` or `JeiSmoke` classes.
- Deployed both JARs to `H:/Minecraft/Instances/ae2-1.21.1/mods`; source/target SHA-256 matched
  and active mod IDs were unique. Prior JARs are in
  `.codex-backups/terminal-panel-drag-reset-20261005-151052/` under that instance.
- Target-modpack manual GUI interaction remains unverified; automated screen input checks
  above ran in the isolated development client. Existing viewer enable/disable states were preserved.

## Initial implementation verification

Environment: Java 21.0.11, Gradle 8.14, NeoForge 21.1.219, AE2 19.2.17,
Myotus 19.2.0-SNAPSHOT, GuideME 21.1.16. Optional dependencies tested:
Curios 9.5.1+1.21.1, JEI 19.19.3.224, EMI 1.1.22+1.21.1.

## Completed

- `compileJava`: passed against the actual dependencies, including both viewer APIs.
- `runData`: generated three recipes and three recipe advancements; Curios recipe is conditional.
  Final regeneration wrote no changed recipe files.
- Root `:build`: packaged `common` and `neoforge-1.21.1` at 01:26 KST.
  The ordinary `test` task has no sources; behavioral tests run through Minecraft GameTest.
- `runGameTestServer`: all 25 required tests passed with Curios (01:11:51 KST).
- `runGameTestServer -Penable_curios=false`: all 25 test entries passed (01:25:49 KST).
  Four Curios cases are guarded no-ops in this mode; the other 21 run normally.
- `runServer -Penable_curios=false`: reached `Done (3.322s)!` at 01:26:46 KST without
  JEI, EMI or Curios installed. No addon startup errors occurred.
  Console `stop` then saved all dimensions and shut down normally at 01:27:43 KST.
- Inspected the final JAR: eight guide Markdown pages, both languages, nine viewer integration
  class files, three recipes, metadata and the existing access transformer are packaged.
  Development smoke classes and datagen `.cache` files are absent.
- English and Korean localization keys match (20 keys each).

The nine new transfer GameTests cover shaped layout, shapeless recipes, repeated requests,
partial ingredient counts, invalid/stale/closed requests, same-tick card removal, rejected
special recipes, full survival/creative inventories, partial storage insertion, energy,
action-source permissions, disconnected storage, view-cell filters and locked player slots.
All call the real server transfer service and check actual grid/inventory state.
The prior 16 tests cover equipment, Curios, crafting consumption and menu cleanup.

## Client and GuideME

The development-only `src/smoke/java` sources are enabled with `-Pclient_smoke=true`.
They open an isolated `run-client/saves/ATM-Smoke` world, originally copied from this project's
smoke server world. A fresh checkout must prepare that test world before using the smoke flag.
Normal builds and normal `runClient` do not include these sources.

| Runtime | Result |
| --- | --- |
| No recipe viewer, Curios present | Eight guide pages rendered; card-to-page item indices and compiled recipes passed. |
| JEI only, Curios present | Shared client preflight and JEI's actual registered transfer handler simulation passed; all eight guide pages rendered (01:21 KST). |
| EMI only, Curios absent | Native EMI plugin discovered and loaded; shared client preflight passed; all eight guide pages rendered, including the missing-Curios recipe fallback (01:21 KST). |
| JEI + EMI, Curios present | Native EMI plugin loaded; shared preflight and eight guide pages passed (01:25 KST). |

Preflight checks cover absent cards, empty sources, three versus four repeated ingredients,
hidden panels, and unchanged inventories during simulation. JEI's universal handler is queried
through its public runtime manager and invoked with `doTransfer=false`.
EMI suppresses JEI callbacks for mods with native EMI plugins; the combined-mode smoke therefore
uses the native EMI path and does not wait for a JEI runtime callback for this mod.
Screenshots and retained logs: `neoforge-1.21.1/build/reports/client-smoke/{none,jei,emi,both}/`.
A Korean crafting-page screenshot was visually inspected.

## Artifact

`neoforge-1.21.1/build/libs/AppliedTerminalModules-1.21.1-0.1.0-SNAPSHOT.jar`

Latest size: 117,906 bytes.
SHA-256: `5CFA064780601AA5BF650ADB83535F67EF46E618602F672B092F0F7E9E7064DB`

Current Myotus dependency: `Myotus-1.21.1-19.2.1-SNAPSHOT.jar`.
SHA-256: `C50ED9EEE32BDFECB42FBF26B0C867C2D2BAD0E10CEE835C0018A7D25F11D2CA`.
This snapshot contains the extensible shared panel API; older snapshots are not supported.

## Remaining boundaries

- No manual end-to-end JEI/EMI button click, EMI supplemental-button placement check, guide G-key
  press, multiplayer packet round-trip, reconnect, or target-modpack validation was performed.
  Server behavior, client simulation, plugin loading and guide rendering are separate evidence.
- Check terminal panels at different GUI scales and with competing addon panels in actual gameplay.
- JEI registration covers exact built-in AE2 menu classes. Existing category-specific handlers take
  precedence; unrelated terminal subclasses need separate registration. EMI's additional module
  button covers standard 118x54 crafting layouts, not arbitrary custom recipe layouts.
- EMI logs untranslated third-party tags; combined JEI/EMI logs duplicate `jei:/...` tag recipes.
  These messages do not name addon recipes and did not prevent the completed smoke checks.
  Dependency refmap/absent-integration warnings also remain.
- Transfers fill one recipe set. No maximum-fill mode, continuous ME replenishment or automatic
  crafting request is implemented. Normal Q is restricted for multi-item outputs; use Ctrl+Q.
- Forge 1.20.1 and NeoForge 26.1.2 remain placeholders and produce no mod JAR.
- No remote publication, Git commit or remote push was performed. Local deployment is recorded above.
