package dev.borderhaze.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.borderhaze.BorderHaze;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the GPU buffers of ONE named uniform block inside a loaded post chain (one buffer per pass that
 * declares the block) and uploads fresh bytes into all of them every frame.
 *
 * The game creates these buffers from the JSON of the chain (PostPass.customUniforms: Map&lt;String, GpuBuffer&gt;).
 * If a buffer was created read-only (no COPY_DST usage) it is swapped for our own writable buffer of the same size.
 * Field names verified from the 26.2 diagnostics dump.
 */
final class UniformBinder {
	private static Field passesField;
	private static Field customUniformsField;

	private final String blockName;
	private final int size;

	private PostChain chain;
	private List<GpuBuffer> buffers;
	private final List<GpuBuffer> own = new ArrayList<>();

	UniformBinder(String blockName, int size) {
		this.blockName = blockName;
		this.size = size;
	}

	@SuppressWarnings("unchecked")
	List<GpuBuffer> resolve(PostChain current) throws Exception {
		if (current == chain && buffers != null) {
			return buffers;
		}
		for (GpuBuffer old : own) {
			try {
				old.close();
			} catch (Throwable ignored) {
			}
		}
		own.clear();
		chain = current;
		buffers = null;

		if (passesField == null) {
			passesField = PostChain.class.getDeclaredField("passes");
			passesField.setAccessible(true);
			customUniformsField = PostPass.class.getDeclaredField("customUniforms");
			customUniformsField.setAccessible(true);
		}

		List<GpuBuffer> result = new ArrayList<>();
		List<Object> passes = (List<Object>) passesField.get(current);
		for (Object pass : passes) {
			Map<String, GpuBuffer> map = (Map<String, GpuBuffer>) customUniformsField.get(pass);
			GpuBuffer found = map.get(blockName);
			if (found == null) {
				continue;
			}
			if (found.size() != size) {
				throw new IllegalStateException("uniform block '" + blockName + "' size is " + found.size() + " but " + size + " was expected (JSON and Java disagree)");
			}
			if ((found.usage() & GpuBuffer.USAGE_COPY_DST) != 0) {
				result.add(found);
			} else {
				GpuBuffer replacement = RenderSystem.getDevice().createBuffer(() -> "Border Haze " + blockName,
						GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, (long) size);
				own.add(replacement);
				try {
					map.put(blockName, replacement);
				} catch (UnsupportedOperationException immutable) {
					Map<String, GpuBuffer> copy = new HashMap<>(map);
					copy.put(blockName, replacement);
					customUniformsField.set(pass, copy);
				}
				result.add(replacement);
			}
		}
		if (result.isEmpty()) {
			throw new IllegalStateException("uniform block '" + blockName + "' not found in any pass (check the chain JSON)");
		}
		BorderHaze.LOGGER.info("[Border Haze][effect] uniform block '{}' bound in {} pass(es), {} own writable buffer(s)", blockName, result.size(), own.size());
		buffers = result;
		return result;
	}

	/** Uploads the bytes (position 0, limit = size) into every bound buffer. */
	void upload(List<GpuBuffer> targets, ByteBuffer bytes) {
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		for (GpuBuffer target : targets) {
			encoder.writeToBuffer(target.slice(), bytes.duplicate());
		}
	}
}
