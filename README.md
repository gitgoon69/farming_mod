# Farming Profit

Mod Fabric **client-only** pour Hypixel SkyBlock Garden.

## Téléchargement

Releases : https://github.com/matteorlt/farming_mod/releases

| Minecraft | Fichier GitHub |
| --- | --- |
| **26.1.2** (la plupart des joueurs) | Release **Latest** → `farmingprofit-x.y.z.jar` |
| **26.2** | Release **« Minecraft 26.2 »** → `farmingprofit-x.y.z-minecraft-26.2.jar` |

Installe **un seul** JAR dans `.minecraft/mods/`, avec Fabric Loader + Fabric API de **la même** version Minecraft. Ne prends pas le JAR 26.2 si tu es en 26.1.2.

L’install auto en jeu ne télécharge **jamais** le JAR 26.2.

## Fonctionnalités

- HUD coins/heure (prix Bazaar Cofl)
- Hitbox crops mature = 1 bloc
- Visitor's Logbook : unique served
- Loadout Pest / Farm via canne à pêche
- Alerte cooldown pest (tab, à 2m50, compte à rebours 5s)
- Auto loadout Pest à 2m50, Farm 0.5–1s après spawn
- Vente NPC (`/fprofit sell`)

## Commandes

Voir [`COMMANDS.md`](COMMANDS.md). Toutes commencent par `/fprofit`.

## Build

Java 25 requis.

```
./gradlew.bat build
./gradlew.bat build "-Pmc=26.2"
```

JAR : `build/libs/farmingprofit-1.0.13.jar` (26.1.2) et `farmingprofit-1.0.13-minecraft-26.2.jar` (26.2)

## Mise à jour

En **26.1.2**, au login, le mod compare sa version à la release GitHub **Latest** et propose **[Installer]** (JAR 26.1.2 uniquement).

En **26.2**, pas d’install auto : le chat pointe vers la release GitHub « Minecraft 26.2 ».
