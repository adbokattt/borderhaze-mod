package dev.borderhaze.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.borderhaze.BorderHaze;
import dev.borderhaze.client.BorderHazeClient;
import dev.borderhaze.client.border.BorderSnapshot;
import dev.borderhaze.client.border.BorderTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

/**
 * Runs the border post-effect chain (assets/borderhaze/post_effect/border_haze.json).
 *
 * STEP 4a (this version): TEST PASS. The whole screen gets a red tint whenever the world border
 * is close enough to be visible. It verifies, in this order:
 *   1. our hook runs in the real render loop,
 *   2. the post chain JSON + shader load and compile on 26.2,
 *   3. the "skip when the border is irrelevant" logic works (no tint = zero GPU cost).
 * The next steps replace the tint by the real mask / blur / ripple.
 *
 * Safety: any problem disables the effect for the rest of the session (logged once),
 * so a broken shader can never spam the log or crash the game.
 */
public final class BorderEffect {
	private static final Identifier CHAIN_ID = Identifier.fromNamespaceAndPath(BorderHaze.MOD_ID, "border_haze");

	private static boolean failed = false;
	private static boolean loggedFirstRun = false;

	private BorderEffect() {
	}

	public static void render(RenderTarget mainTarget, GraphicsResourceAllocator allocator) {
		if (failed || !BorderHazeClient.isEffectEnabled()) {
			return;
		}
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null || mc.player == null) {
				return;
			}

			BorderSnapshot border = BorderTracker.read(mc);
			double viewBlocks = mc.options.renderDistance().get() * 16.0;
			if (!border.isRelevantFor(mc.player.getX(), mc.player.getZ(), viewBlocks)) {
				return; // border far away or infinite: no GPU work at all
			}

			PostChain chain = mc.getShaderManager().getPostChain(CHAIN_ID, LevelTargetBundle.MAIN_TARGETS);
			if (chain == null) {
				disable("post chain '" + CHAIN_ID + "' could not be loaded (look for shader/resource errors earlier in the log)", null);
				return;
			}

			chain.process(mainTarget, allocator);

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
