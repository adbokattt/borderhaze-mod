package dev.borderhaze.client.mixin;

import dev.borderhaze.BorderHaze;
import dev.borderhaze.client.BorderHazeClient;
import net.minecraft.client.renderer.WorldBorderRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the vanilla world border visuals (the animated blue/red striped wall).
 *
 * The target is selected by method NAME ("render") on purpose: in 26.2 the rendering code was
 * heavily refactored and the exact descriptor may differ from older versions. The handler only
 * takes CallbackInfo, which Mixin allows for any target arguments.
 *
 * require = 0: if the method is not found, the game still starts (the border simply stays visible)
 * and the diagnostic dump in the log tells us the real method names.
 */
@Mixin(WorldBorderRenderer.class)
public abstract class WorldBorderRendererMixin {

	@Unique
	private static boolean borderhaze$loggedFirstCancel = false;

	@Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
	private void borderhaze$hideVanillaBorder(CallbackInfo ci) {
		if (BorderHazeClient.isVanillaBorderHidden()) {
			if (!borderhaze$loggedFirstCancel) {
				borderhaze$loggedFirstCancel = true;
				BorderHaze.LOGGER.info("[Border Haze] vanilla border rendering suppressed (mixin is active)");
			}
			ci.cancel();
		}
	}
}
