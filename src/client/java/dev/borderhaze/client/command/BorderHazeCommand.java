package dev.borderhaze.client.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.borderhaze.client.config.HazeConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * LOCAL client-side command /bhlocal (personal tweaks; nothing is sent to any server).
 * The server-wide command is /borderhaze (operators, command blocks, functions) - see ServerCommands.
 *
 * While a server's settings are active the look is controlled by the server, so changes here are refused
 * (except status / debug / ignoreserver); your own values stay saved and come back when you leave the server.
 *
 *   /bhlocal status | on | off | reload
 *   /bhlocal style blur|pixel     /bhlocal blur <0..96>        /bhlocal haze <0..1>
 *   /bhlocal falloff <0.5..200>   /bhlocal pixelsize <2..64>
 *   /bhlocal edge <0..192>        /bhlocal edgedistance <1..200>   /bhlocal edgehaze <0..1>
 *   /bhlocal sound <0..1>         /bhlocal debug <true|false>
 *   /bhlocal ignoreserver <true|false>   true = never accept a server's settings
 */
public final class BorderHazeCommand {
	private BorderHazeCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				ClientCommands.literal("bhlocal")
						.executes(ctx -> status(ctx.getSource()))
						.then(ClientCommands.literal("status").executes(ctx -> status(ctx.getSource())))
						.then(ClientCommands.literal("on").executes(ctx -> change(ctx.getSource(), () -> HazeConfig.enabled = true, "on")))
						.then(ClientCommands.literal("off").executes(ctx -> change(ctx.getSource(), () -> HazeConfig.enabled = false, "off")))
						.then(ClientCommands.literal("reload").executes(ctx -> {
							HazeConfig.reload();
							return say(ctx.getSource(), "Border Haze: config reloaded");
						}))
						.then(ClientCommands.literal("style")
								.then(ClientCommands.literal("blur").executes(ctx -> change(ctx.getSource(), () -> HazeConfig.style = 0, "style = blur")))
								.then(ClientCommands.literal("pixel").executes(ctx -> change(ctx.getSource(), () -> HazeConfig.style = 1, "style = pixel"))))
						.then(floatNode("blur", "radius", 0f, 96f, v -> HazeConfig.blurRadius = v))
						.then(floatNode("haze", "strength", 0f, 1f, v -> HazeConfig.hazeStrength = v))
						.then(floatNode("falloff", "blocks", 0.5f, 200f, v -> HazeConfig.falloff = v))
						.then(floatNode("pixelsize", "pixels", 2f, 64f, v -> HazeConfig.pixelSize = v))
						.then(floatNode("edge", "pixels", 0f, 192f, v -> HazeConfig.edgeBlur = v))
						.then(floatNode("edgedistance", "blocks", 1f, 200f, v -> HazeConfig.edgeDistance = v))
						.then(floatNode("edgehaze", "strength", 0f, 1f, v -> HazeConfig.edgeHaze = v))
						.then(floatNode("sound", "volume", 0f, 1f, v -> {
							HazeConfig.soundVolume = v;
							HazeConfig.soundEnabled = v > 0.0f;
						}))
						.then(ClientCommands.literal("debug")
								.then(ClientCommands.argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
									HazeConfig.debugMask = BoolArgumentType.getBool(ctx, "enabled");
									HazeConfig.save();
									return say(ctx.getSource(), "Border Haze: debug view = " + HazeConfig.debugMask);
								})))
						.then(ClientCommands.literal("ignoreserver")
								.then(ClientCommands.argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
									boolean ignore = BoolArgumentType.getBool(ctx, "enabled");
									HazeConfig.ignoreServerSettings = ignore;
									HazeConfig.save();
									HazeConfig.reload(); // applies immediately (leaves server control when set to true)
									return say(ctx.getSource(), "Border Haze: ignore server settings = " + ignore);
								})))
		));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> floatNode(String literal, String argument, float min, float max, Consumer<Float> setter) {
		return ClientCommands.literal(literal).then(ClientCommands.argument(argument, FloatArgumentType.floatArg(min, max)).executes(ctx -> {
			float value = FloatArgumentType.getFloat(ctx, argument);
			return change(ctx.getSource(), () -> setter.accept(value), literal + " = " + value);
		}));
	}

	/** Applies a LOCAL change unless a server is controlling the look. */
	private static int change(FabricClientCommandSource source, Runnable mutation, String message) {
		if (HazeConfig.isServerControlled()) {
			return say(source, "Border Haze: this server controls the border visuals, local changes are disabled here. "
					+ "(/bhlocal ignoreserver true = use your own values.)");
		}
		mutation.run();
		HazeConfig.save();
		return say(source, "Border Haze: " + message);
	}

	private static int status(FabricClientCommandSource source) {
		return say(source, "Border Haze" + (HazeConfig.isServerControlled() ? " [SERVER-CONTROLLED]" : " [local]") + ": "
				+ (HazeConfig.enabled ? "on" : "off")
				+ " | style=" + (HazeConfig.style == 1 ? "pixel" : "blur")
				+ " | blur=" + HazeConfig.blurRadius
				+ " | haze=" + HazeConfig.hazeStrength
				+ " | falloff=" + HazeConfig.falloff
				+ " | pixelSize=" + HazeConfig.pixelSize
				+ " | edge=" + HazeConfig.edgeBlur + "@" + HazeConfig.edgeDistance + " haze " + HazeConfig.edgeHaze
				+ " | sound=" + (HazeConfig.soundEnabled ? HazeConfig.soundVolume : 0.0f)
				+ " | debug=" + HazeConfig.debugMask
				+ " | ignoreServer=" + HazeConfig.ignoreServerSettings);
	}

	private static int say(FabricClientCommandSource source, String text) {
		source.sendFeedback(Component.literal(text));
		return Command.SINGLE_SUCCESS;
	}
}
