package dev.borderhaze.client.mixin.optional;

import dev.borderhaze.client.BorderHazeClient;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Removes the vanilla red "you are near the world border" warning at the screen edges
 * (the Border Haze edge blur replaces it).
 *
 * Only the border part of the HUD vignette is neutralised: the vignette asks the border how far the camera
 * is from it; we answer "very far", so the warning strength is zero. Everything else the vignette does stays.
 *
 * Target class/method come from the 26.x source: net.minecraft.client.gui.Hud#extractVignette.
 * This mixin lives in its own NON-required mixin config: if a future version renames something,
 * the game still starts and only the red warning would come back.
 */
@Mixin(targets = "net.minecraft.client.gui.Hud")
public abstract class HudVignetteMixin {

	@Redirect(method = "extractVignette",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/level/border/WorldBorder;getDistanceToBorder(Lnet/minecraft/world/entity/Entity;)D"),
			require = 0)
	private double borderhaze$noBorderWarning(WorldBorder border, Entity camera) {
		if (BorderHazeClient.isVanillaBorderHidden()) {
			return 1.0E9;
		}
		return border.getDistanceToBorder(camera);
	}
}
