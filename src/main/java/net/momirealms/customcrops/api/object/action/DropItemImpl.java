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
import net.momirealms.customcrops.api.object.loot.Loot;
import net.momirealms.customcrops.api.object.world.SimpleLocation;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public record DropItemImpl(Loot[] loots) implements Action {

    @Override
    public void doOn(@Nullable Player player, @Nullable SimpleLocation crop_loc, ItemMode itemMode) {
        if (crop_loc == null) return;
        for (Loot loot : loots) {
            loot.drop(player, crop_loc.getBukkitLocation());
        }
    }
}
