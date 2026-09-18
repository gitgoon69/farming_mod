# Farming Profit

Client-only Fabric mod for Hypixel SkyBlock Garden.

## Download

Releases: https://github.com/matteorlt/farming_mod/releases

| Minecraft | GitHub file |
| --- | --- |
| **26.1.2** (most players) | **Latest** release → `farmingprofit-x.y.z.jar` |
| **26.2** | **“Minecraft 26.2”** release → `farmingprofit-x.y.z-minecraft-26.2.jar` |

Install **one** JAR in `.minecraft/mods/`, with Fabric Loader + Fabric API for **the same** Minecraft version. Do not use the 26.2 JAR on 26.1.2.

In-game auto-install **never** downloads the 26.2 JAR.

## Features

- Coins/hour HUD (Cofl Bazaar prices)
- In-game menu (`/fprofit` or `/fprofit menu`): every option, vanilla item icons
- Mature crop hitboxes = 1 block
- Visitor's Logbook: unique served
- Pest / Farm loadout via fishing rod
- Pest cooldown alert (tab list, at 2m50, 5s countdown)
- Auto Pest loadout at 2m50, Farm 0.5–1s after spawn
- NPC sell (`/fprofit sell`)

## Commands

See [`COMMANDS.md`](COMMANDS.md). All commands start with `/fprofit`.

## Build

Java 25 required.

```
./gradlew.bat build
./gradlew.bat build "-Pmc=26.2"
```

JAR: `build/libs/farmingprofit-1.0.16.jar` (26.1.2) and `farmingprofit-1.0.16-minecraft-26.2.jar` (26.2)

## Updates

On **26.1.2**, at login, the mod compares its version to the GitHub **Latest** release and offers **[Install]** (26.1.2 JAR only).

On **26.2**, there is no auto-install: chat points to the GitHub “Minecraft 26.2” release.
