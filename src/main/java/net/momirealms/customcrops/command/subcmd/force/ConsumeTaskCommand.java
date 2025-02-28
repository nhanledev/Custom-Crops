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

package net.momirealms.customcrops.command.subcmd.force;

import net.momirealms.customcrops.CustomCrops;
import net.momirealms.customcrops.api.object.basic.MessageManager;
import net.momirealms.customcrops.api.object.world.CCWorld;
import net.momirealms.customcrops.api.util.AdventureUtils;
import net.momirealms.customcrops.command.AbstractSubCommand;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.stream.Collectors;

public class ConsumeTaskCommand extends AbstractSubCommand {

    public static final ConsumeTaskCommand INSTANCE = new ConsumeTaskCommand();

    public ConsumeTaskCommand() {
        super("consume");
    }

    @Override
    public boolean onCommand(CommandSender sender, List<String> args) {
        if (lackArgs(sender, 1, args.size())) return true;
        World world = Bukkit.getWorld(args.get(0));
        if (world == null) {
            AdventureUtils.sendMessage(sender, MessageManager.prefix + MessageManager.worldNotExist.replace("{world}", args.get(0)));
            return true;
        }
        AdventureUtils.sendMessage(sender, MessageManager.prefix + MessageManager.forceConsume.replace("{world}", args.get(0)));
        CustomCrops.getInstance().getScheduler().runTaskAsync(() -> {
            CCWorld ccworld = CustomCrops.getInstance().getWorldDataManager().getWorld(args.get(0));
            if (ccworld != null) {
                ccworld.scheduleConsumeTask();
            }
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, List<String> args) {
        if (args.size() == 1) {
            return super.filterStartingWith(Bukkit.getWorlds().stream().filter(world -> CustomCrops.getInstance().getWorldDataManager().isWorldAllowed(world)).map(WorldInfo::getName).collect(Collectors.toList()), args.get(0));
        }
        return null;
    }
}
