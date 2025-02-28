/*
 *  Copyright (C) <2022> <XiaoMoMi>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.momirealms.customcrops.api.object.action;

import net.momirealms.customcrops.api.object.ItemMode;
import net.momirealms.customcrops.api.object.world.SimpleLocation;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public class ChainImpl implements Action {

    private final Action[] actions;
    private final double chance;

    public ChainImpl(Action[] actions, double chance) {
        this.actions = actions;
        this.chance = chance;
    }

    @Override
    public void doOn(@Nullable Player player, @Nullable SimpleLocation crop_loc, ItemMode itemMode) {
        if (Math.random() < chance) {
            for (Action action : actions) {
                action.doOn(player, crop_loc, itemMode);
            }
        }
    }
}
