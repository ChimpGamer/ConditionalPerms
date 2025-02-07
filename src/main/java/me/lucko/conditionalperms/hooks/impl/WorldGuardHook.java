/*
 * Copyright (c) 2017 Lucko (Luck) <luck@lucko.me>
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.conditionalperms.hooks.impl;

import com.google.common.collect.ImmutableSet;
import com.sk89q.worldguard.bukkit.RegionContainer;
import com.sk89q.worldguard.bukkit.RegionQuery;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import me.lucko.conditionalperms.ConditionalPerms;
import me.lucko.conditionalperms.events.PlayerEnterRegionEvent;
import me.lucko.conditionalperms.events.PlayerLeaveRegionEvent;
import me.lucko.conditionalperms.hooks.AbstractHook;
import me.lucko.helper.Events;
import me.lucko.helper.terminable.TerminableConsumer;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class WorldGuardHook extends AbstractHook {
    private final WorldGuardPlugin worldGuard;
    private final Map<UUID, Set<String>> regions = new HashMap<>();

    WorldGuardHook(ConditionalPerms plugin) {
        super(plugin);
        worldGuard = (WorldGuardPlugin) getPlugin().getServer().getPluginManager().getPlugin("WorldGuard");
    }

    public ImmutableSet<String> getRegions(Player player) {
        Set<String> ret = regions.get(player.getUniqueId());
        if (ret == null || ret.isEmpty()) {
            return ImmutableSet.of();
        } else {
            return ImmutableSet.copyOf(ret);
        }
    }

    private Set<String> queryRegions(Location location) {
        RegionContainer container = worldGuard.getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(location);

        if (set.size() == 0) {
            return Collections.emptySet();
        }

        final Set<String> regions = new HashSet<>();
        for (ProtectedRegion r : set.getRegions()) {
            regions.add(r.getId().toLowerCase());
        }

        return regions;
    }

    @Override
    public void setup(TerminableConsumer consumer) {
        Events.subscribe(PlayerJoinEvent.class)
                .handler(e -> regions.put(e.getPlayer().getUniqueId(), new HashSet<>(queryRegions(e.getPlayer().getLocation()))))
                .bindWith(consumer);

        Events.subscribe(PlayerQuitEvent.class)
                .handler(e -> regions.remove(e.getPlayer().getUniqueId()))
                .bindWith(consumer);

        Events.subscribe(PlayerMoveEvent.class)
                .filter( e ->
                        e.getFrom().getBlockX() != e.getTo().getBlockX() ||
                                e.getFrom().getBlockZ() != e.getTo().getBlockZ() ||
                                e.getFrom().getBlockY() != e.getTo().getBlockY() ||
                                !e.getFrom().getWorld().equals(e.getTo().getWorld()))
                .filter(e -> shouldCheck(WorldGuardHook.class, e.getPlayer().getUniqueId()))
                .handler(e -> {
                    Set<String> previouslyIn = regions.get(e.getPlayer().getUniqueId());
                    Set<String> now = queryRegions(e.getPlayer().getLocation());

                    for (String s : previouslyIn) {
                        if (!now.contains(s)) {
                            // Fire RegionLeaveEvent
                            worldGuard.getServer().getPluginManager().callEvent(new PlayerLeaveRegionEvent(e.getPlayer(), s));
                        }
                    }

                    for (String s : now) {
                        if (!previouslyIn.contains(s)) {
                            // Fire RegionEnterEvent
                            worldGuard.getServer().getPluginManager().callEvent(new PlayerEnterRegionEvent(e.getPlayer(), s));
                        }
                    }

                    previouslyIn.clear();
                    previouslyIn.addAll(now);
                })
                .bindWith(consumer);
    }
}
