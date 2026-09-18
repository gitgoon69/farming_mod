# Farming Profit

Mod Fabric **client-only** pour Minecraft **26.1.2** (Hypixel SkyBlock Garden).

- HUD coins/heure (prix Bazaar Cofl)
- Hitbox crops mature = 1 bloc
- Visitor's Logbook : unique served
- Loadout Pest / Farm via canne à pêche
- Alerte cooldown pest (tab, à 2m50, compte à rebours 5s)
- Auto loadout Pest à 2m50, Farm 2s après spawn
- Vente NPC (`/fprofit sell`)

## Installation

1. Fabric Loader + Fabric API pour 26.1.2
2. Copie `farmingprofit-x.y.z.jar` dans `.minecraft/mods/`

Releases : https://github.com/matteorlt/farming_mod/releases

## Commandes

Voir [`COMMANDS.md`](COMMANDS.md). Toutes commencent par `/fprofit`.

## Build

Java 25 requis.

```
./gradlew.bat build
```

JAR : `build/libs/farmingprofit-1.0.12.jar`

## Mise à jour

Au login, le mod compare sa version à la dernière release GitHub. Clique **[Installer]** dans le chat (ou `/fprofit update install`) : le nouveau JAR est téléchargé, Minecraft se ferme, puis il suffit de relancer.
