package dev.borderhaze.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.borderhaze.client.render.BorderEffect;
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
 * Hook point: runs the border post-effect right after the whole 3D world (terrain, entities,
 * translucent layers) has been rendered into the main target, and before the hand and the GUI.
 * The parameter list is copied from the real 26.2 signature (verified in the diagnostics dump):
 * render(GraphicsResourceAllocator, DeltaTracker, boolean, CameraRenderState, Matrix4fc, GpuBufferSlice, Vector4f, boolean)
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

	@Inject(method = "render", at = @At("RETURN"), require = 0)
	private void borderhaze$afterLevelRender(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
											 boolean renderBlockOutline, CameraRenderState cameraState,
											 Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog,
											 Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
		BorderEffect.renderWorld(allocator, cameraState, modelViewMatrix);
	}
}
