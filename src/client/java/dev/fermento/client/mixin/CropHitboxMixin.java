package dev.fermento.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.fermento.client.hitbox.CropHitboxes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CarrotBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PotatoBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Mature: 1×1×1 cube. Not mature: lowest hitbox. Cocoa included.
 */
@Mixin(value = {
		CropBlock.class,
		CarrotBlock.class,
		PotatoBlock.class,
		NetherWartBlock.class,
		MushroomBlock.class,
		CocoaBlock.class
})
public abstract class CropHitboxMixin {
	@Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
	private void fermento$cropHitbox(
			BlockState state,
			BlockGetter level,
			BlockPos pos,
			CollisionContext context,
			CallbackInfoReturnable<VoxelShape> cir
	) {
		VoxelShape shape = CropHitboxes.selectionShape(this, state);
		if (shape != null) {
			cir.setReturnValue(shape);
		}
	}
}
