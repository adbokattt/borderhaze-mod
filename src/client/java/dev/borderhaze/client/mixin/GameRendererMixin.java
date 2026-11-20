package dev.borderhaze.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import dev.borderhaze.client.render.BorderEffect;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hook point for the border post-effect: runs right after the 3D world has been rendered
 * (and before the GUI is drawn), the same stage vanilla uses for its own post effects.
 * Field/method names were verified from the 26.2 diagnostics dump.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

	@Shadow
	@Final
	private CrossFrameResourcePool resourcePool;

	@Shadow
	public abstract RenderTarget mainRenderTarget();

	@Inject(method = "renderLevel", at = @At("RETURN"), require = 0)
	private void borderhaze$afterRenderLevel(CallbackInfo ci) {
		BorderEffect.render(this.mainRenderTarget(), this.resourcePool);
	}
}
