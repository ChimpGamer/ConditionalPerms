package me.lucko.conditionalperms.conditions.worldguard;

import me.lucko.conditionalperms.conditions.AbstractCondition;
import me.lucko.conditionalperms.events.PlayerEnterRegionEvent;
import me.lucko.conditionalperms.events.PlayerLeaveRegionEvent;
import me.lucko.conditionalperms.hooks.impl.WorldGuardHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

public class InRegion extends AbstractCondition {
    public InRegion() {
        super(true, WorldGuardHook.class);
    }

    @Override
    public boolean shouldApply(Player player, String parameter) {
        return getPlugin().getHookManager().isHooked(WorldGuardHook.class) &&
                getPlugin().getHookManager().get(WorldGuardHook.class).getRegions(player).contains(parameter.toLowerCase());
    }

    @EventHandler
    public void onRegionEnter(PlayerEnterRegionEvent e) {
        getPlugin().refreshPlayer(e.getPlayer(), 1L);
    }

    @EventHandler
    public void onRegionLeave(PlayerLeaveRegionEvent e) {
        getPlugin().refreshPlayer(e.getPlayer(), 1L);
    }
}
