package dev.borderhaze.client.glitch;

import dev.borderhaze.BorderHaze;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * What a GLITCH figure looks like (stage 2: the body). Everything here only changes how a player is DRAWN on this
 * client; the player's real skin, inventory, armor and game mechanics are untouched.
 */
public final class GlitchAppearance {
	/** The GLITCH skin: a completely black body with two glowing eyes. Used instead of the account's skin. */
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(BorderHaze.MOD_ID, "textures/entity/glitch.png");

	/** Packed light value for "full brightness" (sky 15, block 15): the eyes always glow, the black body stays black. */
	public static final int FULL_BRIGHT = 0xF000F0;

	private static final Map<String, Field> FIELDS = new HashMap<>();
	private static boolean loggedReflectionProblem = false;

	private GlitchAppearance() {
	}

	/** Called right after vanilla filled the render state of a GLITCH player. */
	public static void apply(AvatarRenderState state) {
		// the second skin layer (hat, jacket, sleeves, pants) is not drawn: the figure is a bare black silhouette
		state.showHat = false;
		state.showJacket = false;
		state.showLeftSleeve = false;
		state.showRightSleeve = false;
		state.showLeftPants = false;
		state.showRightPants = false;
		state.isSpectator = false;

		// Fields of the base render-state classes (not part of the dump we studied): set by name, safely.
		setInt(state, "lightCoords", FULL_BRIGHT);          // always fully lit: the eyes glow in the dark
		setBoolean(state, "isInvisible", false);             // GLITCH is drawn even if the player has Invisibility (a Vanish style)
		setBoolean(state, "isInvisibleToPlayer", false);
	}

	private static void setInt(Object target, String name, int value) {
		Field f = field(target.getClass(), name);
		if (f != null) {
			try {
				f.setInt(target, value);
			} catch (Throwable t) {
				problem(name, t);
			}
		}
	}

	private static void setBoolean(Object target, String name, boolean value) {
		Field f = field(target.getClass(), name);
		if (f != null) {
			try {
				f.setBoolean(target, value);
			} catch (Throwable t) {
				problem(name, t);
			}
		}
	}

	/** Finds a field by name in the class or any parent class; null if it does not exist (never throws). */
	private static Field field(Class<?> type, String name) {
		String key = type.getName() + "#" + name;
		if (FIELDS.containsKey(key)) {
			return FIELDS.get(key);
		}
		Field found = null;
		for (Class<?> c = type; c != null && found == null; c = c.getSuperclass()) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				found = f;
			} catch (NoSuchFieldException ignored) {
			} catch (Throwable t) {
				problem(name, t);
				break;
			}
		}
		FIELDS.put(key, found);
		if (found == null) {
			BorderHaze.LOGGER.info("[Border Haze][glitch] render-state field '{}' does not exist in this version (skipped)", name);
		}
		return found;
	}

	private static void problem(String name, Throwable t) {
		if (!loggedReflectionProblem) {
			loggedReflectionProblem = true;
			BorderHaze.LOGGER.warn("[Border Haze][glitch] could not set render-state field '{}': {}", name, t.toString());
		}
	}
}
