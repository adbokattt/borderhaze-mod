package dev.borderhaze.client.glitch;

import java.util.UUID;

/**
 * The "random" glitching of a figure, made DETERMINISTIC: every value is a pure function of (player UUID, time).
 * That is what keeps the body (which texture is shown) and the eyes (drawn by the post effect) in sync without sharing state,
 * and keeps all frames of one moment consistent.
 */
public final class GlitchTimeline {
	public static final int VARIANT_NORMAL = 0;
	public static final int VARIANT_HEAD = 1;
	public static final int VARIANT_BODY = 2;
	public static final int VARIANT_RIGHT_ARM = 3;
	public static final int VARIANT_LEFT_ARM = 4;
	public static final int VARIANT_RIGHT_LEG = 5;
	public static final int VARIANT_LEFT_LEG = 6;
	public static final int VARIANT_ALL = 7;

	private GlitchTimeline() {
	}

	private static long mix(long x) {
		x ^= x >>> 33;
		x *= 0xff51afd7ed558ccdL;
		x ^= x >>> 33;
		x *= 0xc4ceb9fe1a85ec53L;
		x ^= x >>> 33;
		return x;
	}

	/** 0..1, stable for one (player, time bucket, salt). */
	private static float rand(UUID id, long bucket, int salt) {
		long h = mix(id.getMostSignificantBits() ^ mix(id.getLeastSignificantBits() + bucket * 31L + salt * 0x9E3779B97F4A7C15L));
		return (h >>> 40) / (float) (1 << 24);
	}

	/**
	 * Which parts of the model are missing right now. Mostly everything is there; for a moment (~0.14 s) a single
	 * part (or very rarely the whole figure) disappears.
	 */
	public static int variant(UUID id, long nowMs) {
		long bucket = nowMs / 140L;
		float r = rand(id, bucket, 1);
		if (r < 0.012f) {
			return VARIANT_ALL;
		}
		if (r < 0.07f) {
			return 1 + (int) (rand(id, bucket, 2) * 6.0f) % 6; // head, body, right arm, left arm, right leg, left leg
		}
		return VARIANT_NORMAL;
	}

	public static boolean headPresent(int variant) {
		return variant != VARIANT_HEAD && variant != VARIANT_ALL;
	}

	/** Eye brightness 0..1: steady most of the time, with short blinks and half-strength flickers. */
	public static float eyeLevel(UUID id, long nowMs) {
		long bucket = nowMs / 90L;
		float r = rand(id, bucket, 3);
		if (r < 0.04f) {
			return 0.0f;
		}
		if (r < 0.10f) {
			return 0.35f;
		}
		return 1.0f;
	}
}
