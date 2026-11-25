package dev.borderhaze.core;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

/**
 * The ONE place that defines who is an administrator for every mechanic of the mod
 * (BorderHaze, GLITCH and anything added later): permission level 2 ("gamemaster"), which covers operators,
 * command blocks, datapack functions and the server console. Ordinary players (level 0) are never admins.
 */
public final class AdminPermission {
	private AdminPermission() {
	}

	public static boolean isAdmin(CommandSourceStack source) {
		return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}
}
