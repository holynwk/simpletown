package com.simpletown;

import com.simpletown.api.SimpleTownAPI;
import com.simpletown.command.TownAdminCommand;
import com.simpletown.command.TownCommand;
import com.simpletown.data.TownManager;
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
    private Economy economy;
    private SimpleTownAPI api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new MessageService(this);
        confirmationManager = new ConfirmationManager(messages);
        townManager = new TownManager(this);
        api = new SimpleTownAPI(townManager);
        settingsMenuManager = new SettingsMenuManager(townManager, messages);
        progressionService = new ProgressionService(this);
        progressionMenuManager = new ProgressionMenuManager(this, townManager, progressionService, messages);
        inventoryService = new TownInventoryService(townManager, messages);
        craftRestrictionService = new CraftRestrictionService(this, townManager, messages);
        hookEconomy();

        TownCommand townCommand = new TownCommand(this, townManager, confirmationManager, messages, settingsMenuManager, progressionMenuManager, inventoryService);
        getCommand("town").setExecutor(townCommand);
        getCommand("town").setTabCompleter(townCommand);

        TownAdminCommand adminCommand = new TownAdminCommand(townManager, messages, settingsMenuManager);
        getCommand("townadmin").setExecutor(adminCommand);
        getCommand("townadmin").setTabCompleter(adminCommand);

        getServer().getPluginManager().registerEvents(new ChunkEnterListener(townManager, messages), this);
        getServer().getPluginManager().registerEvents(settingsMenuManager, this);
        getServer().getPluginManager().registerEvents(progressionMenuManager, this);
        getServer().getPluginManager().registerEvents(inventoryService, this);
        getServer().getPluginManager().registerEvents(craftRestrictionService, this);
    }

    @Override
    public void onDisable() {
        townManager.save();
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
}
