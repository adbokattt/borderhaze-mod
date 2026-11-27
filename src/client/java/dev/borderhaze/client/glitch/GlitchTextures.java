package dev.borderhaze.client.glitch;

import dev.borderhaze.BorderHaze;
import net.minecraft.resources.Identifier;

/**
 * The GLITCH body textures. All of them are "marker black": visually black, but with a tiny blue value (r = g = 0)
 * that the glitch post effect uses to find the figure on screen. The variants have a part cut out (transparent), which
 * makes that part vanish for a moment.
 */
public final class GlitchTextures {
	private static final Identifier[] BY_VARIANT = {
			tex("glitch"),
			tex("glitch_hole_head"),
			tex("glitch_hole_body"),
			tex("glitch_hole_rarm"),
			tex("glitch_hole_larm"),
			tex("glitch_hole_rleg"),
			tex("glitch_hole_lleg"),
			tex("glitch_hole_all")
	};

	private GlitchTextures() {
	}

	private static Identifier tex(String name) {
		return Identifier.fromNamespaceAndPath(BorderHaze.MOD_ID, "textures/entity/" + name + ".png");
	}

	public static Identifier forVariant(int variant) {
		return BY_VARIANT[variant >= 0 && variant < BY_VARIANT.length ? variant : 0];
	}

	/** The intact body (used for the first-person arms). */
	public static Identifier normal() {
		return BY_VARIANT[0];
	}
}
