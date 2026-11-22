package dev.borderhaze.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.borderhaze.BorderHaze;
import dev.borderhaze.client.border.BorderSnapshot;
import dev.borderhaze.client.config.HazeConfig;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Feeds the per-frame data (camera matrices, border bounds, effect settings) to the shader.
 *
 * The JSON of the post effect declares a uniform block "BorderConfig" (7 x vec4 = 112 bytes).
 * The game creates a GPU buffer for it when it loads the chain (PostPass.customUniforms).
 * Every frame we find that buffer and overwrite its contents with fresh values.
 * (Same technique other mods use to drive vanilla post shaders; names verified from the 26.2 dump.)
 *
 * Layout (std140, must match border_haze.fsh):
 *   vec4 InvViewProj0..3   inverse(projection * view), columns. Maps screen+depth to camera-relative world position
 *   vec4 Bounds            minX, maxX, minZ, maxZ of the border, relative to the camera (float precision stays good)
 *   vec4 Params            x time(s), y falloff(blocks), z debug(0/1), w reverse-Z flag (1 = reverse-Z, sky depth 0; 0 = forward, sky depth 1)
 *   vec4 Effect            x blur radius(px), y ripple amplitude, z ripple speed, w ripple scale
 */
public final class BorderUniforms {
	private static final String BLOCK_NAME = "BorderConfig";
	private static final int SIZE = 7 * 16;
	private static final long START_NANOS = System.nanoTime();

	private static Field passesField;
	private static Field customUniformsField;

	private static PostChain cachedChain;
	private static GpuBuffer cachedBuffer;
	private static GpuBuffer ownBuffer;
	private static boolean loggedFirstWrite;

	private BorderUniforms() {
	}

	/** Finds (and caches) the writable GPU buffer of our uniform block inside the loaded chain. */
	@SuppressWarnings("unchecked")
	public static GpuBuffer resolve(PostChain chain) throws Exception {
		if (chain == cachedChain && cachedBuffer != null) {
			return cachedBuffer;
		}
		if (ownBuffer != null) {
			try {
				ownBuffer.close();
			} catch (Throwable ignored) {
			}
			ownBuffer = null;
		}
		cachedChain = chain;
		cachedBuffer = null;

		if (passesField == null) {
			passesField = PostChain.class.getDeclaredField("passes");
			passesField.setAccessible(true);
			customUniformsField = PostPass.class.getDeclaredField("customUniforms");
			customUniformsField.setAccessible(true);
		}

		List<Object> passes = (List<Object>) passesField.get(chain);
		for (Object pass : passes) {
			Map<String, GpuBuffer> map = (Map<String, GpuBuffer>) customUniformsField.get(pass);
			GpuBuffer found = map.get(BLOCK_NAME);
			if (found == null) {
				continue;
			}
			boolean writable = (found.usage() & GpuBuffer.USAGE_COPY_DST) != 0;
			BorderHaze.LOGGER.info("[Border Haze][effect] uniform block '{}' found: size={} usage=0x{} copyDst={}",
					BLOCK_NAME, found.size(), Integer.toHexString(found.usage()), writable);
			if (found.size() != SIZE) {
				throw new IllegalStateException("uniform block size is " + found.size() + " but " + SIZE + " was expected (JSON and Java disagree)");
			}
			if (writable) {
				cachedBuffer = found;
			} else {
				// Vanilla created it without COPY_DST: swap in our own writable buffer of the same size.
				ownBuffer = RenderSystem.getDevice().createBuffer(() -> "Border Haze BorderConfig",
						GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, (long) SIZE);
				try {
					map.put(BLOCK_NAME, ownBuffer);
				} catch (UnsupportedOperationException immutable) {
					Map<String, GpuBuffer> copy = new HashMap<>(map);
					copy.put(BLOCK_NAME, ownBuffer);
					customUniformsField.set(pass, copy);
				}
				BorderHaze.LOGGER.info("[Border Haze][effect] replaced the read-only buffer by our own writable one");
				cachedBuffer = ownBuffer;
			}
			return cachedBuffer;
		}
		throw new IllegalStateException("uniform block '" + BLOCK_NAME + "' not found in any pass (check border_haze.json)");
	}

	/** Packs all values and uploads them to the GPU. Called once per frame. */
	public static void write(GpuBuffer buffer, CameraRenderState cam, Matrix4fc modelView, BorderSnapshot border) {
		Matrix4f proj = cam.projectionMatrix;
		Matrix4f view = new Matrix4f(modelView);
		Matrix4f inv = new Matrix4f(proj).mul(view).invert();

		// Depth convention (verified from the 26.2 log: m22 ~ 0, m32 = near plane):
		// 26.2 renders with REVERSE-Z and an infinite far plane: stored depth = near / distance, sky = 0.
		// In both reverse and classic "zero to one" setups the NDC z equals the stored depth, so the shader
		// can unproject with the inverse matrix directly. Only the "nothing drawn / sky" value differs.
		// Detection: m22 ~ 0 -> reverse-Z; m22 ~ -1 -> classic forward projection.
		float m22 = proj.m22();
		float m32 = proj.m32();
		boolean reverseZ = Math.abs(m22) < 0.01f;

		if (!loggedFirstWrite) {
			loggedFirstWrite = true;
			BorderHaze.LOGGER.info("[Border Haze][effect] projection check: m22={} m32={} -> {}",
					m22, m32, reverseZ ? "REVERSE_Z (depth = near/distance, sky = 0)" : "FORWARD zero-to-one (sky = 1)");
			BorderHaze.LOGGER.info("[Border Haze][effect] modelView param equals cameraState.viewRotationMatrix: {} (differs when view bobbing is on)",
					view.equals(cam.viewRotationMatrix, 1.0e-4f));
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

		bb.flip();
		RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), bb);
	}
}
