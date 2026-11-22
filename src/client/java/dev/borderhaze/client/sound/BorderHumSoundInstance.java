package dev.borderhaze.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * The looping border hum. It plays "relative to the listener" (not positional), so it is an ambient
 * background hum; its volume and pitch follow the values computed by BorderSound every tick.
 */
final class BorderHumSoundInstance extends AbstractTickableSoundInstance {
	private volatile boolean killed = false;

	BorderHumSoundInstance(float startVolume) {
		super(BorderSound.HUM, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
		this.looping = true;
		this.delay = 0;
		this.volume = startVolume;
		this.pitch = 0.9f;
		this.relative = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
	}

	void kill() {
		killed = true;
	}

	@Override
	public void tick() {
		if (killed) {
			stop();
			return;
		}
		// smooth fade towards the target so the hum swells in and out instead of jumping
		this.volume += (BorderSound.targetVolume - this.volume) * 0.2f;
		this.pitch += (BorderSound.targetPitch - this.pitch) * 0.1f;
		if (BorderSound.wantStop && this.volume < 0.004f) {
			stop();
		}
	}
}
