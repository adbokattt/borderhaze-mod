package dev.borderhaze.client.glitch.render;

/**
 * Screen-space description of one visible GLITCH figure for the post effect (everything already projected).
 * The figure is described analytically (from the player's pose), NOT found on the picture: that is what makes it
 * independent of fog, lighting, night and distance.
 */
final class GlitchFigure {
	static final int PRIMS = 6; // head, torso, right arm, left arm, right leg, left leg

	/** Screen rectangle (uv 0..1) that contains the figure plus its smoke cloud: minU, minV, maxU, maxV. */
	final float[] rect = new float[4];
	/** x = view depth (w) of the figure centre, y = a per-figure random seed. */
	final float[] info = new float[4];
	/** Capsules: {Ax, Ay, Bx, By} in uv. */
	final float[][] prims = new float[PRIMS][4];
	/** Capsule radii in "fractions of the screen height"; negative = this part is missing right now. */
	final float[] radii = new float[8];
	/** Two eyes: {u, v, depth, radius}. */
	final float[][] eyes = new float[2][4];
	/** Brightness of the two eyes (0 = off). */
	final float[] eyeLevel = new float[2];
}
