# Architecture — dossiers / fichiers

```
mod farming/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── COMMANDS.md
├── README.md
    ├── logo.png
    ├── ARCHITECTURE.md
├── versions/
│   ├── 26.1.2.properties
│   └── 26.2.properties
├── .github/
│   └── workflows/
│       └── release.yml
├── supabase/
│   ├── config.toml
│   └── schema.sql
└── src/
    ├── main/
    │   ├── java/dev/stook/
    │   │   └── StookMod.java
    │   └── resources/
    │       ├── fabric.mod.json
    │       └── assets/stook/icon.png
    └── client/
        ├── java/dev/stook/client/
        │   ├── StookClient.java
        │   ├── account/
        │   │   ├── AccountSwitchService.java
        │   │   ├── AccountSwitchScreen.java
        │   │   ├── GameMenuAccountButton.java
        │   │   └── PrismAccountSource.java
        │   ├── config/
        │   │   └── ModConfig.java
        │   ├── garden/
        │   │   ├── Crop.java
        │   │   ├── FarmingTracker.java
        │   │   ├── GardenDetector.java
        │   │   ├── PestCooldownTracker.java
        │   │   ├── SkyblockItems.java
        │   │   ├── TabList.java
        │   │   └── VisitorLogbookStats.java
        │   ├── hud/
        │   │   ├── ProfitHud.java
        │   │   ├── PestModeHud.java
        │   │   ├── PestCooldownAlertHud.java
        │   │   └── HudMoveScreen.java
        │   ├── gui/
        │   │   └── SettingsScreen.java
        │   ├── prices/
        │   │   ├── CoflBazaarService.java
        │   │   └── BazaarQuote.java
        │   ├── loadout/
        │   │   └── PestLoadoutService.java
        │   ├── mining/
        │   │   ├── EndstoneTracker.java
        │   │   ├── MiningFortune.java
        │   │   └── PickaxeAbilityService.java
        │   ├── sell/
        │   │   └── NpcSellService.java
        │   ├── update/
        │   │   ├── UpdateChecker.java
        │   │   └── UpdateInstaller.java
        │   ├── usage/
        │   │   ├── UsagePingService.java
        │   │   └── LauncherDetector.java
        │   ├── pack/
        │   │   └── ServerPackHider.java
        │   ├── hitbox/
        │   │   └── CropHitboxes.java
        │   ├── compat/
        │   │   ├── ClientScreens.java
        │   │   ├── ClientHudHidden.java
        │   │   ├── ClientMissTime.java
        │   │   └── PolarPresence.java
        │   └── mixin/
        │       ├── StookMixinPlugin.java
        │       ├── CropHitboxMixin.java
        │       ├── PackRepositoryMixin.java
        │       ├── DownloadedPackSourceMixin.java
        │       └── ClientCommonPacketListenerImplMixin.java
        └── resources/
            ├── stook.client.mixins.json
            └── assets/stook/lang/
                └── en_us.json
```
