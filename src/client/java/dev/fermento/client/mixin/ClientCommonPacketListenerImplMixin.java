package dev.fermento.client.mixin;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.fermento.client.pack.ServerPackHider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {
	@Shadow
	@Final
	protected Minecraft minecraft;

	@Inject(method = "handleResourcePackPush", at = @At("HEAD"), cancellable = true)
	private void fermento$acceptHiddenPack(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
		if (!ServerPackHider.shouldHide(packet)) {
			return;
		}
		Minecraft client = this.minecraft;
		client.execute(() -> {
			try {
				URL url = URI.create(packet.url()).toURL();
				client.getDownloadedPackSource().pushPack(packet.id(), url, packet.hash());
				client.getDownloadedPackSource().allowServerPacks();
			} catch (IllegalArgumentException | MalformedURLException ignored) {
			}
		});
		ci.cancel();
	}
}
