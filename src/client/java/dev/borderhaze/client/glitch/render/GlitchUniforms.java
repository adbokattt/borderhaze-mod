package dev.borderhaze.client.glitch.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import dev.borderhaze.client.core.UniformBinder;
import net.minecraft.client.renderer.PostChain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Per-frame data for the GLITCH post effect.
 *
 * Block "GlitchConfig" (std140, must match glitch.fsh and glitch.json), 54 x vec4 = 864 bytes:
 *   vec4 Time          x time(s), y reverse-Z flag, z number of figures, w debug flag
 *   vec4 Cam           x near plane, y projection scale (m11)
 *   vec4 FigRect[4]    screen rectangle of each figure
 *   vec4 FigInfo[4]    x view depth of the figure, y seed
 *   vec4 PrimAB[24]    6 capsules per figure: Ax, Ay, Bx, By (uv)
 *   vec4 PrimR[8]      2 vec4 per figure: radii of the 6 capsules (negative = missing)
 *   vec4 EyePos[8]     2 per figure: u, v, depth, radius
 *   vec4 EyeInt[4]     per figure: x, y = brightness of its two eyes
 */
final class GlitchUniforms {
	static final int MAX_FIGURES = 4;
	private static final int SIZE = 54 * 16;
	private static final UniformBinder BINDER = new UniformBinder("GlitchConfig", SIZE);
	private static final long START_NANOS = System.nanoTime();

	private GlitchUniforms() {
	}

	static void update(PostChain chain, boolean reverseZ, boolean debug, float near, float m11, List<GlitchFigure> figures) throws Exception {
		List<GpuBuffer> buffers = BINDER.resolve(chain);
		ByteBuffer bb = ByteBuffer.allocateDirect(SIZE).order(ByteOrder.nativeOrder());

		int count = Math.min(figures.size(), MAX_FIGURES);
		float time = (float) (((System.nanoTime() - START_NANOS) / 1.0e9) % 10000.0);
		bb.putFloat(time).putFloat(reverseZ ? 1.0f : 0.0f).putFloat(count).putFloat(debug ? 1.0f : 0.0f);
		bb.putFloat(near).putFloat(m11).putFloat(0.0f).putFloat(0.0f);

		for (int i = 0; i < MAX_FIGURES; i++) {                       // FigRect[4]
			put4(bb, i < count ? figures.get(i).rect : null);
		}
		for (int i = 0; i < MAX_FIGURES; i++) {                       // FigInfo[4]
			put4(bb, i < count ? figures.get(i).info : null);
		}
		for (int i = 0; i < MAX_FIGURES; i++) {                       // PrimAB[24]
			for (int k = 0; k < GlitchFigure.PRIMS; k++) {
				put4(bb, i < count ? figures.get(i).prims[k] : null);
			}
		}
		for (int i = 0; i < MAX_FIGURES; i++) {                       // PrimR[8]
			for (int half = 0; half < 2; half++) {
				for (int c = 0; c < 4; c++) {
					bb.putFloat(i < count ? figures.get(i).radii[half * 4 + c] : -1.0f);
				}
			}
		}
		for (int i = 0; i < MAX_FIGURES; i++) {                       // EyePos[8]
			for (int e = 0; e < 2; e++) {
				put4(bb, i < count ? figures.get(i).eyes[e] : null);
			}
		}
		for (int i = 0; i < MAX_FIGURES; i++) {                       // EyeInt[4]
			bb.putFloat(i < count ? figures.get(i).eyeLevel[0] : 0.0f)
					.putFloat(i < count ? figures.get(i).eyeLevel[1] : 0.0f)
					.putFloat(0.0f).putFloat(0.0f);
		}

		bb.flip();
		BINDER.upload(buffers, bb);
	}

	private static void put4(ByteBuffer bb, float[] v) {
		if (v == null) {
			bb.putFloat(0).putFloat(0).putFloat(0).putFloat(0);
		} else {
			bb.putFloat(v[0]).putFloat(v[1]).putFloat(v[2]).putFloat(v[3]);
		}
	}
}
