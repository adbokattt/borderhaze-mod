package dev.borderhaze.client.debug;

import dev.borderhaze.BorderHaze;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Temporary helper (steps 2-4): writes the real member names of the render classes into
 * <game dir>/logs/borderhaze-diag.txt so the next steps can be written against the real
 * 26.x code instead of guesses. Pure reflection with initialize=false: no side effects.
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

			// --- world border (data side) ---
			dump(out, "net.minecraft.world.level.border.WorldBorder", ALL);
			dump(out, "net.minecraft.world.level.Level", anyOf("border"));
			dump(out, "net.minecraft.client.multiplayer.ClientLevel", anyOf("border"));

			// --- world border (render side, already hidden by the mixin) ---
			dump(out, "net.minecraft.client.renderer.WorldBorderRenderer", ALL);
			dump(out, "net.minecraft.client.renderer.state.WorldBorderRenderState", ALL);

			// --- post-effect system (needed for the blur/ripple pass) ---
			dump(out, "net.minecraft.client.renderer.PostChain", ALL);
			dump(out, "net.minecraft.client.renderer.PostPass", ALL);
			dump(out, "net.minecraft.client.renderer.PostChainConfig", ALL);
			dump(out, "net.minecraft.client.renderer.ShaderManager", ALL);
			dump(out, "net.minecraft.client.renderer.LevelTargetBundle", ALL);
			dump(out, "com.mojang.blaze3d.framegraph.FrameGraphBuilder", ALL);

			// --- where the level is rendered / where post effects are applied ---
			dump(out, "net.minecraft.client.renderer.LevelRenderer", anyOf("post", "effect", "border", "target", "render(", "renderlevel"));
			dump(out, "net.minecraft.client.renderer.GameRenderer", anyOf("post", "effect", "target", "blur", "process", "shader", "render"));
			dump(out, "net.minecraft.client.renderer.extract.LevelExtractor", anyOf("border"));
			dump(out, "net.minecraft.client.renderer.state.level.LevelRenderState", anyOf("border", "camera"));
			dump(out, "net.minecraft.client.renderer.state.level.CameraRenderState", ALL);
			dump(out, "net.minecraft.client.Minecraft", anyOf("target", "post", "border", "levelextractor", "levelrenderer", "gamerenderer", "shadermanager"));

			// --- GPU API used to feed a custom uniform / draw a pass ---
			dump(out, "com.mojang.blaze3d.systems.RenderPass", ALL);
			dump(out, "com.mojang.blaze3d.pipeline.RenderTarget", ALL);
		} catch (Throwable t) {
			out.add("DIAGNOSTICS FAILED: " + t);
		}

		try {
			Path file = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("borderhaze-diag.txt");
			Files.createDirectories(file.getParent());
			Files.write(file, out);
			BorderHaze.LOGGER.info("[Border Haze] diagnostics written to {} ({} lines)", file, out.size());
		} catch (Throwable t) {
			BorderHaze.LOGGER.warn("[Border Haze] could not write diagnostics file: {}", t.toString());
		}
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
}
