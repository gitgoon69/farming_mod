# Fermento

<p align="center">
  <img src="logo.png" width="192" alt="Fermento — pièce d'or et blé du Garden">
</p>

Client-only Fabric mod for Hypixel SkyBlock Garden. Named after the endgame farming armor.

## Download

Releases: https://github.com/matteorlt/farming_mod/releases

| Minecraft | GitHub file |
| --- | --- |
| **26.1.2** (most players) | **Latest** release → `fermento-x.y.z.jar` |
| **26.2** | **“Minecraft 26.2”** release → `fermento-x.y.z-minecraft-26.2.jar` |

Install **one** JAR in `.minecraft/mods/`, with Fabric Loader + Fabric API for **the same** Minecraft version. Do not use the 26.2 JAR on 26.1.2.

In-game auto-install only downloads the JAR that matches your Minecraft version.

## Features

- Coins/hour HUD (Cofl Bazaar prices)
- In-game menu (`/fermento`, `/fmt`, or `/fprofit`): every option, vanilla item icons
- Mature crop hitboxes = 1 block
- Visitor's Logbook: unique served
- Pest / Farm loadout via fishing rod
- Pest cooldown alert (tab list, at 2m50, 5s countdown)
- Auto Pest loadout at 2m50, Farm 0.5–1s after spawn
- NPC sell (`/fermento sell`)
- Switch account from the title screen, pause menu, or Sys tab (Prism accounts in one click)

## Commands

See [`COMMANDS.md`](COMMANDS.md). Commands start with `/fermento` (aliases: `/fmt`, `/fprofit`).

## Build

Java 25 required.

```
./gradlew.bat build
./gradlew.bat build "-Pmc=26.2"
```

JAR: `build/libs/fermento-1.0.20.jar` (26.1.2) and `fermento-1.0.20-minecraft-26.2.jar` (26.2)

## Updates

At login, the mod checks GitHub for **your** Minecraft version and offers **[Install]**.

- **26.1.2** → Latest → `fermento-x.y.z.jar`
- **26.2** → “Minecraft 26.2” release → `fermento-x.y.z-minecraft-26.2.jar`
