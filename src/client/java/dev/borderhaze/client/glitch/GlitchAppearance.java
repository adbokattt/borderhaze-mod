package dev.borderhaze.client.glitch;

import dev.borderhaze.BorderHaze;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.PlayerSkin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * What a GLITCH figure looks like on the body level. Everything here only changes how a player is DRAWN on this
 * client; the player's real skin, inventory, armor and game mechanics are untouched. (Eyes, ragged edges, colour fringes
 * and ghosts are the "shell": see render/GlitchEffect and the glitch post effect.)
 */
public final class GlitchAppearance {
	/** Packed light value for "full brightness" (sky 15, block 15): the figure is lit the same everywhere, so its marker colour is reliable. */
	public static final int FULL_BRIGHT = 0xF000F0;

	private static final Map<String, Field> FIELDS = new HashMap<>();
	private static boolean loggedProblem = false;
	private static Constructor<?> skinConstructor;
	private static Object wideModelType;

	private GlitchAppearance() {
	}

	/** Called right after vanilla filled the render state of a GLITCH player. */
	public static void apply(AvatarRenderState state) {
		// the second skin layer (hat, jacket, sleeves, pants) is not drawn: the figure is a bare silhouette
		state.showHat = false;
		state.showJacket = false;
		state.showLeftSleeve = false;
		state.showRightSleeve = false;
		state.showLeftPants = false;
		state.showRightPants = false;
		state.isSpectator = false;

		forceWideModel(state);

		// Fields of the base render-state classes: set by name, safely.
		setInt(state, "lightCoords", FULL_BRIGHT);
		setBoolean(state, "isInvisible", false);             // GLITCH is drawn even with Invisibility (a Vanish style)
		setBoolean(state, "isInvisibleToPlayer", false);
	}

	/**
	 * Steve-style thick arms for everybody: the renderer is chosen from state.skin.model() (slim = Alex, wide = Steve),
	 * so the skin record is replaced by an equal one with the WIDE model type. The account's real skin is never touched.
	 */
	private static void forceWideModel(AvatarRenderState state) {
		try {
			PlayerSkin old = state.skin;
			if (old == null) {
				return;
			}
			if (wideModelType == null) {
				Object current = old.model();
				Object[] constants = ((Enum<?>) current).getDeclaringClass().getEnumConstants();
				for (Object c : constants) {
					String n = ((Enum<?>) c).name();
					if (n.equals("WIDE") || n.equals("DEFAULT") || n.equals("CLASSIC") || n.equals("STEVE")) {
						wideModelType = c;
					}
				}
				if (wideModelType == null) {
					for (Object c : constants) {
						if (!((Enum<?>) c).name().equals("SLIM")) {
							wideModelType = c;
						}
					}
				}
				for (Constructor<?> k : PlayerSkin.class.getConstructors()) {
					if (k.getParameterCount() == 5) {
						skinConstructor = k;
					}
				}
				BorderHaze.LOGGER.info("[Border Haze][glitch] wide model type = {}", wideModelType);
			}
			if (wideModelType != null && skinConstructor != null && old.model() != wideModelType) {
				state.skin = (PlayerSkin) skinConstructor.newInstance(old.body(), old.cape(), old.elytra(), wideModelType, old.secure());
			}
		} catch (Throwable t) {
			problem("wide model", t);
		}
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

	private static void problem(String what, Throwable t) {
		if (!loggedProblem) {
			loggedProblem = true;
			BorderHaze.LOGGER.warn("[Border Haze][glitch] appearance step '{}' failed: {}", what, t.toString());
		}
	}
}
