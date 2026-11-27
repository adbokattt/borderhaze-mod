package dev.borderhaze.client.glitch.render;

/** Screen-space description of one visible GLITCH figure for the post effect (all values already projected). */
final class GlitchFigure {
	/** Screen rectangle (uv 0..1) that contains the figure plus room for the fringes: minU, minV, maxU, maxV. */
	final float[] rect = new float[4];
	/** Two eyes: {u, v, depth, radius(uv, relative to screen height)}. */
	final float[][] eyes = new float[2][4];
	/** Brightness of the two eyes (0 = off). */
	final float[] eyeLevel = new float[2];
}
