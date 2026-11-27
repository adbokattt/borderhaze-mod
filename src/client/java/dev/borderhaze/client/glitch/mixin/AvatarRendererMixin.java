package dev.borderhaze.client.glitch.mixin;

import dev.borderhaze.client.glitch.GlitchAppearance;
import dev.borderhaze.client.glitch.GlitchClientState;
import dev.borderhaze.client.glitch.GlitchRenderFlag;
import dev.borderhaze.client.glitch.GlitchTextures;
import dev.borderhaze.client.glitch.GlitchTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the look of a GLITCH player. All method names and signatures come from the 26.2 dump of AvatarRenderer.
 * The players' own skins, equipment and mechanics are never modified: only what is drawn on this client changes.
 *
 * Lives in a NON-required mixin config: if a future version renames something, the game still starts
 * (the figure just stays a normal player) and nothing can crash.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

	// ---------------------------------------------------------------- third person / other players

	/** After vanilla filled the render state: remember who is GLITCH and strip the second skin layer, brighten, un-hide. */
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("RETURN"), require = 0)
	private void borderhaze$markGlitch(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		boolean glitch = GlitchClientState.isGlitched(avatar.getUUID());
		if (state instanceof GlitchRenderFlag flag) {
			flag.borderhaze$setGlitch(glitch);
			flag.borderhaze$setVariant(glitch ? GlitchTimeline.variant(avatar.getUUID(), System.currentTimeMillis()) : GlitchTimeline.VARIANT_NORMAL);
		}
		if (glitch) {
			GlitchAppearance.apply(state);
		}
	}

	/** The GLITCH skin instead of the account's skin. */
	@Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/resources/Identifier;",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void borderhaze$skin(AvatarRenderState state, CallbackInfoReturnable<Identifier> cir) {
		if (state instanceof GlitchRenderFlag flag && flag.borderhaze$isGlitch()) {
			cir.setReturnValue(GlitchTextures.forVariant(flag.borderhaze$getVariant()));
		}
	}

	/** No cape, armor, held items, elytra, head items, parrots, arrows: all of that is drawn by render layers. */
	@Inject(method = "shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Z",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void borderhaze$noLayers(AvatarRenderState state, CallbackInfoReturnable<Boolean> cir) {
		if (state instanceof GlitchRenderFlag flag && flag.borderhaze$isGlitch()) {
			cir.setReturnValue(false);
		}
	}

	/** A mysterious figure has no name tag. */
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void borderhaze$noName(Avatar avatar, double distance, CallbackInfoReturnable<Boolean> cir) {
		if (GlitchClientState.isGlitched(avatar.getUUID())) {
			cir.setReturnValue(false);
		}
	}

	// ---------------------------------------------------------------- first person (the player's own arms)

	/** renderHand(PoseStack, SubmitNodeCollector, int light, Identifier skin, ModelPart arm, boolean sleeve): the first-person arm. */
	@ModifyVariable(method = "renderHand", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private Identifier borderhaze$armSkin(Identifier skin) {
		return borderhaze$localPlayerIsGlitch() ? GlitchTextures.normal() : skin;
	}

	@ModifyVariable(method = "renderHand", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private int borderhaze$armLight(int light) {
		return borderhaze$localPlayerIsGlitch() ? GlitchAppearance.FULL_BRIGHT : light;
	}

	@ModifyVariable(method = "renderHand", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private boolean borderhaze$armSleeve(boolean sleeve) {
		return !borderhaze$localPlayerIsGlitch() && sleeve;
	}

	private static boolean borderhaze$localPlayerIsGlitch() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && GlitchClientState.isGlitched(mc.player.getUUID());
	}
}
