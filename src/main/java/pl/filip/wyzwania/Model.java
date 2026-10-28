package pl.filip.wyzwania;

import java.util.*;

public final class Model {
    private Model() {}
    public enum Type { KILL, MINE, CRAFT, EAT, MOVE, FISH, BREED, SHEAR, PVP_KILL, PVP_DAMAGE,
        EAT_UNIQUE, BIOME, HARVEST, BREW, ANVIL_REPAIR, TRADE }
    public enum Tier { EASY, MEDIUM, HARD, WEEKLY }
    public enum Delivery { READY, DELIVERING, CLAIMED }

    public record Objective(Type type, String target, double goal, String label) {
        public boolean matches(Type event, Set<String> targets) {
            return type == event && Arrays.stream(target.split(",")).map(String::trim).anyMatch(targets::contains);
        }
    }
    public record ItemSpec(String material, int amount, String enchantment, int level, String potion) {}
    public record Reward(String label, List<ItemSpec> items, int xp, int points) {}
    public record Template(String id, Tier tier, String title, List<Objective> objectives) {}
    public record Quest(String id, Tier tier, String title, List<Objective> objectives, Reward reward) {}

    public static final class Progress {
        public double[] values;
        public boolean completed;
        // May be null in older SQLite snapshots; initialized lazily by the engine.
        public Map<String, Set<String>> seen = new HashMap<>();
        public Progress(int size) { values = new double[size]; }
    }
    public static final class Receipt {
        public String id;
        public String questTitle;
        public Reward reward;
        public Delivery delivery = Delivery.READY;
        public Receipt(String id, String questTitle, Reward reward) {
            this.id = id; this.questTitle = questTitle; this.reward = reward;
        }
    }
    public static final class PlayerData {
        public String name = "Gracz";
        public long points;
        public String month = "";
        public long monthPoints;
        public String selectedPrefix = "auto";
        public Map<String, Progress> progress = new HashMap<>();
        public List<Receipt> receipts = new ArrayList<>();
    }
    public static final class State {
        public int schema = 1;
        public String day = "";
        public String week = "";
        public List<Quest> daily = new ArrayList<>();
        public List<Quest> weekly = new ArrayList<>();
        public Map<String, PlayerData> players = new HashMap<>();
        public Set<String> placedOres = new HashSet<>();
        // Persistent per attacker/victim cooldown, also shared by PvP damage and kills.
        public Map<String, Long> pvpKills = new HashMap<>();
        public Map<String, Long> pvpDamage = new HashMap<>();
    }
}
