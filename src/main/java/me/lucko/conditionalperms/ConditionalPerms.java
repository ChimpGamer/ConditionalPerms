package me.lucko.conditionalperms;

import lombok.Getter;
import me.lucko.conditionalperms.conditions.AbstractCondition;
import me.lucko.conditionalperms.hooks.AbstractHook;
import me.lucko.conditionalperms.hooks.HookManager;
import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.regex.Pattern;

public class ConditionalPerms extends JavaPlugin implements Listener {
    private static final Pattern DOT_PATTERN = Pattern.compile("\\.");
    private static final Pattern EQUALS_PATTERN = Pattern.compile("=");

    private boolean debug = false;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    /**
     * Used to stop any listeners in hooks firing for players who do not have any conditional permissions assigned.
     */
    @Getter
    private final Map<UUID, Set<Class<? extends AbstractHook>>> neededHooks = new HashMap<>();

    @Getter
    private final HookManager hookManager = new HookManager(this);

    private void debug(String s) {
        if (debug) getLogger().info("[DEBUG] " + s);
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        for (Condition condition : Condition.values()) {
            condition.getCondition().init(this);
        }

        hookManager.init();
    }

    @Override
    public void onDisable() {
        hookManager.shutdown();
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent e) {
        final Player p = e.getPlayer();
        attachments.put(p.getUniqueId(), p.addAttachment(this));
        neededHooks.put(p.getUniqueId(), new HashSet<Class<? extends AbstractHook>>());
        refreshPlayer(p);
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent e) {
        refreshPlayer(e.getPlayer());
        refreshPlayer(e.getPlayer(), 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        final Player p = e.getPlayer();
        p.removeAttachment(attachments.get(p.getUniqueId()));
        attachments.remove(p.getUniqueId());
        neededHooks.remove(p.getUniqueId());
    }

    public void refreshPlayer(final Player player, long delay) {
        new BukkitRunnable() {
            @Override
            public void run() {
                refreshPlayer(player);
            }
        }.runTaskLater(this, delay);
    }

    public void refreshPlayer(Player player) {
        debug("Processing permissions for player " + player.getName() + ".");
        final PermissionAttachment attachment = attachments.get(player.getUniqueId());
        if (attachment == null) {
            debug("Aborting, permission attachment is null.");
            return;
        }

        // Clear existing applied permissions
        for (String p : attachment.getPermissions().keySet()) {
            attachment.unsetPermission(p);
        }
        neededHooks.get(player.getUniqueId()).clear();

        // process recursively so you can chain permissions together
        boolean work = true;
        final List<String> applied = new ArrayList<>();
        while (work) {
            work = false;

            for (PermissionAttachmentInfo pa : player.getEffectivePermissions()) {
                // Don't apply negative permissions
                if (!pa.getValue()) {
                    continue;
                }

                if (!pa.getPermission().startsWith("cperms.")) {
                    continue;
                }

                if (applied.contains(pa.getPermission())) {
                    continue;
                }

                debug("Processing conditional permission: " + pa.getPermission());
                final List<String> parts = Arrays.asList(DOT_PATTERN.split(pa.getPermission()));
                if (parts.size() <= 2) {
                    debug("Aborting, permission does not contain a node to apply.");
                    continue;
                }

                String conditionPart = parts.get(1);

                boolean negated = conditionPart.startsWith("!");
                if (negated) {
                    debug("Condition is negated.");
                    conditionPart = conditionPart.substring(1);
                }

                String parameter = null;
                if (conditionPart.contains("=")) {
                    final String[] parameterSplit = EQUALS_PATTERN.split(conditionPart, 2);
                    conditionPart = parameterSplit[0];
                    parameter = parameterSplit[1];
                    debug("Found parameter: " + parameter);
                }

                Condition condition = null;
                for (Condition i : Condition.values()) {
                    if (i.name().equalsIgnoreCase(conditionPart)) {
                        condition = i;
                        debug("Found condition " + condition.name() + ".");
                        break;
                    }
                }

                if (condition == null) {
                    debug("Aborting, could not find a condition that matches " + conditionPart + ".");
                    continue;
                }

                final AbstractCondition c = condition.getCondition();
                if (c.isHookNeeded()) {
                    if (!hookManager.isHooked(c.getNeededHook())) {
                        debug("Condition " + condition.name() + " requires hook " + c.getNeededHook().getSimpleName() + " to function.");
                    }

                    neededHooks.get(player.getUniqueId()).add(c.getNeededHook());
                }

                if (c.isParameterNeeded() && parameter == null) {
                    debug("Aborting, condition " + condition.name() + " requires a parameter, but one was not given.");
                    continue;
                }

                final boolean shouldApply = c.shouldApply(player, parameter);
                if (negated == shouldApply) {
                    debug("Player did not meet the conditions required for this permission to be applied.");
                    continue;
                }

                final String toApply = StringUtils.join(parts.subList(2, parts.size()), ".");
                attachment.setPermission(toApply, true);
                debug("Applying permission " + pa.getPermission() + " --> " + toApply + " for player " + player.getName() + ".");

                work = true;
                applied.add(pa.getPermission());
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendMessage(sender, "Running version &bv" + getDescription().getVersion() + "&7.");
            if (sender.hasPermission("conditionalperms.reload")) {
                sendMessage(sender, "--> &b/cperms reload&7 to refresh all online users.");
                sendMessage(sender, "--> &b/cperms reload <username>&7 to refresh a specific user.");
            }
            if (sender.hasPermission("conditionalperms.debug")) {
                sendMessage(sender, "--> &b/cperms debug&7 to toggle debug mode.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reload") && sender.hasPermission("conditionalperms.reload")) {
            if (args.length > 1) {
                final Player p = getServer().getPlayer(args[1]);
                if (p == null) {
                    sendMessage(sender, "&7Player '" + args[1] + "' is not online.");
                } else {
                    refreshPlayer(p);
                    sendMessage(sender, "&7Player " + p.getName() + " has their permissions refreshed.");
                }
            } else {
                for (Player p : getServer().getOnlinePlayers()) {
                    refreshPlayer(p);
                }
                sendMessage(sender, "&7All online users were refreshed.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("debug") && sender.hasPermission("conditionalperms.debug")) {
            debug = !debug;
            sendMessage(sender, "&7Set debug to " + debug + ".");
            return true;
        }

        sendMessage(sender, "&7Unknown sub command.");
        return true;
    }

    private static void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8&l[&fConditionalPerms&8&l] &7" + message));
    }
}
