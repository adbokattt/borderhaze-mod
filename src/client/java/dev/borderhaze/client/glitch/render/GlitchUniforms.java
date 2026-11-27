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
 * Block "GlitchConfig" (std140, must match glitch.fsh and glitch.json), 29 x vec4 = 464 bytes:
 *   vec4 Time        x time(s), y reverse-Z flag, z number of figures, w unused
 *   vec4 Rects[8]    screen rectangle of each figure (minU, minV, maxU, maxV)
 *   vec4 EyePos[16]  eye (u, v, depth, radius) - eye 2*i and 2*i+1 belong to figure i
 *   vec4 EyeInt[4]   the 16 eye brightnesses packed four per vec4
 */
final class GlitchUniforms {
	static final int MAX_FIGURES = 8;
	private static final int SIZE = 29 * 16;
	private static final UniformBinder BINDER = new UniformBinder("GlitchConfig", SIZE);
	private static final long START_NANOS = System.nanoTime();

	private GlitchUniforms() {
	}

	static void update(PostChain chain, boolean reverseZ, List<GlitchFigure> figures) throws Exception {
		List<GpuBuffer> buffers = BINDER.resolve(chain);
		ByteBuffer bb = ByteBuffer.allocateDirect(SIZE).order(ByteOrder.nativeOrder());

		int count = Math.min(figures.size(), MAX_FIGURES);
		float time = (float) (((System.nanoTime() - START_NANOS) / 1.0e9) % 10000.0);
		bb.putFloat(time).putFloat(reverseZ ? 1.0f : 0.0f).putFloat(count).putFloat(0.0f);

		for (int i = 0; i < MAX_FIGURES; i++) {
			if (i < count) {
				float[] r = figures.get(i).rect;
				bb.putFloat(r[0]).putFloat(r[1]).putFloat(r[2]).putFloat(r[3]);
			} else {
				bb.putFloat(0).putFloat(0).putFloat(0).putFloat(0);
			}
		}
		for (int i = 0; i < MAX_FIGURES * 2; i++) {
			int fig = i / 2;
			if (fig < count) {
				float[] e = figures.get(fig).eyes[i % 2];
				bb.putFloat(e[0]).putFloat(e[1]).putFloat(e[2]).putFloat(e[3]);
			} else {
				bb.putFloat(0).putFloat(0).putFloat(0).putFloat(0);
			}
		}
		for (int i = 0; i < MAX_FIGURES * 2; i++) {
			int fig = i / 2;
			bb.putFloat(fig < count ? figures.get(fig).eyeLevel[i % 2] : 0.0f);
		}

		bb.flip();
		BINDER.upload(buffers, bb);
	}
}
