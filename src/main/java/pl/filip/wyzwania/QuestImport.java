package pl.filip.wyzwania;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

final class QuestImport {
    private QuestImport() {}
    static int addMissing(FileConfiguration config, FileConfiguration defaults) {
        ConfigurationSection target = config.getConfigurationSection("quests");
        ConfigurationSection source = defaults.getConfigurationSection("quests");
        if (target == null || source == null) throw new IllegalArgumentException("Brak sekcji quests w config.yml.");
        int added = 0;
        for (String id : source.getKeys(false)) {
            if (!target.contains(id)) {
                // Copy values rather than attaching a section still parented to another config.
                target.createSection(id, source.getConfigurationSection(id).getValues(true));
                added++;
            }
        }
        return added;
    }
}
