package dev.farmingprofit.client;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import dev.farmingprofit.FarmingProfitMod;
import dev.farmingprofit.client.compat.ClientScreens;
import dev.farmingprofit.client.config.ModConfig;
import dev.farmingprofit.client.garden.Crop;
import dev.farmingprofit.client.garden.FarmingTracker;
import dev.farmingprofit.client.garden.GardenDetector;
import dev.farmingprofit.client.garden.PestCooldownTracker;
import dev.farmingprofit.client.garden.VisitorLogbookStats;
import dev.farmingprofit.client.hud.HudMoveScreen;
import dev.farmingprofit.client.hud.PestCooldownAlertHud;
import dev.farmingprofit.client.hud.PestModeHud;
import dev.farmingprofit.client.hud.ProfitHud;
import dev.farmingprofit.client.loadout.PestLoadoutService;
import dev.farmingprofit.client.mining.PickaxeAbilityService;
import dev.farmingprofit.client.prices.CoflBazaarService;
import dev.farmingprofit.client.sell.NpcSellService;
import dev.farmingprofit.client.update.UpdateChecker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

public class FarmingProfitClient implements ClientModInitializer {
	private static ModConfig config;
	private static FarmingTracker tracker;
	private static CoflBazaarService prices;
	private static NpcSellService npcSell;
	private static PestLoadoutService pestLoadout;
	private static PickaxeAbilityService pickaxeAbility;
	private static PestCooldownTracker pestCooldown;
	private static UpdateChecker updates;

	@Override
	public void onInitializeClient() {
		config = ModConfig.load();
		tracker = new FarmingTracker();
		prices = new CoflBazaarService();
		prices.start();
		npcSell = new NpcSellService();
		pestLoadout = new PestLoadoutService(config);
		pickaxeAbility = new PickaxeAbilityService(config);
		pestCooldown = new PestCooldownTracker();
		updates = new UpdateChecker();

		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				FarmingProfitMod.id("profit_hud"),
				(graphics, delta) -> ProfitHud.render(graphics, delta, config, tracker, prices)
		);
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				FarmingProfitMod.id("pest_mode"),
				(graphics, delta) -> PestModeHud.render(graphics, delta, config, pestLoadout)
		);
		HudElementRegistry.addLast(
				FarmingProfitMod.id("pest_cooldown_alert"),
				(graphics, delta) -> PestCooldownAlertHud.render(graphics, delta, config, pestCooldown)
		);

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?>)) {
				return;
			}
			ScreenEvents.afterExtract(screen).register((openScreen, graphics, mouseX, mouseY, tickProgress) -> {
				if (openScreen instanceof AbstractContainerScreen<?> container) {
					VisitorLogbookStats.render(container, graphics);
				}
			});
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			GardenDetector.tick(client);
			tracker.tick(client, config);
			prices.tick();
			npcSell.tick(client);
			VisitorLogbookStats.tick(client);
			pestCooldown.tick(client, config);
			if (pestCooldown.consumeAlertTrigger()) {
				pestLoadout.onPestAlert();
			}
			pestLoadout.tick(client);
			if (!pestLoadout.running() && !npcSell.running()) {
				pickaxeAbility.tick(client);
			}
			updates.tick(client);
		});

		ClientPlayerBlockBreakEvents.AFTER.register((world, player, pos, state) -> {
			Crop crop = Crop.fromBlock(state.getBlock());
			if (crop != null) {
				tracker.onBlockBroken(crop);
			}
		});

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			String text = message.getString();
			if (!overlay) {
				pestLoadout.onChat(text);
			}
			pickaxeAbility.onChat(text);
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (config.checkUpdates) {
				updates.onJoin();
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			GardenDetector.reset();
			tracker.resetSession();
			VisitorLogbookStats.reset();
			pestLoadout.resetSession();
			pickaxeAbility.reset();
			pestCooldown.reset();
			if (npcSell.running()) {
				npcSell.cancel();
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				literal("fprofit")
						.executes(FarmingProfitClient::help)
						.then(literal("reset").executes(ctx -> {
							tracker.resetSession();
							feedback(ctx, "Session reset.");
							return 1;
						}))
						.then(literal("toggle").executes(ctx -> {
							config.hudEnabled = !config.hudEnabled;
							config.save();
							feedback(ctx, config.hudEnabled ? "HUD activé." : "HUD désactivé.");
							return 1;
						}))
						.then(literal("hitbox").executes(ctx -> {
							config.fullCropHitboxes = !config.fullCropHitboxes;
							config.save();
							feedback(ctx, config.fullCropHitboxes
									? "Hitbox crops : 1 bloc si mature, plus basse sinon (cacao inclus)."
									: "Hitbox crops vanilla.");
							return 1;
						}))
						.then(literal("pest").executes(ctx -> {
							if (pestLoadout.running()) {
								pestLoadout.cancel();
								feedback(ctx, "Changement de loadout annulé.");
								return 1;
							}
							if (pestLoadout.pestMode()) {
								feedback(ctx, "Ouverture /loadout → " + config.farmLoadoutName + "...");
							} else {
								feedback(ctx, "Ouverture /loadout → " + config.pestLoadoutName + "...");
							}
							pestLoadout.startFromCommand();
							return 1;
						}))
						.then(literal("pestalert").executes(ctx -> {
							config.pestCooldownAlert = !config.pestCooldownAlert;
							config.save();
							feedback(ctx, config.pestCooldownAlert
									? "Alerte cooldown pest : ON (à 2m50, compte à rebours 5s)."
									: "Alerte cooldown pest : OFF.");
							return 1;
						}))
						.then(literal("pestauto").executes(ctx -> {
							config.autoPestLoadout = !config.autoPestLoadout;
							config.save();
							if (!config.autoPestLoadout) {
								pestLoadout.cancelPendingAuto();
							}
							feedback(ctx, config.autoPestLoadout
									? "Auto loadout : ON (Pest à 2m50, /setspawn au spawn, Farm 0.5–1s après)."
									: "Auto loadout : OFF.");
							return 1;
						}))
						.then(literal("serverpack").executes(ctx -> {
							config.hideServerResourcePack = !config.hideServerResourcePack;
							config.save();
							Minecraft client = ctx.getSource().getClient();
							if (config.hideServerResourcePack) {
								client.reloadResourcePacks();
								feedback(ctx, "Pack serveur Hypixel : tout en dernier (vanilla et tes packs passent devant).");
							} else {
								feedback(ctx, "Pack serveur Hypixel : priorité normale. Reconnecte pour le remettre en haut.");
							}
							return 1;
						}))
						.then(literal("pickaxe").executes(ctx -> {
							config.autoPickaxeAbility = !config.autoPickaxeAbility;
							config.save();
							if (!config.autoPickaxeAbility) {
								pickaxeAbility.reset();
							}
							feedback(ctx, config.autoPickaxeAbility
									? "Auto ability pioche : ON (clic droit quand le cooldown arrive à 0)."
									: "Auto ability pioche : OFF.");
							return 1;
						}))
						.then(literal("update")
								.executes(ctx -> {
									if (!config.checkUpdates) {
										feedback(ctx, "Vérif updates désactivée (checkUpdates dans farmingprofit.json).");
										return 0;
									}
									updates.refreshNow();
									feedback(ctx, "Vérification GitHub…");
									return 1;
								})
								.then(literal("install").executes(ctx -> {
									if (!config.checkUpdates) {
										feedback(ctx, "Vérif updates désactivée (checkUpdates dans farmingprofit.json).");
										return 0;
									}
									updates.installNow();
									return 1;
								})))
						.then(literal("prices").executes(ctx -> {
							prices.refreshNow();
							feedback(ctx, "Rafraîchissement des prix Cofl...");
							return 1;
						}))
						.then(literal("mode")
								.then(argument("type", StringArgumentType.word()).executes(ctx -> {
									String type = StringArgumentType.getString(ctx, "type").toUpperCase();
									if (!type.equals("OFFER") && !type.equals("INSTANT")) {
										feedback(ctx, "Utilise OFFER (sell offer) ou INSTANT (instant sell).");
										return 0;
									}
									config.priceMode = type;
									config.save();
									feedback(ctx, "Mode prix: " + type);
									return 1;
								})))
						.then(literal("move")
								.executes(FarmingProfitClient::openMoveScreen)
								.then(literal("reset").executes(ctx -> {
									config.hudX = 8;
									config.hudY = 48;
									config.save();
									feedback(ctx, "HUD remis à x=8 y=48.");
									return 1;
								}))
								.then(argument("x", IntegerArgumentType.integer(0, 4000))
										.then(argument("y", IntegerArgumentType.integer(0, 4000)).executes(ctx -> {
											config.hudX = IntegerArgumentType.getInteger(ctx, "x");
											config.hudY = IntegerArgumentType.getInteger(ctx, "y");
											config.save();
											feedback(ctx, "HUD déplacé à x=" + config.hudX + " y=" + config.hudY + ".");
											return 1;
										}))))
						.then(literal("sell")
								.then(literal("cancel").executes(ctx -> {
									npcSell.cancel();
									return 1;
								}))
								.then(argument("item", StringArgumentType.greedyString()).executes(ctx -> {
									npcSell.start(StringArgumentType.getString(ctx, "item"));
									return 1;
								})))
						.then(literal("help").executes(FarmingProfitClient::help))
		));

		FarmingProfitMod.LOGGER.info("Farming Profit client prêt. Commande: /fprofit");
	}

	private static int openMoveScreen(CommandContext<FabricClientCommandSource> ctx) {
		Minecraft client = ctx.getSource().getClient();
		client.execute(() -> ClientScreens.set(client, new HudMoveScreen(config, tracker, prices)));
		feedback(ctx, "Glisse le HUD, puis Échap ou Terminé.");
		return 1;
	}

	private static int help(CommandContext<FabricClientCommandSource> ctx) {
		ctx.getSource().sendFeedback(Component.literal("Farming Profit — /fprofit sell <item> [fois] | pest | pestauto | pestalert | pickaxe | serverpack | update [install] | sell cancel | move [x y|reset] | reset | toggle | hitbox | prices | mode <OFFER|INSTANT>").withStyle(ChatFormatting.GOLD));
		return 1;
	}

	public static ModConfig config() {
		return config;
	}

	private static void feedback(CommandContext<FabricClientCommandSource> ctx, String message) {
		ctx.getSource().sendFeedback(Component.literal("[Farming Profit] " + message).withStyle(ChatFormatting.YELLOW));
	}
}
