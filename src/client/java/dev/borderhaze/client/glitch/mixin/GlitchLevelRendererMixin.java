package dev.borderhaze.client.glitch.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.borderhaze.client.glitch.render.GlitchEffect;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the GLITCH post effect right after the whole 3D world has been drawn. Same real 26.2 signature as the BorderHaze hook;
 * this is a separate mixin of the GLITCH mechanic. Lower priority number = applied (and run) first, so the glitch effect
 * is finished before any BorderHaze blur is applied on top of it.
 */
@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class GlitchLevelRendererMixin {

	@Inject(method = "render", at = @At("RETURN"), require = 0)
	private void borderhaze$glitchAfterLevelRender(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
												   boolean renderBlockOutline, CameraRenderState cameraState,
												   Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog,
												   Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
		GlitchEffect.render(allocator, deltaTracker, cameraState, modelViewMatrix);
	}
}
