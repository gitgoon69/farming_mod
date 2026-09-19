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
│   ├── schema.sql
│   ├── migrations/
│   │   ├── 20260919120000_account_sync.sql
│   │   ├── 20260919130000_account_sync_force_rls.sql
│   │   └── 20260919140000_account_sync_per_uuid.sql
│   └── functions/
│       └── account-sync/
│           └── index.ts
└── src/
    ├── main/
    │   ├── java/dev/fermento/
    │   │   └── FermentoMod.java
    │   └── resources/
    │       ├── fabric.mod.json
    │       └── assets/fermento/icon.png
    └── client/
        ├── java/dev/fermento/client/
        │   ├── FermentoClient.java
        │   ├── account/
        │   │   ├── AccountSwitchService.java
        │   │   ├── AccountSwitchScreen.java
        │   │   ├── GameMenuAccountButton.java
        │   │   ├── PrismAccountSource.java
        │   │   └── AccountSyncManager.java
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
        │       ├── FermentoMixinPlugin.java
        │       ├── CropHitboxMixin.java
        │       ├── PackRepositoryMixin.java
        │       ├── DownloadedPackSourceMixin.java
        │       └── ClientCommonPacketListenerImplMixin.java
        └── resources/
            ├── fermento.client.mixins.json
            └── assets/fermento/lang/
                ├── en_us.json
                └── fr_fr.json
```
