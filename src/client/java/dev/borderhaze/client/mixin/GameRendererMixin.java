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
 * Hook point for the SCREEN-EDGE blur: the very end of GameRenderer.render(DeltaTracker, boolean), after the world,
 * the hand and the GUI have been drawn into the main target. Field/method names verified from the 26.2 dump.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

	@Shadow
	@Final
	private CrossFrameResourcePool resourcePool;

	@Shadow
	public abstract RenderTarget mainRenderTarget();

	@Inject(method = "render", at = @At("RETURN"), require = 0)
	private void borderhaze$afterFrame(CallbackInfo ci) {
		BorderEffect.renderEdge(this.mainRenderTarget(), this.resourcePool);
	}
}
