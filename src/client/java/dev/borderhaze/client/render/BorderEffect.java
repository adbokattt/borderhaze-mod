package dev.borderhaze.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.borderhaze.BorderHaze;
import dev.borderhaze.client.BorderHazeClient;
import dev.borderhaze.client.border.BorderSnapshot;
import dev.borderhaze.client.border.BorderTracker;
import dev.borderhaze.client.config.HazeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;

import java.util.List;

/**
 * Runs the border post-effect chain (assets/borderhaze/post_effect/border_haze.json) once per frame,
 * right after the 3D world has been drawn.
 *
 * Performance: when the border is infinite or too far to be seen, this method returns after a few
 * float comparisons and nothing is sent to the GPU.
 *
 * Safety: any problem disables the effect for the rest of the session (logged once), so a broken
 * shader or API mismatch can never spam the log or crash the game.
 */
public final class BorderEffect {
	private static final Identifier CHAIN_ID = Identifier.fromNamespaceAndPath(BorderHaze.MOD_ID, "border_haze");

	private static boolean failed = false;
	private static boolean loggedFirstRun = false;

	private BorderEffect() {
	}

	public static void render(GraphicsResourceAllocator allocator, CameraRenderState cameraState, Matrix4fc modelView) {
		if (failed || !BorderHazeClient.isEffectEnabled()) {
			return;
		}
		try {
			HazeConfig.tick();
			if (!HazeConfig.enabled) {
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) {
				return;
			}

			BorderSnapshot border = BorderTracker.read(mc);
			double viewBlocks = mc.options.renderDistance().get() * 16.0;
			if (!border.isRelevantFor(cameraState.pos.x, cameraState.pos.z, viewBlocks)) {
				return; // border far away or infinite: no GPU work at all
			}

			PostChain chain = mc.getShaderManager().getPostChain(CHAIN_ID, LevelTargetBundle.MAIN_TARGETS);
			if (chain == null) {
				disable("post chain '" + CHAIN_ID + "' could not be loaded (look for shader/resource errors earlier in the log)", null);
				return;
			}

			List<GpuBuffer> uniforms = BorderUniforms.resolve(chain);
			BorderUniforms.write(uniforms, cameraState, modelView, border);

			chain.process(mc.gameRenderer.mainRenderTarget(), allocator);

			if (!loggedFirstRun) {
				loggedFirstRun = true;
				BorderHaze.LOGGER.info("[Border Haze][effect] post chain '{}' executed successfully (first run)", CHAIN_ID);
			}
		} catch (Throwable t) {
			disable("exception while running the post chain", t);
		}
	}

	private static void disable(String reason, Throwable t) {
		failed = true;
		if (t != null) {
			BorderHaze.LOGGER.error("[Border Haze][effect] DISABLED for this session: {}", reason, t);
		} else {
			BorderHaze.LOGGER.error("[Border Haze][effect] DISABLED for this session: {}", reason);
		}
	}
}
