package dev.borderhaze.client.glitch.mixin;

import dev.borderhaze.client.glitch.GlitchRenderFlag;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives every AvatarRenderState a "this player is GLITCH" flag (see GlitchRenderFlag). */
@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements GlitchRenderFlag {

	@Unique
	private boolean borderhaze$glitch;

	@Override
	public boolean borderhaze$isGlitch() {
		return borderhaze$glitch;
	}

	@Override
	public void borderhaze$setGlitch(boolean glitch) {
		this.borderhaze$glitch = glitch;
	}
}
