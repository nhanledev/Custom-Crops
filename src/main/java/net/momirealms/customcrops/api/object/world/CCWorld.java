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

import net.momirealms.customcrops.CustomCrops;
import net.momirealms.customcrops.api.object.Function;
import net.momirealms.customcrops.api.object.ItemMode;
import net.momirealms.customcrops.api.object.action.Action;
import net.momirealms.customcrops.api.object.action.VariationImpl;
import net.momirealms.customcrops.api.object.basic.ConfigManager;
import net.momirealms.customcrops.api.object.condition.Condition;
import net.momirealms.customcrops.api.object.condition.DeathCondition;
import net.momirealms.customcrops.api.object.crop.CropConfig;
import net.momirealms.customcrops.api.object.crop.GrowingCrop;
import net.momirealms.customcrops.api.object.crop.StageConfig;
import net.momirealms.customcrops.api.object.fertilizer.Fertilizer;
import net.momirealms.customcrops.api.object.fertilizer.FertilizerConfig;
import net.momirealms.customcrops.api.object.fertilizer.SoilRetain;
import net.momirealms.customcrops.api.object.fertilizer.SpeedGrow;
import net.momirealms.customcrops.api.object.pot.Pot;
import net.momirealms.customcrops.api.object.pot.PotConfig;
import net.momirealms.customcrops.api.object.season.CCSeason;
import net.momirealms.customcrops.api.object.season.SeasonData;
import net.momirealms.customcrops.api.object.sprinkler.Sprinkler;
import net.momirealms.customcrops.api.object.sprinkler.SprinklerAnimation;
import net.momirealms.customcrops.api.object.sprinkler.SprinklerConfig;
import net.momirealms.customcrops.api.util.AdventureUtils;
import net.momirealms.customcrops.api.util.ConfigUtils;
import net.momirealms.customcrops.api.util.FakeEntityUtils;
import net.momirealms.customcrops.helper.Log;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class CCWorld extends Function {

    private final String worldName;
    private final Reference<World> world;
    private final ConcurrentHashMap<ChunkCoordinate, CCChunk> chunkMap;
    private final ScheduledThreadPoolExecutor schedule;
    private long current_day;
    private ScheduledFuture<?> timerTask;
    private int pointTimer;
    private int cacheTimer;
    private int workCounter;
    private int consumeCounter;
    private final HashSet<SimpleLocation> plantToday;

    public CCWorld(World world) {
        this.world = new WeakReference<>(world);
        this.worldName = world.getName();
        this.chunkMap = new ConcurrentHashMap<>(64);
        this.schedule = new ScheduledThreadPoolExecutor(ConfigManager.corePoolSize);
        this.schedule.setMaximumPoolSize(ConfigManager.maxPoolSize);
        this.schedule.setKeepAliveTime(ConfigManager.keepAliveTime, TimeUnit.SECONDS);
        this.schedule.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        this.plantToday = new HashSet<>(128);
        this.cacheTimer = ConfigManager.cacheSaveInterval;
    }

    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void init() {
        File chunks_folder = ConfigUtils.getChunkFolder(worldName);
        if (!chunks_folder.exists()) chunks_folder.mkdirs();
        File[] data_files = chunks_folder.listFiles();
        if (data_files == null) return;

        List<File> outdated = new ArrayList<>();
        for (File file : data_files) {
            ChunkCoordinate chunkCoordinate = ChunkCoordinate.getByString(file.getName().substring(0, file.getName().length() - 7));
            try (FileInputStream fis = new FileInputStream(file); ObjectInputStream ois = new ObjectInputStream(fis)) {
                CCChunk chunk = (CCChunk) ois.readObject();
                if (chunk.isUseless()) {
                    outdated.add(file);
                    continue;
                }
                if (chunkCoordinate != null) chunkMap.put(chunkCoordinate, chunk);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                Log.info("Error at " + file.getAbsolutePath());
                outdated.add(file);
            }
        }

        for (File file : outdated) {
            file.delete();
        }

        YamlConfiguration dataFile;
        if (ConfigManager.worldFolderPath.equals("")) {
            dataFile = ConfigUtils.readData(new File(CustomCrops.getInstance().getDataFolder().getParentFile().getParentFile(), worldName + File.separator + "customcrops" + File.separator + "data.yml"));
        } else {
            dataFile = ConfigUtils.readData(new File(ConfigManager.worldFolderPath + worldName + File.separator + "customcrops" + File.separator + "data.yml"));
        }
        if (ConfigManager.enableSeason) {
            SeasonData seasonData;
            if (dataFile.contains("season") && dataFile.contains("date")) {
                seasonData = new SeasonData(worldName, CCSeason.valueOf(dataFile.getString("season")), dataFile.getInt("date"));
            } else {
                seasonData = new SeasonData(worldName);
            }
            CustomCrops.getInstance().getSeasonManager().loadSeasonData(seasonData);
        }
        this.current_day = dataFile.getLong("day", 0);
    }

    @Override
    public void disable() {
        closePool();
        saveCrop();
        saveDate();
        CustomCrops.getInstance().getSeasonManager().unloadSeasonData(worldName);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void saveCrop() {
        File chunks_folder = ConfigUtils.getChunkFolder(worldName);
        if (!chunks_folder.exists()) chunks_folder.mkdirs();
        for (Map.Entry<ChunkCoordinate, CCChunk> entry : chunkMap.entrySet()) {
            ChunkCoordinate chunkCoordinate = entry.getKey();
            CCChunk chunk = entry.getValue();
            String fileName = chunkCoordinate.getFileName() + ".ccdata";
            File file = new File(chunks_folder, fileName);
            if (chunk.isUseless() && file.exists()) {
                file.delete();
                continue;
            }
            try (FileOutputStream fos = new FileOutputStream(file); ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                oos.writeObject(chunk);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void saveDate() {
        YamlConfiguration dataFile = new YamlConfiguration();
        if (ConfigManager.enableSeason && !ConfigManager.rsHook) {
            SeasonData seasonData = CustomCrops.getInstance().getSeasonManager().getSeasonData(worldName);
            if (seasonData == null) {
                dataFile.set("season", "SPRING");
                dataFile.set("date", 1);
            } else {
                dataFile.set("season", seasonData.getSeason().name());
                dataFile.set("date", seasonData.getDate());
            }
        }
        dataFile.set("day", current_day);
        try {
            dataFile.save(new File(CustomCrops.getInstance().getDataFolder().getParentFile().getParentFile(), ConfigManager.worldFolderPath + worldName + File.separator + "customcrops" + File.separator + "data.yml"));
        } catch (IOException e) {
            AdventureUtils.consoleMessage("<red>[CustomCrops] Failed to save season data for world: " + worldName);
        }
    }

    public void load() {
        this.pointTimer = ConfigManager.pointGainInterval;
        this.cacheTimer = ConfigManager.cacheSaveInterval;
        this.consumeCounter = ConfigManager.intervalConsume;
        this.workCounter = ConfigManager.intervalWork;
        this.scheduleTask();
    }

    private void scheduleTask() {
        if (this.timerTask == null) {
            this.timerTask = CustomCrops.getInstance().getScheduler().runTaskTimerAsync(() -> {

                World current = world.get();
                if (current != null) {

                    if (ConfigManager.debug) {
                        Log.info("Queue size: " + schedule.getQueue().size() + " Completed: " + schedule.getCompletedTaskCount());
                    }

                    long day = current.getFullTime() / 24000;
                    long time = current.getTime();

                    this.tryDayCycleTask(time, day);
                    this.tryScheduleGrow();
                }
                else {

                    AdventureUtils.consoleMessage("<red>[CustomCrops] World: " + worldName + " unloaded unexpectedly. Shutdown the schedule.");
                    this.schedule.shutdown();

                }
            }, 1000, 1000L);
        }
    }

    private void tryDayCycleTask(long time, long day) {
        if (time < 100 && day != current_day) {
            current_day = day;
            if (ConfigManager.enableSeason && !ConfigManager.rsHook && ConfigManager.autoSeasonChange) {
                CustomCrops.getInstance().getSeasonManager().addDate(worldName);
            }
        }
        if (ConfigManager.cacheSaveInterval != -1) {
            cacheTimer--;
            if (cacheTimer <= 0) {
                if (ConfigManager.debug) Log.info("== Save cache ==");
                cacheTimer = ConfigManager.cacheSaveInterval;
                schedule.execute(this::saveDate);
                schedule.execute(this::saveCrop);
            }
        }
    }

    private void tryScheduleGrow() {
        pointTimer--;
        if (pointTimer <= 0) {
            pointTimer = ConfigManager.pointGainInterval;
            onReachPoint();
        }
    }

    public void onReachPoint() {
        if (ConfigManager.debug) Log.info("== Grow point ==");
        plantToday.clear();
        int size = schedule.getQueue().size();
        if (size != 0) {
            schedule.getQueue().clear();
            if (ConfigManager.debug) Log.info("== Clear queue ==");
        }

        for (CCChunk chunk : chunkMap.values()) {
            chunk.scheduleGrowTask(this);
        }
        if (ConfigManager.enableScheduleSystem) {
            workCounter--;
            consumeCounter--;
            if (consumeCounter <= 0) {
                consumeCounter = ConfigManager.intervalConsume;
                if (ConfigManager.debug) Log.info("== Consume time ==");
                scheduleConsumeTask();
            }
            if (workCounter <= 0) {
                workCounter = ConfigManager.intervalWork;
                if (ConfigManager.debug) Log.info("== Work time ==");
                scheduleSprinklerWork();
            }
        }
    }

    public void unload() {
        if (this.timerTask != null) {
            this.timerTask.cancel(false);
            this.timerTask = null;
        }
    }

    private void closePool() {
        this.schedule.shutdown();
    }

    public void pushCropTask(SimpleLocation simpleLocation, int delay) {
        schedule.schedule(new CropCheckTask(simpleLocation), delay, TimeUnit.SECONDS);
    }

    public void pushSprinklerTask(SimpleLocation simpleLocation, int delay) {
        schedule.schedule(new SprinklerCheckTask(simpleLocation), delay, TimeUnit.SECONDS);
    }

    public void pushConsumeTask(SimpleLocation simpleLocation, int delay) {
        schedule.schedule(new ConsumeCheckTask(simpleLocation), delay, TimeUnit.SECONDS);
    }

    public class ConsumeCheckTask implements Runnable {

        private final SimpleLocation simpleLocation;

        public ConsumeCheckTask(SimpleLocation simpleLocation) {
            this.simpleLocation = simpleLocation;
        }

        public void run() {
            Pot pot = getPotData(simpleLocation);
            if (pot == null) return;

            if (pot.isWet() && CustomCrops.getInstance().getFertilizerManager().getConfigByFertilizer(pot.getFertilizer()) instanceof SoilRetain soilRetain && soilRetain.canTakeEffect()) {
                pot.setWater(pot.getWater() + 1);
            }
            if (pot.reduceWater() | pot.reduceFertilizer()) {

                PotConfig potConfig = pot.getConfig();
                Fertilizer fertilizer = pot.getFertilizer();
                boolean wet = pot.isWet();

                if (!wet && fertilizer == null) {
                    removePotData(simpleLocation);
                }

                Location location = simpleLocation.getBukkitLocation();
                if (location == null) {
                    return;
                }

                CustomCrops.getInstance().getScheduler().callSyncMethod(() -> {
                    if (CustomCrops.getInstance().getPlatformInterface().removeAnyBlock(location)) {
                        String replacer = wet ? potConfig.getWetPot(fertilizer) : potConfig.getDryPot(fertilizer);
                        if (ConfigUtils.isVanillaItem(replacer)) location.getBlock().setType(Material.valueOf(replacer));
                        else CustomCrops.getInstance().getPlatformInterface().placeNoteBlock(location, replacer);
                    } else {
                        CustomCrops.getInstance().getWorldDataManager().removePotData(SimpleLocation.getByBukkitLocation(location));
                    }
                    return null;
                });
            }
        }
    }

    public class SprinklerCheckTask implements Runnable {

        private final SimpleLocation simpleLocation;

        public SprinklerCheckTask(SimpleLocation simpleLocation) {
            this.simpleLocation = simpleLocation;
        }

        public void run() {
            Sprinkler sprinkler = getSprinklerData(simpleLocation);
            if (sprinkler == null) return;

            SprinklerConfig sprinklerConfig = sprinkler.getConfig();
            if (sprinklerConfig == null) {
                removeSprinklerData(simpleLocation);
                return;
            }
            int water = sprinkler.getWater();
            if (water < 1) {
                removeSprinklerData(simpleLocation);
                return;
            }

            SprinklerAnimation sprinklerAnimation = sprinklerConfig.getSprinklerAnimation();
            Location location = simpleLocation.getBukkitLocation();
            if (location != null && sprinklerAnimation != null) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    SimpleLocation playerLoc = SimpleLocation.getByBukkitLocation(player.getLocation());
                    if (playerLoc.isNear(simpleLocation, 48)) {
                        FakeEntityUtils.playWaterAnimation(player, location.clone().add(0.5, sprinklerAnimation.offset(), 0.5), sprinklerAnimation.id(), sprinklerAnimation.duration(), sprinklerAnimation.itemMode());
                    }
                }
            }

            sprinkler.setWater(--water);
            if (water == 0) {
                removeSprinklerData(simpleLocation);
            }
            int range = sprinklerConfig.getRange();
            for (int i = -range; i <= range; i++) {
                for (int j = -range; j <= range; j++) {
                    addWaterToPot(simpleLocation.add(i, -1, j), 1, null);
                }
            }
        }
    }

    public class CropCheckTask implements Runnable {

        private final SimpleLocation simpleLocation;

        public CropCheckTask(SimpleLocation simpleLocation) {
            this.simpleLocation = simpleLocation;
        }

        public void run() {
            GrowingCrop growingCrop = getCropData(simpleLocation);
            if (growingCrop == null) return;

            CropConfig cropConfig = growingCrop.getConfig();
            if (cropConfig == null) {
                removeCropData(simpleLocation);
                return;
            }

            ItemMode itemMode = cropConfig.getCropMode();
            DeathCondition[] deathConditions = cropConfig.getDeathConditions();
            if (deathConditions != null) {
                for (DeathCondition deathCondition : deathConditions) {
                    if (deathCondition.checkIfDead(simpleLocation)) {
                        removeCropData(simpleLocation);
                        deathCondition.applyDeadModel(simpleLocation, itemMode);
                        return;
                    }
                }
            }

            Condition[] conditions = cropConfig.getGrowConditions();
            if (conditions != null) {
                for (Condition condition : conditions) {
                    if (!condition.isMet(simpleLocation)) {
                        return;
                    }
                }
            }

            int points = 1;
            Pot pot = CustomCrops.getInstance().getWorldDataManager().getPotData(simpleLocation.add(0,-1,0));
            if (pot != null) {
                FertilizerConfig fertilizerConfig = CustomCrops.getInstance().getFertilizerManager().getConfigByFertilizer(pot.getFertilizer());
                if (fertilizerConfig instanceof SpeedGrow speedGrow) {
                    points += speedGrow.getPointBonus();
                }
            }
            addCropPoint(points, cropConfig, growingCrop, simpleLocation, itemMode);
        }
    }

    public boolean addCropPointAt(SimpleLocation simpleLocation, int points) {
        GrowingCrop growingCrop = getCropData(simpleLocation);
        if (growingCrop == null) return false;
        CropConfig cropConfig = growingCrop.getConfig();
        if (cropConfig == null) {
            removeCropData(simpleLocation);
            return false;
        }
        if (points == 0) return true;
        addCropPoint(points, cropConfig, growingCrop, simpleLocation, cropConfig.getCropMode());
        return true;
    }

    public void addCropPoint(int points, CropConfig cropConfig, GrowingCrop growingCrop, SimpleLocation simpleLocation, ItemMode itemMode) {
        int current = growingCrop.getPoints();
        String nextModel = null;
        for (int i = current + 1; i <= points + current; i++) {
            StageConfig stageConfig = cropConfig.getStageConfig(i);
            if (stageConfig == null) continue;
            if (stageConfig.getModel() != null) nextModel = stageConfig.getModel();
            Action[] growActions = stageConfig.getGrowActions();
            if (growActions != null) {
                for (Action action : growActions) {
                    if (action instanceof VariationImpl variation) {
                        if (variation.doOn(simpleLocation, itemMode)) {
                            return;
                        }
                    } else {
                        action.doOn(null, simpleLocation, itemMode);
                    }
                }
            }
        }

        growingCrop.setPoints(current + points);
        if (growingCrop.getPoints() >= cropConfig.getMaxPoints()) {
            removeCropData(simpleLocation);
        }

        Location location = simpleLocation.getBukkitLocation();
        String finalNextModel = nextModel;
        if (finalNextModel == null || location == null) return;
        CompletableFuture<Chunk> asyncGetChunk = location.getWorld().getChunkAtAsync(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        if (itemMode == ItemMode.ITEM_FRAME || itemMode == ItemMode.ITEM_DISPLAY) {
            CompletableFuture<Boolean> loadEntities = asyncGetChunk.thenApply((chunk) -> {
                chunk.getEntities();
                return chunk.isEntitiesLoaded();
            });
            loadEntities.whenComplete((result, throwable) -> {
                CustomCrops.getInstance().getScheduler().callSyncMethod(() -> {
                    if (CustomCrops.getInstance().getPlatformInterface().removeCustomItem(location, itemMode)) {
                        CustomCrops.getInstance().getPlatformInterface().placeCustomItem(location, finalNextModel, itemMode);
                    } else {
                        removeCropData(simpleLocation);
                    }
                    return null;
                });
            });
        }
        else {
            asyncGetChunk.whenComplete((result, throwable) ->
                    CustomCrops.getInstance().getScheduler().callSyncMethod(() -> {
                        if (CustomCrops.getInstance().getPlatformInterface().removeCustomItem(location, itemMode)) {
                            CustomCrops.getInstance().getPlatformInterface().placeCustomItem(location, finalNextModel, itemMode);
                        } else {
                            removeCropData(simpleLocation);
                        }
                        return null;
                    }));
        }
    }

    public String getWorldName() {
        return worldName;
    }

    public World getWorld() {
        return world.get();
    }

    public void removePotData(SimpleLocation simpleLocation) {
        CCChunk chunk = chunkMap.get(simpleLocation.getChunkCoordinate());
        if (chunk == null) return;
        chunk.removePotData(simpleLocation);
    }

    public void removeCropData(SimpleLocation simpleLocation) {
        CCChunk chunk = chunkMap.get(simpleLocation.getChunkCoordinate());
        if (chunk == null) return;
        chunk.removeCropData(simpleLocation);
    }

    public void addCropData(SimpleLocation simpleLocation, GrowingCrop growingCrop, boolean grow) {
        CCChunk chunk = chunkMap.get(simpleLocation.getChunkCoordinate());
        if (chunk != null) {
            chunk.addCropData(simpleLocation, growingCrop);
            if (grow) growIfNotDuplicated(simpleLocation);
            return;
        }
        chunk = createNewChunk(simpleLocation);
        chunk.addCropData(simpleLocation, growingCrop);
        if (grow) growIfNotDuplicated(simpleLocation);
    }

    private void growIfNotDuplicated(SimpleLocation simpleLocation) {
        if (plantToday.contains(simpleLocation)) {
            return;
        }
        pushCropTask(simpleLocation, ThreadLocalRandom.current().nextInt(ConfigManager.pointGainInterval));
        plantToday.add(simpleLocation);
    }

    public GrowingCrop getCropData(SimpleLocation simpleLocation) {
        CCChunk chunk = chunkMap.get(simpleLocation.getChunkCoordinate());
        if (chunk != null) {
            return chunk.getCropData(simpleLocation);
        }
        return null;
    }

    public int getChunkCropAmount(SimpleLocation simpleLocation) {
        CCChunk chunk = chunkMap.get(simpleLocation.getChunkCoordinate());
        if (chunk == null) return 0;
        return chunk.getCropAmount();
    }

    public void removeGreenhouse(SimpleLocation simpleLocation) {
        CCChunk chunk = chunkMap.get(simpleLocation.getChunkCoordinate());
        if (chunk == null) return;
        chunk.removeGreenhouse(simpleLocation);
    }

    public void addGreenhouse(SimpleLocation simpleLocation) {
        CCChunk chunk = chunkMap.get(simpleLocation.getChunkCoordinate());
        if (chunk != null) {
            chunk.addGreenhouse(simpleLocation);
            return;
        }
        chunk = createNewChunk(simpleLocation);
        chunk.addGreenhouse(simpleLocation);
    }

    public boolean isGreenhouse(SimpleLocation simpleLocation) {
        CCChunk chunk = chunkMap.get(simpleLocation.getChunkCoordinate());
        if (chunk == null) return false;
        return chunk.isGreenhouse(simpleLocation);
    }

    public void removeScarecrow(SimpleLocation simpleLocation) {
        CCChunk chunk = chunkMap.get(simpleLocation.getChunkCoordinate());
        if (chunk == null) return;
        chunk.removeScarecrow(simpleLocation);
    }

    public void addScarecrow(SimpleLocation simpleLocation) {
        CCChunk chunk = chunkMap.get(simpleLocation.getChunkCoordinate());
        if (chunk != null) {
            chunk.addScarecrow(simpleLocation);
            return;
        }
        chunk = createNewChunk(simpleLocation);
        chunk.addScarecrow(simpleLocation);
    }

    public boolean hasScarecrow(SimpleLocation simpleLocation) {
        CCChunk chunk = chunkMap.get(simpleLocation.getChunkCoordinate());
        if (chunk == null) return false;
        return chunk.hasScarecrow();
    }

    public void removeSprinklerData(SimpleLocation simpleLocation) {
        CCChunk chunk = chunkMap.get(simpleLocation.getChunkCoordinate());
        if (chunk == null) return;
        chunk.removeSprinklerData(simpleLocation);
    }

    public void addSprinklerData(SimpleLocation simpleLocation, Sprinkler sprinkler) {
        CCChunk chunk = chunkMap.get(simpleLocation.getChunkCoordinate());
        if (chunk != null) {
            chunk.addSprinklerData(simpleLocation, sprinkler);
            return;
        }
        chunk = createNewChunk(simpleLocation);
        chunk.addSprinklerData(simpleLocation, sprinkler);
    }

    @Nullable
    public Sprinkler getSprinklerData(SimpleLocation simpleLocation) {
        CCChunk chunk = chunkMap.get(simpleLocation.getChunkCoordinate());
        if (chunk == null) return null;
        return chunk.getSprinklerData(simpleLocation);
    }

    public void addWaterToPot(SimpleLocation simpleLocation, int amount, @Nullable String pot_id) {
        CCChunk chunk = chunkMap.get(simpleLocation.getChunkCoordinate());
        if (chunk != null) {
            chunk.addWaterToPot(simpleLocation, amount, pot_id);
            return;
        }
        chunk = createNewChunk(simpleLocation);
        chunk.addWaterToPot(simpleLocation, amount, pot_id);
    }

    public void addFertilizerToPot(SimpleLocation simpleLocation, Fertilizer fertilizer, @NotNull String pot_id) {
        CCChunk chunk = chunkMap.get(simpleLocation.getChunkCoordinate());
        if (chunk != null) {
            chunk.addFertilizerToPot(simpleLocation, fertilizer, pot_id);
            return;
        }
        chunk = createNewChunk(simpleLocation);
        chunk.addFertilizerToPot(simpleLocation, fertilizer, pot_id);
    }

    public Pot getPotData(SimpleLocation simpleLocation) {
        CCChunk chunk = chunkMap.get(simpleLocation.getChunkCoordinate());
        if (chunk == null) return null;
        return chunk.getPotData(simpleLocation);
    }

    public void addPotData(SimpleLocation simpleLocation, Pot pot) {
        CCChunk chunk = chunkMap.get(simpleLocation.getChunkCoordinate());
        if (chunk != null) {
            chunk.addPotData(simpleLocation, pot);
            return;
        }
        chunk = createNewChunk(simpleLocation);
        chunk.addPotData(simpleLocation, pot);
    }

    public CCChunk createNewChunk(SimpleLocation simpleLocation) {
        CCChunk newChunk = new CCChunk();
        chunkMap.put(simpleLocation.getChunkCoordinate(), newChunk);
        return newChunk;
    }

    public void scheduleSprinklerWork() {
        schedule.execute(() -> {
            for (CCChunk chunk : chunkMap.values()) {
                chunk.scheduleSprinklerTask(this);
            }
        });
    }

    public void scheduleConsumeTask() {
        schedule.schedule(() -> {
            for (CCChunk chunk : chunkMap.values()) {
                chunk.scheduleConsumeTask(this);
            }
        }, 0, TimeUnit.SECONDS);
    }
}