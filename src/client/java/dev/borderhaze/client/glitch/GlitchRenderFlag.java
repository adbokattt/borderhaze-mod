package dev.borderhaze.client.glitch;

/**
 * Added to AvatarRenderState by a mixin: remembers, for one player's render state, whether that player is GLITCH.
 * (The render state has no UUID, and the renderer's later steps - texture, layers - only receive the state.)
 * Always used through "instanceof": if the mixin could not be applied, nothing happens and nothing crashes.
 */
public interface GlitchRenderFlag {
	boolean borderhaze$isGlitch();

	void borderhaze$setGlitch(boolean glitch);
}
