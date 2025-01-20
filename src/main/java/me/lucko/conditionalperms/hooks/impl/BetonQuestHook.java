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

import me.lucko.conditionalperms.ConditionalPerms;
import me.lucko.conditionalperms.hooks.AbstractHook;
import me.lucko.helper.terminable.TerminableConsumer;
import org.betonquest.betonquest.BetonQuest;
import org.betonquest.betonquest.api.config.quest.QuestPackage;
import org.betonquest.betonquest.api.profiles.Profile;
import org.betonquest.betonquest.config.Config;
import org.betonquest.betonquest.exceptions.ObjectNotFoundException;
import org.betonquest.betonquest.id.ConditionID;
import org.betonquest.betonquest.utils.PlayerConverter;
import org.bukkit.entity.Player;

import java.util.logging.Level;

public class BetonQuestHook extends AbstractHook {
    BetonQuestHook(ConditionalPerms plugin) {
        super(plugin);
    }

    public boolean hasCondition(Player player, String packageName, String value) {
        Profile playerProfile = PlayerConverter.getID(player);
        QuestPackage questPackage = Config.getPackages().get(packageName);
        if (questPackage == null) {
            return false;
        }
        try {
            ConditionID conditionID = new ConditionID(questPackage, value);
            return BetonQuest.condition(playerProfile, conditionID);
        } catch (ObjectNotFoundException ex) {
            getPlugin().getLogger().log(Level.SEVERE, "Could not find BetonQuest object", ex);
        }
        return false;
    }


    /*
     * Pass on BetonQuest events if the hook is enabled.
     */
    @Override
    public void setup(TerminableConsumer consumer) {

    }
}
