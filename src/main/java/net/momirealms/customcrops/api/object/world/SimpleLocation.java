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

package net.momirealms.customcrops.api.object.world;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class SimpleLocation implements Serializable {

    @Serial
    private static final long serialVersionUID = -1288860694388882412L;

    private final int x;
    private final int y;
    private final int z;
    private final String worldName;

    public SimpleLocation(String worldName, int x, int y, int z){
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public int getY() {
        return y;
    }

    public String getWorldName() {
        return worldName;
    }

    public ChunkCoordinate getChunkCoordinate() {
        return new ChunkCoordinate(x >> 4, z >> 4);
    }

    public SimpleLocation add(int x, int y, int z) {
        return new SimpleLocation(worldName, this.x + x, this.y + y, this.z + z);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SimpleLocation other = (SimpleLocation) obj;
        if (!Objects.equals(worldName, other.getWorldName())) {
            return false;
        }
        if (Double.doubleToLongBits(this.x) != Double.doubleToLongBits(other.x)) {
            return false;
        }
        if (Double.doubleToLongBits(this.y) != Double.doubleToLongBits(other.y)) {
            return false;
        }
        if (Double.doubleToLongBits(this.z) != Double.doubleToLongBits(other.z)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        //hash = 19 * hash + (worldName != null ? worldName.hashCode() : 0);
        hash = 19 * hash + (int) (Double.doubleToLongBits(this.x) ^ (Double.doubleToLongBits(this.x) >>> 32));
        hash = 19 * hash + (int) (Double.doubleToLongBits(this.y) ^ (Double.doubleToLongBits(this.y) >>> 32));
        hash = 19 * hash + (int) (Double.doubleToLongBits(this.z) ^ (Double.doubleToLongBits(this.z) >>> 32));
        return hash;
    }

    @Nullable
    public Location getBukkitLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    @Nullable
    public World getBukkitWorld() {
        return Bukkit.getWorld(worldName);
    }

    public static SimpleLocation getByString(String location, String world) {
        String[] loc = StringUtils.split(location, ",");
        return new SimpleLocation(world, Integer.parseInt(loc[0]), Integer.parseInt(loc[1]), Integer.parseInt(loc[2]));
    }

    public static SimpleLocation getByBukkitLocation(Location location) {
        return new SimpleLocation(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    public String toString() {
        return "[" + worldName + "," + x + "," + y + "," + z + "]";
    }

    public boolean isNear(SimpleLocation simpleLocation, int distance) {
        if (Math.abs(simpleLocation.x - this.x) > distance) {
            return false;
        }
        if (Math.abs(simpleLocation.z - this.z) > distance) {
            return false;
        }
        if (Math.abs(simpleLocation.y - this.y) > distance) {
            return false;
        }
        return true;
    }

    public SimpleLocation copy() {
        return new SimpleLocation(worldName, x, y, z);
    }
}