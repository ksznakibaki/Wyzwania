package pl.filip.wyzwania;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class QuestImportTest {
    @Test void importsAllMissingTemplatesWithoutChangingPointsOrExistingOverrides() throws Exception {
        var current = new YamlConfiguration();
        current.loadFromString("rewards:\n  EASY:\n    points: 37\nquests:\n  zombie:\n    title: Moj zombie\n    tier: EASY\n");
        var defaults = new YamlConfiguration();
        try (var resource = getClass().getResourceAsStream("/config.yml")) {
            defaults.loadFromString(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals(56, QuestImport.addMissing(current, defaults));
        assertEquals(37, current.getInt("rewards.EASY.points"));
        assertEquals("Moj zombie", current.getString("quests.zombie.title"));
        assertEquals(57, current.getConfigurationSection("quests").getKeys(false).size());
        var restored = new YamlConfiguration(); restored.loadFromString(current.saveToString());
        assertEquals("BREW", restored.getMapList("quests.alchemist.objectives").getFirst().get("type"));
        assertEquals(0, QuestImport.addMissing(restored, defaults));
    }
    @Test void importedSectionsDoNotRemainLinkedToSourceConfig() {
        var current = new YamlConfiguration(); current.createSection("quests");
        var defaults = new YamlConfiguration(); defaults.set("quests.sample.title", "Original");
        QuestImport.addMissing(current, defaults);
        defaults.set("quests.sample.title", "Changed");
        assertEquals("Original", current.getString("quests.sample.title"));
    }
    @Test void missingSectionIsRejectedWithoutTouchingExistingSettings() {
        var current = new YamlConfiguration(); current.set("rewards.EASY.points", 42);
        var defaults = new YamlConfiguration(); defaults.set("quests.sample.title", "Test");
        assertThrows(IllegalArgumentException.class, () -> QuestImport.addMissing(current, defaults));
        assertEquals(42, current.getInt("rewards.EASY.points"));
    }
}
