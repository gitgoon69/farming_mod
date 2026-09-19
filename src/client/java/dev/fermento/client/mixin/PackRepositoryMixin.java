package dev.fermento.client.mixin;

import java.util.Collection;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.fermento.client.pack.ServerPackHider;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
	@Inject(method = "rebuildSelected", at = @At("RETURN"), cancellable = true)
	private void fermento$serverPackLowestPriority(Collection<String> selectedNames, CallbackInfoReturnable<List<Pack>> cir) {
		if (!ServerPackHider.enabled()) {
			return;
		}
		List<Pack> reordered = ServerPackHider.withServerPacksLowest(cir.getReturnValue());
		if (reordered != null) {
			cir.setReturnValue(reordered);
		}
	}
}
