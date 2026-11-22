package dev.borderhaze.client.sound;

import dev.borderhaze.BorderHaze;
import dev.borderhaze.client.border.BorderSnapshot;
import dev.borderhaze.client.border.BorderTracker;
import dev.borderhaze.client.config.HazeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Controls the border hum: computes how loud it should be from the player's distance to the border
 * (silent far away, swelling as you approach, full volume at and beyond the border) and keeps one
 * looping sound instance alive only while it is audible.
 *
 * The sound event is NOT registered in any registry (client-only mod, nothing to sync with servers);
 * the game finds the sound through assets/borderhaze/sounds.json.
 */
public final class BorderSound {
	static final SoundEvent HUM = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(BorderHaze.MOD_ID, "border_hum"));

	static volatile float targetVolume = 0.0f;
	static volatile float targetPitch = 0.9f;
	static volatile boolean wantStop = true;

	private static BorderHumSoundInstance instance;
	private static ClientLevel lastLevel;

	private BorderSound() {
	}

	/** Called once per client tick. */
	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level != lastLevel) {
			// joined / left a world or changed dimension: drop the old instance, a new one starts when needed
			killInstance();
			lastLevel = level;
		}

		float vol = 0.0f;
		if (HazeConfig.enabled && HazeConfig.soundEnabled && level != null && mc.player != null) {
			BorderSnapshot border = BorderTracker.read(mc);
			if (!border.isEffectivelyInfinite()) {
				double dist = border.signedDistance(mc.player.getX(), mc.player.getZ()); // < 0 outside
				float reach = Math.max(HazeConfig.soundDistance, 1.0f);
				float t = Math.max(0.0f, Math.min(1.0f, 1.0f - (float) (dist / reach)));
				vol = HazeConfig.soundVolume * t * t; // quadratic: a gentle start, a strong finish
				targetPitch = 0.85f + 0.15f * t;
			}
		}
		targetVolume = vol;
		boolean audible = vol > 0.004f;
		wantStop = !audible;

		if (audible) {
			if (instance == null || instance.isStopped()) {
				instance = new BorderHumSoundInstance(Math.max(vol, 0.02f));
				mc.getSoundManager().play(instance);
			}
		} else if (instance != null && instance.isStopped()) {
			instance = null;
		}
	}

	private static void killInstance() {
		if (instance != null) {
			instance.kill();
			instance = null;
		}
	}
}
