package dev.borderhaze.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
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

/**
 * Runs the two post-effect chains of the mod.
 *
 *  1. WORLD chain (border_haze.json), right after the 3D world is drawn: blur / pixel / ripple / haze on
 *     everything BEYOND the border, using the depth buffer to know where the border is.
 *  2. EDGE chain (border_edge.json), at the very end of the frame, after the hand and the GUI: blurs the
 *     screen edges when the player is near the border. The blurred area grows from the edges towards the
 *     centre as the player gets closer and covers the whole screen at the border. It needs no depth, so it
 *     also covers the hand, the hotbar and open inventories.
 *
 * Performance: when the border is infinite or too far to matter, both methods return after a few float
 * comparisons and nothing is sent to the GPU.
 * Safety: any problem disables that chain for the rest of the session (logged once), so a broken shader or API
 * mismatch can never spam the log or crash the game.
 */
public final class BorderEffect {
	private static final Identifier WORLD_CHAIN_ID = Identifier.fromNamespaceAndPath(BorderHaze.MOD_ID, "border_haze");
	private static final Identifier EDGE_CHAIN_ID = Identifier.fromNamespaceAndPath(BorderHaze.MOD_ID, "border_edge");

	private static boolean worldFailed = false;
	private static boolean edgeFailed = false;
	private static boolean loggedWorldRun = false;
	private static boolean loggedEdgeRun = false;

	private BorderEffect() {
	}

	// ------------------------------------------------------------------ world chain

	public static void renderWorld(GraphicsResourceAllocator allocator, CameraRenderState cameraState, Matrix4fc modelView) {
		if (worldFailed || !BorderHazeClient.isEffectEnabled()) {
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

			PostChain chain = mc.getShaderManager().getPostChain(WORLD_CHAIN_ID, LevelTargetBundle.MAIN_TARGETS);
			if (chain == null) {
				disableWorld("post chain '" + WORLD_CHAIN_ID + "' could not be loaded (look for shader/resource errors earlier in the log)", null);
				return;
			}

			BorderUniforms.update(chain, cameraState, modelView, border);
			chain.process(mc.gameRenderer.mainRenderTarget(), allocator);

			if (!loggedWorldRun) {
				loggedWorldRun = true;
				BorderHaze.LOGGER.info("[Border Haze][effect] world chain '{}' executed successfully (first run)", WORLD_CHAIN_ID);
			}
		} catch (Throwable t) {
			disableWorld("exception while running the world chain", t);
		}
	}

	// ------------------------------------------------------------------ edge chain

	public static void renderEdge(RenderTarget mainTarget, GraphicsResourceAllocator allocator) {
		if (edgeFailed || !BorderHazeClient.isEffectEnabled() || !HazeConfig.enabled || HazeConfig.edgeBlur < 0.5f) {
			return;
		}
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null || mc.player == null) {
				return;
			}
			BorderSnapshot border = BorderTracker.read(mc);
			if (border.isEffectivelyInfinite()) {
				return;
			}

			// proximity: 0 further than edgeDistance from the border, 1 at the border or beyond (smooth in between)
			double dist = border.signedDistance(mc.player.getX(), mc.player.getZ());
			float p = Math.max(0.0f, Math.min(1.0f, 1.0f - (float) (dist / Math.max(HazeConfig.edgeDistance, 0.5f))));
			p = p * p * (3.0f - 2.0f * p);
			if (p < 0.01f) {
				return;
			}

			PostChain chain = mc.getShaderManager().getPostChain(EDGE_CHAIN_ID, LevelTargetBundle.MAIN_TARGETS);
			if (chain == null) {
				disableEdge("post chain '" + EDGE_CHAIN_ID + "' could not be loaded (look for shader/resource errors earlier in the log)", null);
				return;
			}

			EdgeUniforms.update(chain, p);
			chain.process(mainTarget, allocator);

			if (!loggedEdgeRun) {
				loggedEdgeRun = true;
				BorderHaze.LOGGER.info("[Border Haze][effect] edge chain '{}' executed successfully (first run)", EDGE_CHAIN_ID);
			}
		} catch (Throwable t) {
			disableEdge("exception while running the edge chain", t);
		}
	}

	private static void disableWorld(String reason, Throwable t) {
		worldFailed = true;
		log(reason, t, "WORLD chain");
	}

	private static void disableEdge(String reason, Throwable t) {
		edgeFailed = true;
		log(reason, t, "EDGE chain");
	}

	private static void log(String reason, Throwable t, String which) {
		if (t != null) {
			BorderHaze.LOGGER.error("[Border Haze][effect] {} DISABLED for this session: {}", which, reason, t);
		} else {
			BorderHaze.LOGGER.error("[Border Haze][effect] {} DISABLED for this session: {}", which, reason);
		}
	}
}
