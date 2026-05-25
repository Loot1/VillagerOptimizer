package me.xginko.villageroptimizer.modules;

import com.github.benmanes.caffeine.cache.Cache;
import me.xginko.villageroptimizer.VillagerOptimizer;
import me.xginko.villageroptimizer.config.Config;
import me.xginko.villageroptimizer.modules.gameplay.EnableLeashingVillagers;
import me.xginko.villageroptimizer.modules.gameplay.FixOptimisationAfterCure;
import me.xginko.villageroptimizer.modules.gameplay.LevelOptimizedProfession;
import me.xginko.villageroptimizer.modules.gameplay.MakeVillagersSpawnAdult;
import me.xginko.villageroptimizer.modules.gameplay.PreventOptimizedDamage;
import me.xginko.villageroptimizer.modules.gameplay.PreventOptimizedTargeting;
import me.xginko.villageroptimizer.modules.gameplay.PreventUnoptimizedTrading;
import me.xginko.villageroptimizer.modules.gameplay.RestockOptimizedTrades;
import me.xginko.villageroptimizer.modules.gameplay.UnoptimizeOnJobLoose;
import me.xginko.villageroptimizer.modules.gameplay.VisuallyHighlightOptimized;
import me.xginko.villageroptimizer.modules.optimization.OptimizeByActivity;
import me.xginko.villageroptimizer.modules.optimization.OptimizeByBlock;
import me.xginko.villageroptimizer.modules.optimization.OptimizeByNametag;
import me.xginko.villageroptimizer.modules.optimization.OptimizeByWorkstation;
import me.xginko.villageroptimizer.struct.Disableable;
import me.xginko.villageroptimizer.struct.Enableable;
import me.xginko.villageroptimizer.wrapper.WrappedVillager;
import org.bukkit.entity.Villager;
import space.arim.morepaperlib.scheduling.GracefulScheduling;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public abstract class VillagerOptimizerModule implements Enableable, Disableable {

    public static final Set<VillagerOptimizerModule> ENABLED_MODULES = new HashSet<>();

    public abstract boolean shouldEnable();

    protected final VillagerOptimizer plugin;
    protected final Config config;
    protected final Cache<Villager, WrappedVillager> wrapperCache;
    protected final GracefulScheduling scheduling;
    public final String configPath;
    private final String logFormat;

    public VillagerOptimizerModule(String configPath) {
        this.plugin = VillagerOptimizer.getInstance();
        this.config = VillagerOptimizer.config();
        this.wrapperCache = VillagerOptimizer.wrappers();
        this.scheduling = VillagerOptimizer.scheduling();
        this.configPath = configPath;
        shouldEnable(); // Ensure enable option is always first
        String[] paths = configPath.split("\\.");
        if (paths.length <= 2) {
            this.logFormat = "<" + configPath + "> {}";
        } else {
            this.logFormat = "<" + paths[paths.length - 2] + "." + paths[paths.length - 1] + "> {}";
        }
    }

    public static void reloadModules() {
        ENABLED_MODULES.forEach(VillagerOptimizerModule::disable);
        ENABLED_MODULES.clear();

        List<Supplier<VillagerOptimizerModule>> factories = Arrays.asList(
                // optimization methods
                OptimizeByActivity::new,
                OptimizeByBlock::new,
                OptimizeByNametag::new,
                OptimizeByWorkstation::new,
                // gameplay
                EnableLeashingVillagers::new,
                FixOptimisationAfterCure::new,
                LevelOptimizedProfession::new,
                MakeVillagersSpawnAdult::new,
                PreventOptimizedDamage::new,
                PreventOptimizedTargeting::new,
                PreventUnoptimizedTrading::new,
                RestockOptimizedTrades::new,
                UnoptimizeOnJobLoose::new,
                VisuallyHighlightOptimized::new,
                // chunk limit
                VillagerChunkLimit::new
        );

        for (Supplier<VillagerOptimizerModule> factory : factories) {
            try {
                VillagerOptimizerModule module = factory.get();
                if (module.shouldEnable()) {
                    ENABLED_MODULES.add(module);
                }
            } catch (Exception e) {
                VillagerOptimizer.logger().error("Failed initialising a module.", e);
            }
        }

        ENABLED_MODULES.forEach(VillagerOptimizerModule::enable);
    }

    protected void error(String message, Throwable throwable) {
        VillagerOptimizer.logger().error(logFormat, message, throwable);
    }

    protected void error(String message) {
        VillagerOptimizer.logger().error(logFormat, message);
    }

    protected void warn(String message) {
        VillagerOptimizer.logger().warn(logFormat, message);
    }

    protected void info(String message) {
        VillagerOptimizer.logger().info(logFormat, message);
    }

    protected void notRecognized(Class<?> clazz, String unrecognized) {
        warn("Unable to parse " + clazz.getSimpleName() + " at '" + unrecognized + "'. Please check your configuration.");
    }
}
