package dev.borderhaze.glitch;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import dev.borderhaze.core.AdminPermission;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /glitch - administrators only (the shared AdminPermission: permission level 2; works from command blocks,
 * datapack functions and the console too).
 *
 *   /glitch on | off | toggle | status               yourself
 *   /glitch on | off | toggle | status <player>      another online player
 *
 * Every command checks the permission itself and answers non-admins with a clear message
 * (so the command stays visible but never does anything for ordinary players).
 */
public final class GlitchCommands {
	private static final String PREFIX = "[GLITCH] ";

	private enum Action {
		ON, OFF, TOGGLE, STATUS
	}

	private GlitchCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("glitch")
						.executes(ctx -> usage(ctx.getSource()))
						.then(Commands.literal("on")
								.executes(ctx -> run(ctx, Action.ON, null))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> run(ctx, Action.ON, EntityArgument.getPlayer(ctx, "player")))))
						.then(Commands.literal("off")
								.executes(ctx -> run(ctx, Action.OFF, null))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> run(ctx, Action.OFF, EntityArgument.getPlayer(ctx, "player")))))
						.then(Commands.literal("toggle")
								.executes(ctx -> run(ctx, Action.TOGGLE, null))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> run(ctx, Action.TOGGLE, EntityArgument.getPlayer(ctx, "player")))))
						.then(Commands.literal("status")
								.executes(ctx -> run(ctx, Action.STATUS, null))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> run(ctx, Action.STATUS, EntityArgument.getPlayer(ctx, "player")))))
		));
	}

	private static int run(CommandContext<CommandSourceStack> ctx, Action action, ServerPlayer explicitTarget) {
		CommandSourceStack source = ctx.getSource();
		if (!AdminPermission.isAdmin(source)) {
			source.sendFailure(Component.literal(PREFIX + "Недостаточно прав: команда доступна только администраторам."));
			return 0;
		}

		ServerPlayer target = explicitTarget != null ? explicitTarget : source.getPlayer();
		if (target == null) {
			source.sendFailure(Component.literal(PREFIX + "Эту команду нужно выполнять от имени игрока или указать ник: /glitch " + action.name().toLowerCase() + " <игрок>"));
			return 0;
		}

		String name = target.getName().getString();
		boolean current = GlitchManager.isGlitched(target.getUUID());

		switch (action) {
			case STATUS -> {
				source.sendSuccess(() -> Component.literal(PREFIX + name + ": " + (current ? "GLITCH активен." : "GLITCH не активен.")
						+ vanishNote(target, current)), false);
			}
			case ON -> apply(source, target, name, true, current);
			case OFF -> apply(source, target, name, false, current);
			case TOGGLE -> apply(source, target, name, !current, current);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static void apply(CommandSourceStack source, ServerPlayer target, String name, boolean wanted, boolean current) {
		if (wanted == current) {
			source.sendSuccess(() -> Component.literal(PREFIX + name + (wanted ? ": GLITCH уже активен." : ": GLITCH уже выключен.")), false);
			return;
		}
		GlitchManager.set(source.getServer(), target.getUUID(), wanted);
		if (wanted) {
			source.sendSuccess(() -> Component.literal(PREFIX + name + " превращён в GLITCH." + vanishNote(target, true)), false);
		} else {
			source.sendSuccess(() -> Component.literal(PREFIX + "Превращение отключено: " + name + " снова выглядит как обычно."), false);
		}
	}

	/** Explains the GLITCH-over-Vanish rule when the target is hidden by the vanilla spectator mode. */
	private static String vanishNote(ServerPlayer target, boolean glitched) {
		if (glitched && target.isSpectator()) {
			return " Игрок в режиме наблюдателя (Vanish): его фигура видна всем, GLITCH приоритетнее Vanish. После отключения Vanish применится как раньше.";
		}
		return "";
	}

	private static int usage(CommandSourceStack source) {
		if (!AdminPermission.isAdmin(source)) {
			source.sendFailure(Component.literal(PREFIX + "Недостаточно прав: команда доступна только администраторам."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal(PREFIX + "Использование: /glitch <on|off|toggle|status> [игрок]"), false);
		return Command.SINGLE_SUCCESS;
	}
}
