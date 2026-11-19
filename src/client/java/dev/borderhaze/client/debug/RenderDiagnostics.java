package dev.borderhaze.client.debug;

import dev.borderhaze.BorderHaze;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Temporary helper (steps 2-3): prints the real member names of the render classes into the log,
 * so the next steps can be written against the real 26.2 code instead of guesses.
 * Pure reflection with initialize=false: it does not touch the renderer and has no side effects.
 * Disable with the JVM argument -Dborderhaze.debug=false
 */
public final class RenderDiagnostics {
	private RenderDiagnostics() {
	}

	public static boolean enabled() {
		return !"false".equalsIgnoreCase(System.getProperty("borderhaze.debug", "true"));
	}

	public static void dump() {
		if (!enabled()) {
			return;
		}
		// Full dump: the border renderer itself
		dumpClass("net.minecraft.client.renderer.WorldBorderRenderer", m -> true, f -> true);
		// Render state of the border (if present in this version)
		dumpClass("net.minecraft.client.renderer.state.WorldBorderRenderState", m -> true, f -> true);
		// Only border-related members of the classes that own/call the border renderer
		Predicate<String> border = s -> s.toLowerCase(Locale.ROOT).contains("border");
		dumpClass("net.minecraft.client.renderer.LevelRenderer", m -> border.test(m.toString()), f -> border.test(f.toString()));
		dumpClass("net.minecraft.client.renderer.extract.LevelExtractor", m -> border.test(m.toString()), f -> border.test(f.toString()));
		dumpClass("net.minecraft.client.renderer.state.level.LevelRenderState", m -> border.test(m.toString()), f -> border.test(f.toString()));
	}

	private static void dumpClass(String name, Predicate<Method> methodFilter, Predicate<Field> fieldFilter) {
		try {
			Class<?> c = Class.forName(name, false, RenderDiagnostics.class.getClassLoader());
			BorderHaze.LOGGER.info("[Border Haze][diag] class {} (super={})", name, c.getSuperclass() == null ? "-" : c.getSuperclass().getName());
			for (Constructor<?> k : c.getDeclaredConstructors()) {
				BorderHaze.LOGGER.info("[Border Haze][diag]   ctor   {}", k);
			}
			Arrays.stream(c.getDeclaredFields()).filter(fieldFilter).sorted(Comparator.comparing(Field::getName))
					.forEach(f -> BorderHaze.LOGGER.info("[Border Haze][diag]   field  {}", f));
			Arrays.stream(c.getDeclaredMethods()).filter(methodFilter).sorted(Comparator.comparing(Method::getName))
					.forEach(m -> BorderHaze.LOGGER.info("[Border Haze][diag]   method {}", m));
		} catch (Throwable t) {
			BorderHaze.LOGGER.warn("[Border Haze][diag] class {} not available: {}", name, t.toString());
		}
	}
}
