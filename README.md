# Stook

<p align="center">
  <img src="logo.png" width="192" alt="Stook — wooden chest">
</p>

Client-only Fabric mod for SkyBlock Garden and mining.

## Download

Releases: https://github.com/gitgoon69/farming_mod/releases

| Minecraft | GitHub file |
| --- | --- |
| **26.1.2** (most players) | **Latest** release → `stook-x.y.z.jar` |
| **26.2** | **“Minecraft 26.2”** release → `stook-x.y.z-minecraft-26.2.jar` |

Install **one** JAR in `.minecraft/mods/`, with Fabric Loader + Fabric API for **the same** Minecraft version. Do not use the 26.2 JAR on 26.1.2.

In-game auto-install only downloads the JAR that matches your Minecraft version.

## Features

- Coins/hour HUD (NPC crop prices + Garden pest drops, and End Stone mining)
- In-game menu (`/stook`, `/fmt`, or `/fprofit`): every option, vanilla item icons
- Mature crop hitboxes = 1 block
- Visitor's Logbook: unique served
- Pest / Farm loadout via fishing rod
- Pest cooldown alert (tab list, at 2m50, 5s countdown)
- Auto Pest loadout at 2m50, Farm 0.5–1s after spawn
- NPC sell (`/stook sell`)
- Switch account from the title screen, pause menu, or Sys tab (Prism accounts in one click)

## Commands

See [`COMMANDS.md`](COMMANDS.md). Commands start with `/stook` (aliases: `/fmt`, `/fprofit`).

## Build

Java 25 required.

```
./gradlew.bat build
./gradlew.bat build "-Pmc=26.2"
```

JAR: `build/libs/stook-1.0.34.jar` (26.1.2) and `stook-1.0.34-minecraft-26.2.jar` (26.2)

## Updates

At login, the mod checks GitHub for **your** Minecraft version and offers **[Install]**.

- **26.1.2** → Latest → `stook-x.y.z.jar`
- **26.2** → “Minecraft 26.2” release → `stook-x.y.z-minecraft-26.2.jar`
