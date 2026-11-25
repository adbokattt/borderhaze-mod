package dev.borderhaze.glitch.mixin;

import dev.borderhaze.glitch.GlitchManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GLITCH has priority over Vanish (vanilla spectator-style vanish).
 *
 * In vanilla a player in spectator mode is not tracked by players who are not spectators: the server never sends
 * the entity to their clients, so they could not see it however it is drawn. ServerPlayer#broadcastToPlayer is the
 * question "may this viewer see me?" asked by the entity tracker. While GLITCH is active we always answer yes.
 * Nothing about the player's game mode or Vanish state is changed: when GLITCH ends the original rule applies again.
 *
 * Lives in a NON-required mixin config: if a future version renames the method, the game still starts and
 * only this priority rule is lost (see GlitchApi for Vanish systems with their own tracking logic).
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

	@Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true, require = 0)
	private void borderhaze$glitchOverridesVanish(ServerPlayer viewer, CallbackInfoReturnable<Boolean> cir) {
		if (GlitchManager.isGlitched(((ServerPlayer) (Object) this).getUUID())) {
			cir.setReturnValue(true);
		}
	}
}
