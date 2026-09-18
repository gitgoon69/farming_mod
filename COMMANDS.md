# Farming Profit — Commands

Every command starts with **`/fprofit`**.

## In-game menu

Every option (HUD, hitbox, pest, pickaxe, pack, updates) lives in one screen, with vanilla icons. Same menu on **26.1.2** and **26.2**.

| Command | Description |
| --- | --- |
| `/fprofit` | Opens the menu |
| `/fprofit menu` | Opens the menu |
| Keybind `Open Farming Profit menu` | Shortcut (unbound by default, Controls) |

## Help

| Command | Description |
| --- | --- |
| `/fprofit help` | Prints chat help |

## HUD (coins / hour)

The HUD shows when a **farming tool** is in hand. The crop comes from the broken block, otherwise from the tool.

| Command | Description |
| --- | --- |
| `/fprofit toggle` | Enable or disable the HUD |
| `/fprofit hitbox` | 1-block hitbox when mature, lowest otherwise (cocoa included) |
| `/fprofit reset` | Reset the farm session (counter, time, profit) |
| `/fprofit prices` | Refresh Cofl Bazaar prices |
| `/fprofit update` | Check GitHub for a new version |
| `/fprofit update install` | Download the JAR, close Minecraft, relaunch |
| `/fprofit mode OFFER` | **Sell offer** prices (default, Cofl `buyPrice` / 160) |
| `/fprofit mode INSTANT` | **Instant sell** prices (Cofl `sellPrice` / 160) |

## HUD position

| Command | Description |
| --- | --- |
| `/fprofit move` | Open the editor: drag the HUD, arrows (Shift = 10 px), Esc / Done |
| `/fprofit move <x> <y>` | Place the HUD at the given coordinates |
| `/fprofit move reset` | Reset the HUD to `x=8` `y=48` |

Examples:

```
/fprofit move
/fprofit move 20 80
/fprofit move reset
```

## Visitor's Logbook

In **Visitor's Logbook**, an overlay on the right shows **Unique served** (visitors with at least 1 accepted offer) and total accepted offers. Open every page for the full count.

## Crop hitboxes

**Aim / click** hitbox only (not collision):

- **Mature**: 1×1×1 cube (wheat, carrots, potatoes, nether wart, cocoa, mushrooms)
- **Not grown yet**: lowest hitbox (stage 0), so young plants are not broken

| Command | Description |
| --- | --- |
| `/fprofit hitbox` | Enable or disable (default: on) |

## Pest loadout (fishing rod)

Right-click with a **fishing rod**:

- not in Pest Mode → `/loadout` then left-click **Pest** (rainbow banner)
- already in Pest Mode → `/loadout` then left-click **Farm** (banner disappears)

| Command | Description |
| --- | --- |
| `/fprofit pest` | Toggle Pest / Farm (same as the rod) |
| `/fprofit pestalert` | Enable or disable the pest cooldown alert (tab) |
| `/fprofit pestauto` | Auto Pest loadout at 2m50, /setspawn on spawn, Farm 0.5–1s later |
| `/fprofit pickaxe` | Auto pickaxe ability (right-click when cooldown hits 0) |
| `/fprofit serverpack` | Hypixel server pack at lowest priority (default: ON) |

Names in `farmingprofit.json`: `pestLoadoutName` (default `Pest`), `farmLoadoutName` (default `Farm`).

## Pest cooldown alert (tab)

The Hypixel tab **Pests** widget shows a timer `Cooldown: 1m 58s`. At **2m50** remaining, a large title appears with a **5-second countdown** and a sound.

The Pests widget must be enabled: `/widget` → Pests.

| Command | Description |
| --- | --- |
| `/fprofit pestalert` | Enable or disable the alert (default: ON) |

Settings in `farmingprofit.json`: `pestCooldownAlertAtSeconds` (default `170` = 2m50), `pestCooldownAlertCountdown` (default `5`).

At **2m50**, if `autoPestLoadout` is ON, the mod equips the **Pest** loadout. On spawn it sends `/setspawn`, then **0.5–1s later** it switches back to **Farm**.

| Command | Description |
| --- | --- |
| `/fprofit pestauto` | Enable or disable auto switch (default: ON) |

## Auto pickaxe ability (mining)

When pickaxe ability cooldown hits **0** (item overlay / chat *You used your … Pickaxe Ability!*), the mod **right-clicks** if a SkyBlock **pickaxe / drill** is in hand. No click in the Garden, or while a menu is open.

| Command | Description |
| --- | --- |
| `/fprofit pickaxe` | Enable or disable (default: ON) |

## Hypixel server pack

Hypixel forces the **World Specific Resources Hypixel Skyblock** pack. The mod still accepts it (required to play) and keeps it loaded for SkyBlock item textures, but puts it **last**: vanilla Minecraft and your packs take priority.

| Command | Description |
| --- | --- |
| `/fprofit serverpack` | Enable or disable low priority (default: ON) |

With **Polar**, these mixins (pack + crop hitboxes) are disabled: Polar hooks the same methods natively, which crashed Windows (`ntdll`). HUD, pest, sell, and logbook stay active.

## NPC sell (sacks + cookie menu)

Empties the sack, opens `/boostercookiemenu`, then **middle-clicks** only the named item (100 ms between slots). Rechecks inventory at the end of each pass.

An active **Booster Cookie** is required.

| Command | Description |
| --- | --- |
| `/fprofit sell <item>` | 1 round: `/gfs <item> 9999` → cookie menu → sell |
| `/fprofit sell <item> <times>` | Repeat the cycle `<times>` times (1–999) |
| `/fprofit sell cancel` | Cancel the current sell |

Stops automatically after **3 empty rounds** in a row.

Examples:

```
/fprofit sell enchanted_wheat
/fprofit sell enchanted_wheat 10
/fprofit sell enchanted wheat 5
/fprofit sell cancel
```

Only the named item is sold. Other inventory slots are not clicked.

## Updates

At login (if online), the mod checks GitHub for **the same Minecraft version** you are running.

- **26.1.2** → GitHub **Latest** (`farmingprofit-x.y.z.jar`)
- **26.2** → GitHub **Minecraft 26.2** release (`farmingprofit-x.y.z-minecraft-26.2.jar`)

If an update exists, **[Install]** appears in chat: it downloads that JAR into `mods/`, closes Minecraft, then a script replaces the old file. Just **relaunch the game**.

A 26.1.2 client never auto-installs a 26.2 JAR, and a 26.2 client never auto-installs a 26.1.2 JAR.

Windows locks the JAR while Minecraft is running, which is why the game is closed automatically (same idea as libautoupdate / ModUpdater).

| Command | Description |
| --- | --- |
| `/fprofit update` | Run the GitHub check again |
| `/fprofit update install` | Install the matching JAR and close the game |

Disable with `"checkUpdates": false` in `farmingprofit.json`.

## Config

Settings (HUD, position, price mode, crop hitboxes) are saved in:

`.minecraft/config/farmingprofit.json`
