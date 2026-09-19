package dev.fermento.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import dev.fermento.client.pack.ServerPackHider;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.Pack;

@Mixin(DownloadedPackSource.class)
public abstract class DownloadedPackSourceMixin {
	@ModifyArg(
			method = "loadRequestedPacks",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/packs/repository/Pack;<init>(Lnet/minecraft/server/packs/PackLocationInfo;Lnet/minecraft/server/packs/repository/Pack$ResourcesSupplier;Lnet/minecraft/server/packs/repository/Pack$Metadata;Lnet/minecraft/server/packs/PackSelectionConfig;)V"
			),
			index = 3
	)
	private PackSelectionConfig fermento$lowestPriority(PackSelectionConfig original) {
		if (!ServerPackHider.enabled()) {
			return original;
		}
		return new PackSelectionConfig(true, Pack.Position.BOTTOM, true);
	}
}
