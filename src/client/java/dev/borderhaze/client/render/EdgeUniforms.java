package dev.borderhaze.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import dev.borderhaze.client.config.HazeConfig;
import dev.borderhaze.client.core.UniformBinder;
import net.minecraft.client.renderer.PostChain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Per-frame data for the SCREEN-EDGE chain (border_edge.json), which runs at the very end of the frame
 * (after the hand and the whole GUI), so it blurs everything the player sees.
 *
 * Block "EdgeConfig" (std140, must match border_edge.fsh and border_edge.json), 2 x vec4 = 32 bytes:
 *   vec4 Edge   x proximity of the player to the border (0 = far, 1 = at/over the border),
 *               y blur radius of the first blur pair(px), z radius of the second pair(px), w haze-wash strength
 *   vec4 Haze   rgb haze colour, w unused
 */
public final class EdgeUniforms {
	private static final int SIZE = 2 * 16;
	private static final UniformBinder BINDER = new UniformBinder("EdgeConfig", SIZE);

	private EdgeUniforms() {
	}

	public static void update(PostChain chain, float proximity) throws Exception {
		List<GpuBuffer> buffers = BINDER.resolve(chain);

		ByteBuffer bb = ByteBuffer.allocateDirect(SIZE).order(ByteOrder.nativeOrder());
		bb.putFloat(proximity);
		bb.putFloat(HazeConfig.edgeBlur);
		bb.putFloat(HazeConfig.edgeBlur * 0.7f);
		bb.putFloat(HazeConfig.edgeHaze);

		int rgb = HazeConfig.hazeColor;
		bb.putFloat(((rgb >> 16) & 0xFF) / 255.0f);
		bb.putFloat(((rgb >> 8) & 0xFF) / 255.0f);
		bb.putFloat((rgb & 0xFF) / 255.0f);
		bb.putFloat(0.0f);

		bb.flip();
		BINDER.upload(buffers, bb);
	}
}
