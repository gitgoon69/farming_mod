# Fermento — Commands

Every command starts with **`/fermento`**. Aliases: **`/fmt`**, **`/fprofit`**.

## In-game menu

Every option (HUD, hitbox, pest, pickaxe, pack, updates) lives in one screen, with vanilla icons. Same menu on **26.1.2** and **26.2**.

| Command | Description |
| --- | --- |
| `/fermento` | Opens the menu |
| `/fermento menu` | Opens the menu |
| Keybind `Open Fermento menu` | Shortcut (unbound by default, Controls) |

## Help

| Command | Description |
| --- | --- |
| `/fermento help` | Prints chat help |

## HUD (coins / hour)

The HUD shows when a **farming tool** is in hand, or while you are in **The End**. The crop comes from the broken block, otherwise from the tool.

| Command | Description |
| --- | --- |
| `/fermento toggle` | Enable or disable the HUD |
| `/fermento hitbox` | 1-block hitbox when mature, lowest otherwise (cocoa included) |
| `/fermento reset` | Reset the farm session (counter, time, profit) |
| `/fermento prices` | Refresh Cofl Bazaar prices (pest loot: Dung, Compost…) |
| `/fermento update` | Check GitHub for a new version |
| `/fermento update install` | Download the JAR, close Minecraft, and relaunch |
| `/fermento mode OFFER` | Pest loot **sell offer** (crops always use NPC) |
| `/fermento mode INSTANT` | Pest loot **instant sell** (crops always use NPC) |

## HUD position

| Command | Description |
| --- | --- |
| `/fermento move` | Open the editor: drag the HUD, arrows (Shift = 10 px), Esc / Done |
| `/fermento move <x> <y>` | Place the HUD at the given coordinates |
| `/fermento move reset` | Reset the HUD to `x=8` `y=48` |

Examples:

```
/fermento move
/fermento move 20 80
/fermento move reset
```

## Visitor's Logbook

In **Visitor's Logbook**, an overlay on the right shows **Unique served** (visitors with at least 1 accepted offer) and total accepted offers. Open every page for the full count.

## Crop hitboxes

**Aim / click** hitbox only (not collision):

- **Mature**: 1×1×1 cube (wheat, carrots, potatoes, nether wart, cocoa, mushrooms)
- **Not grown yet**: lowest hitbox (stage 0), so young plants are not broken

| Command | Description |
| --- | --- |
| `/fermento hitbox` | Enable or disable (default: on) |

## Pest loadout (fishing rod)

Right-click with a **fishing rod**:

- not in Pest Mode → `/loadout` then left-click **Pest** (rainbow banner)
- already in Pest Mode → `/loadout` then left-click **Farm** (banner disappears)

| Command | Description |
| --- | --- |
| `/fermento pest` | Toggle Pest / Farm (same as the rod) |
| `/fermento pestalert` | Enable or disable the pest cooldown alert (tab) |
| `/fermento pestauto` | Auto Pest loadout at 2m50, /setspawn on spawn, Farm 0.5–1s later |
| `/fermento pickaxe` | Auto pickaxe ability (right-click when cooldown hits 0) |
| `/fermento serverpack` | Hypixel server pack at lowest priority (default: ON) |

Names in `fermento.json`: `pestLoadoutName` (default `Pest`), `farmLoadoutName` (default `Farm`).

## Pest cooldown alert (tab)

The Hypixel tab **Pests** widget shows a timer `Cooldown: 1m 58s`. At **2m50** remaining, a large title appears with a **5-second countdown** and a sound.

The Pests widget must be enabled: `/widget` → Pests.

| Command | Description |
| --- | --- |
| `/fermento pestalert` | Enable or disable the alert (default: ON) |

Settings in `fermento.json`: `pestCooldownAlertAtSeconds` (default `170` = 2m50), `pestCooldownAlertCountdown` (default `5`).

At **2m50**, if `autoPestLoadout` is ON, the mod equips the **Pest** loadout. On spawn it sends `/setspawn`, then **0.5–1s later** it switches back to **Farm**.

| Command | Description |
| --- | --- |
| `/fermento pestauto` | Enable or disable auto switch (default: ON) |

## Auto pickaxe ability (mining)

When pickaxe ability cooldown hits **0** (item overlay / chat *You used your … Pickaxe Ability!*), the mod **right-clicks** if a SkyBlock **pickaxe / drill** is in hand. No click in the Garden, or while a menu is open.

| Command | Description |
| --- | --- |
| `/fermento pickaxe` | Enable or disable (default: ON) |

## End Stone profit

In **The End** (including Dragon's Nest and the other End areas), the same coins/hour HUD switches to End Stone.

Profit follows SkyHanni's gemstone coins/hour: items that land in **sacks** (hover on the `[Sacks]` chat line), priced at **the better of NPC and bazaar**. End Stone NPC is 2 coins; Enchanted End Stone is 160 stones. Sell offer uses the bazaar when it beats NPC (instant sell usually does not). **Mite Gel** from that session is added the same way.

Until a sack message arrives, coins/hour is a pace: blocks you break × `(1 + (Mining Fortune + Block Fortune) / 100)`. Fortune is read from the tab stats. That pace does not include Mining Spread; the sack total does.

`/fermento mode OFFER|INSTANT` applies to End Stone and pest loot. Crops stay NPC. `/fermento reset` clears both sessions.

| Command | Description |
| --- | --- |
| `/fermento endstone` | Enable or disable End Stone profit (default: ON) |

## Hypixel server pack

Hypixel forces the **World Specific Resources Hypixel Skyblock** pack. The mod still accepts it (required to play) and keeps it loaded for SkyBlock item textures, but puts it **last**: vanilla Minecraft and your packs take priority.

| Command | Description |
| --- | --- |
| `/fermento serverpack` | Enable or disable low priority (default: ON) |

With **Polar**, these mixins (pack + crop hitboxes) are disabled: Polar hooks the same methods natively, which crashed Windows (`ntdll`). HUD, pest, sell, and logbook stay active.

## NPC sell (sacks + cookie menu)

Empties the sack, opens `/boostercookiemenu`, then **middle-clicks** only the named item (100 ms between slots). Rechecks inventory at the end of each pass.

An active **Booster Cookie** is required.

| Command | Description |
| --- | --- |
| `/fermento sell <item>` | 1 round: `/gfs <item> 9999` → cookie menu → sell |
| `/fermento sell <item> <times>` | Repeat the cycle `<times>` times (1–999) |
| `/fermento sell cancel` | Cancel the current sell |

Stops automatically after **3 empty rounds** in a row.

Examples:

```
/fermento sell enchanted_wheat
/fermento sell enchanted_wheat 10
/fermento sell enchanted wheat 5
/fermento sell cancel
```

Only the named item is sold. Other inventory slots are not clicked.

## Updates

When the game starts (and again on login if needed), the mod checks GitHub for **the same Minecraft version** you are running.

- **26.1.2** → GitHub **Latest** (`fermento-x.y.z.jar`)
- **26.2** → GitHub **Minecraft 26.2** release (`fermento-x.y.z-minecraft-26.2.jar`)

If a newer JAR exists, it is downloaded into `mods/`, Minecraft closes, the old file is replaced, and **the same game process is started again**. No click on **[Install]** is required.

A 26.1.2 client never installs a 26.2 JAR, and a 26.2 client never installs a 26.1.2 JAR. The Loom dev environment does not auto-install.

Windows locks the JAR while Minecraft is running, which is why the game is closed before the file is swapped (same idea as libautoupdate / ModUpdater).

| Command | Description |
| --- | --- |
| `/fermento update` | Run the GitHub check again |
| `/fermento update install` | Install the matching JAR, close the game, and relaunch |

Disable with `"checkUpdates": false` in `fermento.json`.

## Switch account

From the **title screen**, the **pause menu** (top-right button), or Fermento **Sys → Switch account**.

The mod reads Microsoft accounts from common launchers (Prism / PolyMC / MultiMC, official `launcher_accounts.json`, Modrinth `minecraft_auth.json`, ATLauncher, GDLauncher) and from the **current in-game session** (any launcher). Click one to switch without copying a token.

Otherwise, paste a Minecraft access token. The token is checked with Minecraft Services, then the client session is replaced. If you are on a server, the mod disconnects so you can rejoin as the new account.

## Config

Settings (HUD, position, price mode, crop hitboxes) are saved in:

`.minecraft/config/fermento.json`
