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

package net.momirealms.customcrops.api.object.requirement;

import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WeatherImpl extends AbstractRequirement implements Requirement {

    private final List<String> weathers;

    public WeatherImpl(@Nullable String[] msg, List<String> weathers) {
        super(msg);
        this.weathers = weathers;
    }

    @Override
    public boolean isConditionMet(CurrentState currentState) {
        World world = currentState.getLocation().getWorld();
        String currentWeather;
        if (world.isThundering()) currentWeather = "thunder";
        else if (world.isClearWeather()) currentWeather = "clear";
        else currentWeather = "rain";
        for (String weather : weathers) {
            if (weather.equalsIgnoreCase(currentWeather)) {
                return true;
            }
        }
        notMetMessage(currentState.getPlayer());
        return false;
    }
}
