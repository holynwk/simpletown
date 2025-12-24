package com.simpletown;

import com.simpletown.api.SimpleTownAPI;
import com.simpletown.command.PoliticalScoreCommand;
import com.simpletown.command.PlotCommand;
import com.simpletown.command.TownAdminCommand;
import com.simpletown.command.TownCommand;
import com.simpletown.data.TownManager;
import com.simpletown.jobs.JobRewardService;
import com.simpletown.jobs.JobsListener;
import com.simpletown.jobs.JobsManager;
import com.simpletown.jobs.JobsMenuManager;
import com.simpletown.jobs.JobsService;
import com.simpletown.listener.ChunkEnterListener;
import com.simpletown.service.ConfirmationManager;
import com.simpletown.service.MessageService;
import com.simpletown.service.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;


public class SimpleTownPlugin extends JavaPlugin {
    private TownManager townManager;
    private ConfirmationManager confirmationManager;
    private MessageService messages;
    private SettingsMenuManager settingsMenuManager;
    private ProgressionService progressionService;
    private ProgressionMenuManager progressionMenuManager;
    private TownInventoryService inventoryService;
    private CraftRestrictionService craftRestrictionService;
    private RichChunkService richChunkService;
    private ResourceMenuManager resourceMenuManager;
    private InfoMenuManager infoMenuManager;
    private BlueMapService blueMapService;
    private PoliticalScoreService politicalScoreService;
    private JobsManager jobsManager;
    private JobsService jobsService;
    private JobsMenuManager jobsMenuManager;
    private JobRewardService jobRewardService;
    private Economy economy;
    private SimpleTownAPI api;
    private PlotSettingsMenuManager plotSettingsMenuManager;
    private PlotInfoMenuManager plotInfoMenuManager;


    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new MessageService(this);
        confirmationManager = new ConfirmationManager(messages);
        townManager = new TownManager(this);
        api = new SimpleTownAPI(townManager);
        jobsManager = new JobsManager(this);
        jobsService = new JobsService(this, jobsManager, messages);
        jobsMenuManager = new JobsMenuManager(jobsService, jobsManager, messages);
        jobRewardService = new JobRewardService(this, messages);
        politicalScoreService = new PoliticalScoreService(this);
        settingsMenuManager = new SettingsMenuManager(townManager, messages);
        progressionService = new ProgressionService(this);
        progressionMenuManager = new ProgressionMenuManager(this, townManager, progressionService, messages, politicalScoreService);
        inventoryService = new TownInventoryService(townManager, messages);
        craftRestrictionService = new CraftRestrictionService(this, townManager, messages);
        richChunkService = new RichChunkService(this);
        resourceMenuManager = new ResourceMenuManager(richChunkService, messages, townManager);
        infoMenuManager = new InfoMenuManager(messages);
        plotSettingsMenuManager = new PlotSettingsMenuManager(townManager, messages);
        plotInfoMenuManager = new PlotInfoMenuManager(messages);
        blueMapService = new BlueMapService(this, townManager);
        hookEconomy();
        registerPlaceholder();
        scheduleRichChunkRewards();

        TownCommand townCommand = new TownCommand(this, townManager, confirmationManager, messages, settingsMenuManager, progressionMenuManager, inventoryService, richChunkService, resourceMenuManager, infoMenuManager, blueMapService, politicalScoreService, jobsMenuManager);
        getCommand("town").setExecutor(townCommand);
        getCommand("town").setTabCompleter(townCommand);

        TownAdminCommand adminCommand = new TownAdminCommand(this, townManager, messages, settingsMenuManager, richChunkService, blueMapService, politicalScoreService);
        getCommand("townadmin").setExecutor(adminCommand);
        getCommand("townadmin").setTabCompleter(adminCommand);

        PoliticalScoreCommand politicalScoreCommand = new PoliticalScoreCommand(messages, politicalScoreService);
        getCommand("politscore").setExecutor(politicalScoreCommand);

        PlotCommand plotCommand = new PlotCommand(this, townManager, messages, plotSettingsMenuManager, plotInfoMenuManager);
        getCommand("plot").setExecutor(plotCommand);
        getCommand("plot").setTabCompleter(plotCommand);

        getServer().getPluginManager().registerEvents(new ChunkEnterListener(townManager, messages), this);
        getServer().getPluginManager().registerEvents(settingsMenuManager, this);
        getServer().getPluginManager().registerEvents(progressionMenuManager, this);
        getServer().getPluginManager().registerEvents(inventoryService, this);
        getServer().getPluginManager().registerEvents(craftRestrictionService, this);
        getServer().getPluginManager().registerEvents(resourceMenuManager, this);
        getServer().getPluginManager().registerEvents(infoMenuManager, this);
        getServer().getPluginManager().registerEvents(plotSettingsMenuManager, this);
        getServer().getPluginManager().registerEvents(plotInfoMenuManager, this);
        getServer().getPluginManager().registerEvents(jobsMenuManager, this);
        getServer().getPluginManager().registerEvents(new JobsListener(jobsService, jobsManager, jobRewardService), this);

        getServer().getScheduler().runTaskTimer(this, () -> {
            for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                jobsService.handleKillerTimeout(player);
                jobsService.ensureKillerTarget(player);
            }
        }, 20L, 20L * 60L);
    }

    @Override
    public void onDisable() {
        townManager.save();
        richChunkService.save();
        politicalScoreService.save();
        if (jobsManager != null) {
            jobsManager.save();
        }
    }

    public Economy getEconomy() {
        return economy;
    }

    public SimpleTownAPI getApi() {
        return api;
    }

    public boolean ensureEconomy() {
        if (economy != null) {
            return true;
        }

        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault не найден. Экономика будет недоступна.");
            return false;
        }

        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            getLogger().warning("Провайдер экономики не найден. Требуется установленный экономический плагин, совместимый с Vault.");
            return false;
        }

        economy = registration.getProvider();
        return economy != null;
    }

    private void hookEconomy() {
        ensureEconomy();
    }

    private void registerPlaceholder() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PoliticalScorePlaceholder(politicalScoreService).register();
        }
    }

    private void scheduleRichChunkRewards() {
        long day = 20L * 60L * 60L * 24L;
        getServer().getScheduler().runTaskTimer(this, () -> richChunkService.distributeDailyRewards(townManager), 20L, day);
    }
}