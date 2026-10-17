package dev.mcextra.mixin;

import dev.mcextra.ClutchHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Пока идёт клач, повороты камеры мышью игнорируются. */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
	// require = 0: если метод когда-нибудь изменится, мод не упадёт, а просто не заблокирует мышь
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true, require = 0)
	private void mce$lockTurn(double yRot, double xRot, CallbackInfo ci) {
		if (ClutchHandler.isLocked() && (Object) this instanceof LocalPlayer) {
			ci.cancel();
		}
	}
}
