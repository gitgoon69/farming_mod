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

The HUD shows when a **farming tool** is in hand. The crop comes from the broken block, otherwise from the tool.

| Command | Description |
| --- | --- |
| `/fermento toggle` | Enable or disable the HUD |
| `/fermento hitbox` | 1-block hitbox when mature, lowest otherwise (cocoa included) |
| `/fermento reset` | Reset the farm session (counter, time, profit) |
| `/fermento prices` | Refresh Cofl Bazaar prices |
| `/fermento update` | Check GitHub for a new version |
| `/fermento update install` | Download the JAR, close Minecraft, relaunch |
| `/fermento mode OFFER` | **Sell offer** prices (default, Cofl `buyPrice` / 160) |
| `/fermento mode INSTANT` | **Instant sell** prices (Cofl `sellPrice` / 160) |

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

At login (if online), the mod checks GitHub for **the same Minecraft version** you are running.

- **26.1.2** → GitHub **Latest** (`fermento-x.y.z.jar`)
- **26.2** → GitHub **Minecraft 26.2** release (`fermento-x.y.z-minecraft-26.2.jar`)

If an update exists, **[Install]** appears in chat: it downloads that JAR into `mods/`, closes Minecraft, then a script replaces the old file. Just **relaunch the game**.

A 26.1.2 client never auto-installs a 26.2 JAR, and a 26.2 client never auto-installs a 26.1.2 JAR.

Windows locks the JAR while Minecraft is running, which is why the game is closed automatically (same idea as libautoupdate / ModUpdater).

| Command | Description |
| --- | --- |
| `/fermento update` | Run the GitHub check again |
| `/fermento update install` | Install the matching JAR and close the game |

Disable with `"checkUpdates": false` in `fermento.json`.

## Switch account

From the **title screen**, the **pause menu** (top-right button), or Fermento **Sys → Switch account**.

With **Prism Launcher**, the mod reads `%AppData%\PrismLauncher\accounts.json` and lists your Microsoft accounts. Click one to switch without copying a token (and without closing the game in Prism).

Otherwise, paste a Minecraft access token. The token is checked with Minecraft Services, then the client session is replaced. If you are on a server, the mod disconnects so you can rejoin as the new account.

## Config

Settings (HUD, position, price mode, crop hitboxes) are saved in:

`.minecraft/config/fermento.json`
