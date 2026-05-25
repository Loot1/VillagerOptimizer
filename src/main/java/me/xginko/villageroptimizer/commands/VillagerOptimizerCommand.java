package me.xginko.villageroptimizer.commands;

import me.xginko.villageroptimizer.VillagerOptimizer;
import me.xginko.villageroptimizer.commands.optimizevillagers.OptVillagersRadius;
import me.xginko.villageroptimizer.commands.unoptimizevillagers.UnOptVillagersRadius;
import me.xginko.villageroptimizer.commands.villageroptimizer.VillagerOptimizerCmd;
import me.xginko.villageroptimizer.struct.Disableable;
import me.xginko.villageroptimizer.struct.Enableable;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

public abstract class VillagerOptimizerCommand implements Enableable, Disableable, CommandExecutor, TabCompleter {

    public static final Set<VillagerOptimizerCommand> COMMANDS = new HashSet<>();
    public static final List<String> RADIUS_SUGGESTIONS = Arrays.asList("5", "10", "25", "50");

    public final PluginCommand pluginCommand;

    protected VillagerOptimizerCommand(@NotNull String name) throws CommandException {
        PluginCommand pluginCommand = VillagerOptimizer.getInstance().getCommand(name);
        if (pluginCommand != null) this.pluginCommand = pluginCommand;
        else throw new CommandException("Command cannot be enabled because it's not defined in the plugin.yml.");
    }

    public static void reloadCommands() {
        COMMANDS.forEach(VillagerOptimizerCommand::disable);
        COMMANDS.clear();

        List<Callable<VillagerOptimizerCommand>> factories = Arrays.asList(
                OptVillagersRadius::new,
                UnOptVillagersRadius::new,
                VillagerOptimizerCmd::new
        );

        for (Callable<VillagerOptimizerCommand> factory : factories) {
            try {
                COMMANDS.add(factory.call());
            } catch (Exception e) {
                VillagerOptimizer.logger().error("Failed initialising a command.", e);
            }
        }

        COMMANDS.forEach(VillagerOptimizerCommand::enable);
    }

    @Override
    public void enable() {
        pluginCommand.setExecutor(this);
        pluginCommand.setTabCompleter(this);
    }

    @Override
    public void disable() {
        pluginCommand.unregister(VillagerOptimizer.commandRegistration().getServerCommandMap());
    }
}