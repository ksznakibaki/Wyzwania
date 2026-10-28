package pl.filip.wyzwania;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionType;
import java.time.*;
import java.util.*;
import static pl.filip.wyzwania.Model.*;

public final class Settings {
    public record Prize(String label, int weight, List<ItemSpec> items) {}
    public record Pool(int xpMin, int xpMax, int points, List<Prize> prizes) {}
    public record Prefix(String id, long points, String text) {}
    public final Periods periods;
    public final List<Template> templates = new ArrayList<>();
    public final Map<Tier, Pool> pools = new EnumMap<>(Tier.class);
    public final List<Prefix> prefixes = new ArrayList<>();
    public final boolean pvp, chat, completionAnnouncements, tab;
    public final long pvpKillCooldown, pvpDamageCooldown;
    public final int autosaveSeconds, tabRefreshSeconds;
    public final double movementMaxStep;
    public final Set<String> worlds;

    public Settings(FileConfiguration config) {
        periods = new Periods(ZoneId.of(config.getString("reset.timezone", "Europe/Warsaw")),
                LocalTime.parse(config.getString("reset.time", "04:00")));
        pvp = config.getBoolean("pvp.enabled", false);
        chat = config.getBoolean("chat.enabled", true);
        completionAnnouncements = config.getBoolean("announcements.completion", true);
        tab = config.getBoolean("tab.enabled", true);
        tabRefreshSeconds = config.getInt("tab.refresh-seconds", 2);
        require(tabRefreshSeconds >= 1 && tabRefreshSeconds <= 60, "tab.refresh-seconds musi wynosić 1–60 sekund.");
        pvpKillCooldown = config.getLong("pvp.kill-cooldown-seconds", 1800) * 1000;
        pvpDamageCooldown = config.getLong("pvp.damage-cooldown-seconds", 30) * 1000;
        require(pvpKillCooldown >= 0 && pvpKillCooldown <= 86400000 && pvpDamageCooldown >= 0 && pvpDamageCooldown <= 86400000,
                "Cooldowny PvP muszą mieścić się w zakresie 0–86400 sekund.");
        autosaveSeconds = config.getInt("storage.autosave-seconds", 30);
        require(autosaveSeconds >= 5 && autosaveSeconds <= 3600, "Autosave musi wynosić 5–3600 sekund.");
        movementMaxStep = config.getDouble("movement.max-step", 12);
        require(Double.isFinite(movementMaxStep) && movementMaxStep > 0 && movementMaxStep <= 100, "Nieprawidłowy movement.max-step.");
        worlds = Set.copyOf(config.getStringList("worlds"));
        for (Tier tier : Tier.values()) {
            ConfigurationSection pool = section(config, "rewards." + tier);
            int min = pool.getInt("xp-min"), max = pool.getInt("xp-max"), points = pool.getInt("points");
            require(min >= 0 && max >= min && max <= 100000 && points >= 0 && points <= 10000, "Nieprawidłowe XP/punkty " + tier);
            List<Prize> prizes = new ArrayList<>();
            ConfigurationSection entries = section(pool, "pool");
            for (String id : entries.getKeys(false)) {
                ConfigurationSection entry = section(entries, id);
                int weight = entry.getInt("weight", 1);
                require(weight > 0 && weight <= 10000, "Nieprawidłowa waga nagrody " + id);
                List<ItemSpec> items = new ArrayList<>();
                for (Map<?, ?> item : entry.getMapList("items")) {
                    String materialName = value(item, "material", "").toUpperCase(Locale.ROOT);
                    Material material = Material.matchMaterial(materialName);
                    require(material != null && material.isItem() && !material.isAir(), "Nieprawidłowy przedmiot " + materialName);
                    int amount = Integer.parseInt(value(item, "amount", "1"));
                    require(amount > 0 && amount <= material.getMaxStackSize(), "Przekroczony rozmiar stosu " + materialName);
                    String enchantment = value(item, "enchantment", "");
                    int level = Integer.parseInt(value(item, "level", "1"));
                    if (!enchantment.isBlank()) {
                        var key = NamespacedKey.fromString(enchantment);
                        var enchant = key == null ? null : Registry.ENCHANTMENT.get(key);
                        require(material == Material.ENCHANTED_BOOK && enchant != null && level >= 1 && level <= enchant.getMaxLevel(), "Nieprawidłowy enchant " + enchantment);
                    }
                    String potion = value(item, "potion", "");
                    if (!potion.isBlank()) {
                        require(material == Material.POTION || material == Material.SPLASH_POTION || material == Material.LINGERING_POTION, "Efekt wymaga mikstury.");
                        PotionType.valueOf(potion);
                    }
                    items.add(new ItemSpec(materialName, amount, enchantment, level, potion));
                }
                require(!items.isEmpty() && items.size() <= 36, "Nagroda wymaga 1–36 stosów przedmiotów: " + id);
                prizes.add(new Prize(entry.getString("label", id), weight, List.copyOf(items)));
            }
            require(!prizes.isEmpty(), "Pusta pula nagród " + tier);
            pools.put(tier, new Pool(min, max, points, List.copyOf(prizes)));
        }
        ConfigurationSection quests = section(config, "quests");
        for (String id : quests.getKeys(false)) {
            ConfigurationSection q = section(quests, id);
            Tier tier = Tier.valueOf(q.getString("tier", "EASY"));
            List<Objective> objectives = new ArrayList<>();
            boolean involvesPvp = false;
            for (Map<?, ?> o : q.getMapList("objectives")) {
                Type type = Type.valueOf(value(o, "type", ""));
                String target = value(o, "target", "ANY").toUpperCase(Locale.ROOT);
                double goal = Double.parseDouble(value(o, "goal", "0"));
                require(Double.isFinite(goal) && goal > 0 && goal <= 10000000, "Nieprawidłowy cel " + id);
                validateTarget(type, target);
                objectives.add(new Objective(type, target, goal, value(o, "label", target)));
                involvesPvp |= type == Type.PVP_KILL || type == Type.PVP_DAMAGE;
            }
            require(!objectives.isEmpty() && objectives.size() <= 10, "Zadanie wymaga 1–10 celów: " + id);
            require(tier == Tier.WEEKLY || objectives.size() == 1, "Dzienne zadanie musi mieć jeden cel: " + id);
            if (!involvesPvp || pvp) templates.add(new Template(id, tier, q.getString("title", id), List.copyOf(objectives)));
        }
        ConfigurationSection ranks = section(config, "prefixes");
        for (String id : ranks.getKeys(false)) {
            ConfigurationSection rank = section(ranks, id);
            long points = rank.getLong("points");
            require(points >= 0 && !id.equals("auto"), "Nieprawidłowy prefix " + id);
            prefixes.add(new Prefix(id, points, rank.getString("text", "")));
        }
        prefixes.sort(Comparator.comparingLong(Prefix::points));
        require(!prefixes.isEmpty() && prefixes.getFirst().points() == 0, "Pierwszy prefix musi wymagać 0 punktów.");
        // Validate rotation even if today's persisted quests do not need regenerating.
        new QuestEngine(new State(), periods, templates, this::reward, new Random(0)).rotate(Instant.now());
    }
    private static String value(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key); return value == null ? fallback : value.toString();
    }
    private static ConfigurationSection section(ConfigurationSection parent, String path) {
        ConfigurationSection result = parent.getConfigurationSection(path);
        require(result != null, "Brak sekcji konfiguracji: " + path); return result;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
    private static void validateTarget(Type type, String target) {
        for (String raw : target.split(",")) {
            String part = raw.trim();
            require(!part.isEmpty(), "Pusty target.");
            if (part.equals("ANY")) continue;
            switch (type) {
                case MINE, CRAFT, EAT, EAT_UNIQUE, HARVEST -> {
                    Material material = Material.matchMaterial(part);
                    require(material != null && (type != Type.MINE || material.isBlock()), "Nieprawidłowy materiał " + part);
                    if (type == Type.EAT || type == Type.EAT_UNIQUE) require(material.isEdible(), "Cel jedzenia musi być jadalny: " + part);
                    if (type == Type.HARVEST) require(Set.of("WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "COCOA", "SWEET_BERRY_BUSH").contains(part), "Nieobsługiwana uprawa: " + part);
                }
                case KILL, BREED, SHEAR -> EntityType.valueOf(part);
                case FISH -> require(Set.of("FISH", "TREASURE").contains(part), "Połów: ANY, FISH albo TREASURE.");
                case BIOME -> {
                    String name = part.toLowerCase(Locale.ROOT);
                    NamespacedKey key = NamespacedKey.fromString(name.contains(":") ? name : "minecraft:" + name);
                    require(key != null && Registry.BIOME.get(key) != null, "Nieprawidłowy biom: " + part);
                }
                case BREW -> PotionType.valueOf(part);
                case TRADE -> require(Set.of("VILLAGER", "WANDERING_TRADER").contains(part), "Handel: ANY, VILLAGER lub WANDERING_TRADER.");
                case MOVE, PVP_KILL, PVP_DAMAGE, ANVIL_REPAIR -> throw new IllegalArgumentException(type + " wymaga target: ANY");
            }
        }
    }
    public Reward reward(Tier tier) {
        Pool pool = pools.get(tier);
        var random = java.util.concurrent.ThreadLocalRandom.current();
        int roll = random.nextInt(pool.prizes().stream().mapToInt(Prize::weight).sum());
        for (Prize prize : pool.prizes()) {
            roll -= prize.weight();
            if (roll < 0) return new Reward(prize.label(), prize.items(), random.nextInt(pool.xpMin(), pool.xpMax() + 1), pool.points());
        }
        throw new IllegalStateException("Pusta pula nagród.");
    }
    public Prefix prefix(PlayerData player) {
        Prefix best = prefixes.getFirst();
        for (Prefix prefix : prefixes) {
            if (prefix.points() <= player.points) {
                best = prefix;
                if (prefix.id().equals(player.selectedPrefix)) return prefix;
            }
        }
        return best;
    }
}
