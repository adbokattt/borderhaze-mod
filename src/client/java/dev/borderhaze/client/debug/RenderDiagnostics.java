package dev.borderhaze.client.debug;

import dev.borderhaze.BorderHaze;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Temporary helper (steps 2-4): writes real class members and vanilla shader sources into
 * <game dir>/logs/ so the next steps are written against the real 26.x code instead of guesses.
 *   borderhaze-diag.txt       - class/method signatures (reflection, initialize=false, no side effects)
 *   borderhaze-resources.txt  - vanilla post_effect JSONs and post/include shaders as shipped in the game
 * Disable with the JVM argument -Dborderhaze.debug=false
 */
public final class RenderDiagnostics {
	private RenderDiagnostics() {
	}

	public static boolean enabled() {
		return !"false".equalsIgnoreCase(System.getProperty("borderhaze.debug", "true"));
	}

	private static final Predicate<String> ALL = s -> true;

	private static Predicate<String> anyOf(String... keywords) {
		return s -> {
			String l = s.toLowerCase(Locale.ROOT);
			for (String k : keywords) {
				if (l.contains(k)) {
					return true;
				}
			}
			return false;
		};
	}

	// ------------------------------------------------------------------ class signatures

	public static void dump() {
		if (!enabled()) {
			return;
		}
		List<String> out = new ArrayList<>();
		try {
			String mc = FabricLoader.getInstance().getModContainer("minecraft")
					.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
			out.add("Border Haze diagnostics | Minecraft " + mc + " | java " + System.getProperty("java.version"));
			out.add("");

			dump(out, "net.minecraft.world.level.border.WorldBorder", anyOf("getmin", "getmax", "getsize", "getcenter", "lerp"));

			// post-effect system
			dump(out, "net.minecraft.client.renderer.PostChain", ALL);
			dump(out, "net.minecraft.client.renderer.PostChain$TargetBundle", ALL);
			dump(out, "net.minecraft.client.renderer.PostPass", ALL);
			dump(out, "net.minecraft.client.renderer.PostPass$Input", ALL);
			dump(out, "net.minecraft.client.renderer.PostChainConfig$Pass", ALL);
			dump(out, "net.minecraft.client.renderer.PostChainConfig$Uniform", ALL);
			dump(out, "net.minecraft.client.renderer.PostChainConfig$Input", ALL);
			dump(out, "net.minecraft.client.renderer.PostChainConfig$InternalTarget", ALL);
			dump(out, "net.minecraft.client.renderer.Projection", ALL);
			dump(out, "net.minecraft.client.renderer.GlobalSettingsUniform", ALL);
			dump(out, "net.minecraft.client.renderer.RenderPipelines", anyOf("post", "blit", "screen"));

			// GPU API (needed to rewrite per-frame uniform buffers)
			dump(out, "com.mojang.blaze3d.buffers.GpuBuffer", ALL);
			dump(out, "com.mojang.blaze3d.buffers.GpuBufferSlice", ALL);
			dump(out, "com.mojang.blaze3d.systems.CommandEncoder", ALL);
			dump(out, "com.mojang.blaze3d.systems.GpuDevice", ALL);
			dump(out, "com.mojang.blaze3d.systems.RenderSystem", anyOf("device", "encoder", "uniform", "dynamic", "projection", "default"));

			// camera data
			dump(out, "net.minecraft.client.renderer.state.level.CameraRenderState", ALL);
			dump(out, "net.minecraft.client.renderer.GameRenderer", anyOf("camera", "projection", "levelprojection", "fov"));
			dump(out, "net.minecraft.client.Options", anyOf("renderdistance", "effectiverender"));
		} catch (Throwable t) {
			out.add("DIAGNOSTICS FAILED: " + t);
		}
		write(out, "borderhaze-diag.txt");
	}

	private static void dump(List<String> out, String className, Predicate<String> memberFilter) {
		try {
			Class<?> c = Class.forName(className, false, RenderDiagnostics.class.getClassLoader());
			out.add("==== " + c.toGenericString() + (c.getSuperclass() == null ? "" : "  extends " + c.getSuperclass().getName())
					+ (c.getInterfaces().length == 0 ? "" : "  implements " + Arrays.toString(Arrays.stream(c.getInterfaces()).map(Class::getName).toArray())));
			for (Constructor<?> k : c.getDeclaredConstructors()) {
				if (memberFilter.test(k.toString())) {
					out.add("  ctor   " + k);
				}
			}
			Arrays.stream(c.getDeclaredFields()).filter(f -> memberFilter.test(f.toString()))
					.sorted(Comparator.comparing(Field::getName))
					.forEach(f -> out.add("  field  " + f));
			Arrays.stream(c.getDeclaredMethods()).filter(m -> memberFilter.test(m.toString()))
					.sorted(Comparator.comparing(Method::getName))
					.forEach(m -> out.add("  method " + m));
			out.add("");
		} catch (Throwable t) {
			out.add("==== " + className + "  NOT AVAILABLE: " + t);
			out.add("");
		}
	}

	// ------------------------------------------------------------------ vanilla resources

	/** Called once the game and its resource packs are fully loaded (CLIENT_STARTED). */
	public static void dumpResources(Minecraft mc) {
		if (!enabled()) {
			return;
		}
		List<String> out = new ArrayList<>();
		try {
			var resourceManager = mc.getResourceManager();
			out.add("Vanilla resources (namespace 'minecraft') as loaded by the game");
			out.add("");

			// 1) every shader file name, so we know what exists
			out.add("#### FILE LIST: shaders/**");
			var shaders = resourceManager.listResources("shaders", id -> id.getNamespace().equals("minecraft"));
			shaders.keySet().stream().map(Object::toString).sorted().forEach(s -> out.add("  " + s));
			out.add("");

			// 2) full text of the post-processing related files
			dumpContents(out, resourceManager, "post_effect", p -> true);
			dumpContents(out, resourceManager, "shaders", p -> p.startsWith("shaders/post/") || p.startsWith("shaders/include/")
					|| p.contains("screenquad") || p.contains("blit"));
		} catch (Throwable t) {
			out.add("RESOURCE DUMP FAILED: " + t);
		}
		write(out, "borderhaze-resources.txt");
	}

	private static void dumpContents(List<String> out, net.minecraft.server.packs.resources.ResourceManager rm,
									 String root, Predicate<String> pathFilter) {
		var found = rm.listResources(root, id -> id.getNamespace().equals("minecraft") && pathFilter.test(id.getPath()));
		found.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey(Comparator.comparing(Object::toString))).forEach(e -> {
			out.add("#### " + e.getKey());
			try (var in = e.getValue().open()) {
				out.add(new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r", ""));
			} catch (Throwable t) {
				out.add("  (could not read: " + t + ")");
			}
			out.add("");
		});
	}

	// ------------------------------------------------------------------ output

	private static void write(List<String> lines, String fileName) {
		try {
			Path file = FabricLoader.getInstance().getGameDir().resolve("logs").resolve(fileName);
			Files.createDirectories(file.getParent());
			Files.write(file, lines);
			BorderHaze.LOGGER.info("[Border Haze] wrote {} ({} lines)", file, lines.size());
		} catch (Throwable t) {
			BorderHaze.LOGGER.warn("[Border Haze] could not write {}: {}", fileName, t.toString());
		}
	}
}
