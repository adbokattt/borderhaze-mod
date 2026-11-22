package dev.borderhaze.client.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import dev.borderhaze.client.config.HazeConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * Client-side command /borderhaze. Works in single player and on any server (it never talks to the server).
 * Every change is saved to config/borderhaze.properties.
 *
 *   /borderhaze status
 *   /borderhaze style blur|pixel
 *   /borderhaze blur <0..96>          blur radius in pixels (style blur)
 *   /borderhaze haze <0..1>           haze strength
 *   /borderhaze falloff <0.5..200>    blocks over which the effect fades in
 *   /borderhaze pixelsize <2..64>     mosaic block size (style pixel)
 *   /borderhaze edge <0..192>         screen blur near the border, max radius in pixels (0 = off)
 *   /borderhaze edgedistance <1..200> blocks from the border at which the screen blur starts
 *   /borderhaze edgehaze <0..1>       haze wash over the fully blurred screen at the border
 *   /borderhaze sound <0..1>          border hum volume (0 = off)
 *   /borderhaze debug <true|false>    debug view (red tint + yellow line)
 *   /borderhaze on | off | reload
 */
public final class BorderHazeCommand {
	private BorderHazeCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				ClientCommands.literal("borderhaze")
						.executes(ctx -> status(ctx.getSource()))
						.then(ClientCommands.literal("status").executes(ctx -> status(ctx.getSource())))
						.then(ClientCommands.literal("on").executes(ctx -> {
							HazeConfig.enabled = true;
							HazeConfig.save();
							return say(ctx.getSource(), "Border Haze: on");
						}))
						.then(ClientCommands.literal("off").executes(ctx -> {
							HazeConfig.enabled = false;
							HazeConfig.save();
							return say(ctx.getSource(), "Border Haze: off");
						}))
						.then(ClientCommands.literal("reload").executes(ctx -> {
							HazeConfig.reload();
							return say(ctx.getSource(), "Border Haze: config reloaded");
						}))
						.then(ClientCommands.literal("style")
								.then(ClientCommands.literal("blur").executes(ctx -> {
									HazeConfig.style = 0;
									HazeConfig.save();
									return say(ctx.getSource(), "Border Haze: style = blur");
								}))
								.then(ClientCommands.literal("pixel").executes(ctx -> {
									HazeConfig.style = 1;
									HazeConfig.save();
									return say(ctx.getSource(), "Border Haze: style = pixel");
								})))
						.then(ClientCommands.literal("blur")
								.then(ClientCommands.argument("radius", FloatArgumentType.floatArg(0f, 96f)).executes(ctx -> {
									HazeConfig.blurRadius = FloatArgumentType.getFloat(ctx, "radius");
									HazeConfig.save();
									return say(ctx.getSource(), "Border Haze: blur radius = " + HazeConfig.blurRadius);
								})))
						.then(ClientCommands.literal("haze")
								.then(ClientCommands.argument("strength", FloatArgumentType.floatArg(0f, 1f)).executes(ctx -> {
									HazeConfig.hazeStrength = FloatArgumentType.getFloat(ctx, "strength");
									HazeConfig.save();
									return say(ctx.getSource(), "Border Haze: haze strength = " + HazeConfig.hazeStrength);
								})))
						.then(ClientCommands.literal("falloff")
								.then(ClientCommands.argument("blocks", FloatArgumentType.floatArg(0.5f, 200f)).executes(ctx -> {
									HazeConfig.falloff = FloatArgumentType.getFloat(ctx, "blocks");
									HazeConfig.save();
									return say(ctx.getSource(), "Border Haze: falloff = " + HazeConfig.falloff + " blocks");
								})))
						.then(ClientCommands.literal("pixelsize")
								.then(ClientCommands.argument("pixels", FloatArgumentType.floatArg(2f, 64f)).executes(ctx -> {
									HazeConfig.pixelSize = FloatArgumentType.getFloat(ctx, "pixels");
									HazeConfig.save();
									return say(ctx.getSource(), "Border Haze: pixel size = " + HazeConfig.pixelSize);
								})))
						.then(ClientCommands.literal("edge")
								.then(ClientCommands.argument("pixels", FloatArgumentType.floatArg(0f, 192f)).executes(ctx -> {
									HazeConfig.edgeBlur = FloatArgumentType.getFloat(ctx, "pixels");
									HazeConfig.save();
									return say(ctx.getSource(), "Border Haze: screen-edge blur = " + HazeConfig.edgeBlur);
								})))
						.then(ClientCommands.literal("edgedistance")
								.then(ClientCommands.argument("blocks", FloatArgumentType.floatArg(1f, 200f)).executes(ctx -> {
									HazeConfig.edgeDistance = FloatArgumentType.getFloat(ctx, "blocks");
									HazeConfig.save();
									return say(ctx.getSource(), "Border Haze: screen blur starts " + HazeConfig.edgeDistance + " blocks from the border");
								})))
						.then(ClientCommands.literal("edgehaze")
								.then(ClientCommands.argument("strength", FloatArgumentType.floatArg(0f, 1f)).executes(ctx -> {
									HazeConfig.edgeHaze = FloatArgumentType.getFloat(ctx, "strength");
									HazeConfig.save();
									return say(ctx.getSource(), "Border Haze: edge haze wash = " + HazeConfig.edgeHaze);
								})))
						.then(ClientCommands.literal("sound")
								.then(ClientCommands.argument("volume", FloatArgumentType.floatArg(0f, 1f)).executes(ctx -> {
									float v = FloatArgumentType.getFloat(ctx, "volume");
									HazeConfig.soundVolume = v;
									HazeConfig.soundEnabled = v > 0.0f;
									HazeConfig.save();
									return say(ctx.getSource(), "Border Haze: hum volume = " + v);
								})))
						.then(ClientCommands.literal("debug")
								.then(ClientCommands.argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
									HazeConfig.debugMask = BoolArgumentType.getBool(ctx, "enabled");
									HazeConfig.save();
									return say(ctx.getSource(), "Border Haze: debug view = " + HazeConfig.debugMask);
								})))));
	}

	private static int status(FabricClientCommandSource source) {
		return say(source, "Border Haze: " + (HazeConfig.enabled ? "on" : "off")
				+ " | style=" + (HazeConfig.style == 1 ? "pixel" : "blur")
				+ " | blur=" + HazeConfig.blurRadius
				+ " | haze=" + HazeConfig.hazeStrength
				+ " | falloff=" + HazeConfig.falloff
				+ " | pixelSize=" + HazeConfig.pixelSize
				+ " | edge=" + HazeConfig.edgeBlur + "@" + HazeConfig.edgeDistance + " haze " + HazeConfig.edgeHaze
				+ " | sound=" + (HazeConfig.soundEnabled ? HazeConfig.soundVolume : 0.0f)
				+ " | debug=" + HazeConfig.debugMask);
	}

	private static int say(FabricClientCommandSource source, String text) {
		source.sendFeedback(Component.literal(text));
		return Command.SINGLE_SUCCESS;
	}
}
