package dev.borderhaze.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import dev.borderhaze.BorderHaze;
import dev.borderhaze.client.border.BorderSnapshot;
import dev.borderhaze.client.config.HazeConfig;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Per-frame data for the WORLD chain (border_haze.json): camera matrices, border bounds, effect settings.
 *
 * Block "BorderConfig" (std140, must match border_haze.fsh and border_haze.json), 9 x vec4 = 144 bytes:
 *   vec4 InvViewProj0..3   inverse(projection * view), columns. Maps screen + depth to a camera-relative world position
 *   vec4 Bounds            minX, maxX, minZ, maxZ of the border, relative to the camera (keeps float precision good)
 *   vec4 Params            x time(s), y falloff(blocks), z debug(0/1), w reverse-Z flag (1 = reverse-Z, sky depth 0)
 *   vec4 Effect            x blur radius(px), y ripple amplitude, z ripple speed, w ripple scale
 *   vec4 Style             x style (0 = blur+ripple, 1 = pixel), y pixel block size(px), z pixel softness, w unused
 *   vec4 Haze              rgb haze colour, w haze strength
 */
public final class BorderUniforms {
	private static final int SIZE = 9 * 16;
	private static final UniformBinder BINDER = new UniformBinder("BorderConfig", SIZE);
	private static final long START_NANOS = System.nanoTime();
	private static boolean loggedFirstWrite;

	private BorderUniforms() {
	}

	public static void update(PostChain chain, CameraRenderState cam, Matrix4fc modelView, BorderSnapshot border) throws Exception {
		List<GpuBuffer> buffers = BINDER.resolve(chain);

		Matrix4f proj = cam.projectionMatrix;
		Matrix4f view = new Matrix4f(modelView);
		Matrix4f inv = new Matrix4f(proj).mul(view).invert();

		// Depth convention (verified from the 26.2 log: m22 ~ 0, m32 = near plane): 26.2 renders with REVERSE-Z and
		// an infinite far plane: stored depth = near / distance, sky = 0. NDC z equals the stored depth in both
		// reverse and classic "zero to one" setups, so the shader unprojects with the inverse matrix directly.
		float m22 = proj.m22();
		float m32 = proj.m32();
		boolean reverseZ = Math.abs(m22) < 0.01f;

		if (!loggedFirstWrite) {
			loggedFirstWrite = true;
			BorderHaze.LOGGER.info("[Border Haze][effect] projection check: m22={} m32={} -> {}",
					m22, m32, reverseZ ? "REVERSE_Z (depth = near/distance, sky = 0)" : "FORWARD zero-to-one (sky = 1)");
		}

		ByteBuffer bb = ByteBuffer.allocateDirect(SIZE).order(ByteOrder.nativeOrder());

		// inverse view-projection, column-major (= std140 mat4 = four vec4 columns)
		bb.putFloat(inv.m00()).putFloat(inv.m01()).putFloat(inv.m02()).putFloat(inv.m03());
		bb.putFloat(inv.m10()).putFloat(inv.m11()).putFloat(inv.m12()).putFloat(inv.m13());
		bb.putFloat(inv.m20()).putFloat(inv.m21()).putFloat(inv.m22()).putFloat(inv.m23());
		bb.putFloat(inv.m30()).putFloat(inv.m31()).putFloat(inv.m32()).putFloat(inv.m33());

		// border bounds relative to the camera
		bb.putFloat((float) (border.minX() - cam.pos.x));
		bb.putFloat((float) (border.maxX() - cam.pos.x));
		bb.putFloat((float) (border.minZ() - cam.pos.z));
		bb.putFloat((float) (border.maxZ() - cam.pos.z));

		// Params
		float time = (float) (((System.nanoTime() - START_NANOS) / 1.0e9) % 10000.0);
		bb.putFloat(time);
		bb.putFloat(HazeConfig.falloff);
		bb.putFloat(HazeConfig.debugMask ? 1.0f : 0.0f);
		bb.putFloat(reverseZ ? 1.0f : 0.0f);

		// Effect
		bb.putFloat(HazeConfig.blurRadius);
		bb.putFloat(HazeConfig.rippleAmplitude);
		bb.putFloat(HazeConfig.rippleSpeed);
		bb.putFloat(HazeConfig.rippleScale);

		// Style
		bb.putFloat(HazeConfig.style);
		bb.putFloat(HazeConfig.pixelSize);
		bb.putFloat(HazeConfig.pixelBlur);
		bb.putFloat(0.0f);

		// Haze (colour + strength)
		int rgb = HazeConfig.hazeColor;
		bb.putFloat(((rgb >> 16) & 0xFF) / 255.0f);
		bb.putFloat(((rgb >> 8) & 0xFF) / 255.0f);
		bb.putFloat((rgb & 0xFF) / 255.0f);
		bb.putFloat(HazeConfig.hazeStrength);

		bb.flip();
		BINDER.upload(buffers, bb);
	}
}
