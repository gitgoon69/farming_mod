# Farming Profit — Commandes

Toutes les commandes commencent par **`/fprofit`**.

## Aide

| Commande | Description |
| --- | --- |
| `/fprofit` | Affiche l’aide |
| `/fprofit help` | Affiche l’aide |

## HUD (coins / heure)

Le HUD s’affiche dès qu’un **outil de farm** est en main. La crop vient du bloc cassé, sinon de l’outil.

| Commande | Description |
| --- | --- |
| `/fprofit toggle` | Active ou désactive le HUD |
| `/fprofit hitbox` | Hitbox 1 bloc si mature, plus basse sinon (cacao inclus) |
| `/fprofit reset` | Reset la session de farm (compteur, temps, profit) |
| `/fprofit prices` | Rafraîchit les prix Bazaar Cofl |
| `/fprofit update` | Vérifie s’il y a une nouvelle version GitHub |
| `/fprofit update install` | Télécharge le JAR, ferme Minecraft, relance le jeu |
| `/fprofit mode OFFER` | Prix **sell offer** (défaut, `buyPrice` Cofl / 160) |
| `/fprofit mode INSTANT` | Prix **instant sell** (`sellPrice` Cofl / 160) |

## Position du HUD

| Commande | Description |
| --- | --- |
| `/fprofit move` | Ouvre l’éditeur : glisser le HUD, flèches (Shift = 10 px), Échap / Terminé |
| `/fprofit move <x> <y>` | Place le HUD aux coordonnées données |
| `/fprofit move reset` | Remet le HUD à `x=8` `y=48` |

Exemples :

```
/fprofit move
/fprofit move 20 80
/fprofit move reset
```

## Visitor's Logbook

Dans **Visitor's Logbook**, un overlay à droite du menu affiche le total **Unique served** (visiteurs avec au moins 1 offre acceptée) et le total d’offres acceptées. Parcours toutes les pages pour le chiffre complet.

## Hitbox des crops

La hitbox de **visée / clic** (pas la collision) :

- **Mature** : cube 1×1×1 (blé, carottes, pommes de terre, nether wart, cacao, champignons)
- **Pas encore poussé** : hitbox la plus basse (stade 0), pour ne pas casser les plants jeunes

| Commande | Description |
| --- | --- |
| `/fprofit hitbox` | Active ou désactive (défaut : activé) |

## Loadout Pest (canne à pêche)

Clic droit avec une **canne à pêche** :

- pas en Mode Pest → `/loadout` puis left-click sur **Pest** (bandeau arc-en-ciel)
- déjà en Mode Pest → `/loadout` puis left-click sur **Farm** (le bandeau disparaît)

| Commande | Description |
| --- | --- |
| `/fprofit pest` | Toggle Pest / Farm (comme la canne) |
| `/fprofit pestalert` | Active ou désactive l’alerte cooldown pest (tab) |
| `/fprofit pestauto` | Auto loadout Pest à 2m50, /setspawn au spawn, Farm 0.5–1s après |
| `/fprofit pickaxe` | Auto ability pioche (clic droit quand le cooldown est à 0) |
| `/fprofit serverpack` | Pack serveur Hypixel en priorité basse (défaut : ON) |

Noms dans `farmingprofit.json` : `pestLoadoutName` (défaut `Pest`), `farmLoadoutName` (défaut `Farm`).

## Alerte cooldown Pest (tab)

Le widget **Pests** du tab Hypixel affiche un chrono `Cooldown: 1m 58s`. À **2m50** restantes, un gros titre s’affiche avec un **compte à rebours de 5 secondes** et un son.

Le widget Pests doit être activé : `/widget` → Pests.

| Commande | Description |
| --- | --- |
| `/fprofit pestalert` | Active ou désactive l’alerte (défaut : ON) |

Réglages dans `farmingprofit.json` : `pestCooldownAlertAtSeconds` (défaut `170` = 2m50), `pestCooldownAlertCountdown` (défaut `5`).

À **2m50**, si `autoPestLoadout` est ON, le mod équipe le loadout **Pest**. Au spawn, il envoie `/setspawn`, puis **0,5 à 1 s après** il remet **Farm**.

| Commande | Description |
| --- | --- |
| `/fprofit pestauto` | Active ou désactive le switch auto (défaut : ON) |

## Auto ability pioche (mining)

Quand le cooldown de l’ability pioche arrive à **0** (overlay item / chat *You used your … Pickaxe Ability!*), le mod fait **un clic droit** si une **pioche / drill** SkyBlock est en main. Pas de clic au Garden, ni dans un menu.

| Commande | Description |
| --- | --- |
| `/fprofit pickaxe` | Active ou désactive (défaut : ON) |

## Pack serveur Hypixel

Hypixel force le pack **World Specific Resources Hypixel Skyblock**. Le mod l’accepte (obligatoire pour jouer) et le laisse chargé pour les textures d’items SkyBlock, mais le place **tout en dernier** : le pack vanilla Minecraft et tes packs perso passent devant.

| Commande | Description |
| --- | --- |
| `/fprofit serverpack` | Active ou désactive la priorité basse (défaut : ON) |

## Vente NPC (sacks + cookie menu)

Vide le sack, ouvre `/boostercookiemenu`, puis **middle-click** uniquement l’item indiqué (100 ms entre chaque slot). Recheck l’inventaire à la fin de chaque passe.

Un **Booster Cookie** actif est nécessaire.

| Commande | Description |
| --- | --- |
| `/fprofit sell <item>` | 1 tour : `/gfs <item> 9999` → menu cookie → vente |
| `/fprofit sell <item> <fois>` | Répète le cycle `<fois>` fois (1–999) |
| `/fprofit sell cancel` | Annule la vente en cours |

Stop automatique si **3 tours d’affilée** ne vendent rien.

Exemples :

```
/fprofit sell enchanted_wheat
/fprofit sell enchanted_wheat 10
/fprofit sell enchanted wheat 5
/fprofit sell cancel
```

Seul l’item nommé est vendu. Les autres slots de l’inventaire ne sont pas cliqués.

## Mises à jour

Au login (si internet), le mod compare sa version à https://github.com/matteorlt/farming_mod/releases. S’il y a une update, un bouton **[Installer]** apparaît dans le chat : ça télécharge le JAR dans `mods/`, ferme Minecraft, puis un script remplace l’ancien fichier. Il suffit de **relancer le jeu**.

Windows verrouille le JAR tant que Minecraft tourne : d’où la fermeture automatique (comme libautoupdate / ModUpdater).

| Commande | Description |
| --- | --- |
| `/fprofit update` | Relance la vérif GitHub |
| `/fprofit update install` | Installe la dernière release et ferme le jeu |

Désactiver : `"checkUpdates": false` dans `farmingprofit.json`.

## Config

Les réglages (HUD, position, mode de prix, hitbox crops) sont sauvés dans :

`.minecraft/config/farmingprofit.json`
