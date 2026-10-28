package pl.filip.wyzwania;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import static pl.filip.wyzwania.Model.*;

/** Main-thread domain state. No Bukkit calls, making rotation and rewards testable. */
public final class QuestEngine {
    public final State state;
    public final Periods periods;
    private final List<Template> templates;
    private final Function<Tier, Reward> rewardFactory;
    private final Random random;

    public QuestEngine(State state, Periods periods, List<Template> templates,
                       Function<Tier, Reward> rewardFactory, Random random) {
        this.state = state; this.periods = periods; this.templates = List.copyOf(templates);
        this.rewardFactory = rewardFactory; this.random = random;
    }
    public List<Quest> active() {
        List<Quest> result = new ArrayList<>(state.daily);
        result.addAll(state.weekly);
        return result;
    }
    /** Points are live configuration; items and XP remain the originally rolled bundle. */
    public void refreshPoints(ToIntFunction<Tier> configuredPoints) {
        Map<Tier, Integer> points = new EnumMap<>(Tier.class);
        for (Tier tier : Tier.values()) {
            int value = configuredPoints.applyAsInt(tier);
            if (value < 0 || value > 10000) throw new IllegalArgumentException("Nieprawidłowe punkty dla " + tier);
            points.put(tier, value);
        }
        state.daily = state.daily.stream().map(q -> withPoints(q, points.get(q.tier()))).toList();
        state.weekly = state.weekly.stream().map(q -> withPoints(q, points.get(q.tier()))).toList();
    }
    private static Quest withPoints(Quest quest, int points) {
        Reward old = quest.reward();
        return new Quest(quest.id(), quest.tier(), quest.title(), quest.objectives(),
                new Reward(old.label(), old.items(), old.xp(), points));
    }
    public UUID resolvePlayer(String nameOrId) {
        try {
            UUID id = UUID.fromString(nameOrId);
            if (state.players.containsKey(id.toString())) return id;
            throw new IllegalArgumentException("Nieznany UUID gracza. Gracz musi wcześniej wejść na serwer z pluginem.");
        } catch (IllegalArgumentException error) {
            // A UUID-like unknown target must not trigger an external profile lookup.
            if (nameOrId.contains("-")) throw new IllegalArgumentException("Nieznany lub nieprawidłowy UUID gracza.");
        }
        List<String> matches = state.players.entrySet().stream()
                .filter(e -> e.getValue().name.equalsIgnoreCase(nameOrId)).map(Map.Entry::getKey).toList();
        if (matches.isEmpty()) throw new IllegalArgumentException("Nieznany gracz. Podaj nick lub UUID gracza, który wcześniej wszedł na serwer z pluginem.");
        if (matches.size() > 1) throw new IllegalArgumentException("Kilku graczy ma zapisany ten nick. Podaj UUID.");
        return UUID.fromString(matches.getFirst());
    }
    public PlayerData addPoints(UUID id, long amount, Instant now) {
        if (amount <= 0) throw new IllegalArgumentException("Liczba dodawanych punktów musi być dodatnią liczbą całkowitą.");
        PlayerData data = state.players.get(id.toString());
        if (data == null) throw new IllegalArgumentException("Nieznany gracz.");
        String month = periods.month(now);
        long currentMonth = month.equals(data.month) ? data.monthPoints : 0;
        // Check both totals before mutating anything, so an overflowing grant is atomic.
        long total = Math.addExact(data.points, amount);
        long monthly = Math.addExact(currentMonth, amount);
        data.points = total;
        data.month = month;
        data.monthPoints = monthly;
        return data;
    }
    public boolean rotate(Instant now) {
        String day = periods.day(now).toString(), week = periods.week(now).toString();
        boolean daily = !day.equals(state.day), weekly = !week.equals(state.week);
        boolean changed = daily || weekly;
        if (changed) {
            // Prepare the entire replacement before changing periods or player progress.
            List<Quest> nextDaily = daily ? chooseDaily(day) : state.daily;
            List<Quest> nextWeekly = weekly ? List.of(choose(Tier.WEEKLY, "W:" + week, Set.of())) : state.weekly;
            state.daily = nextDaily; state.weekly = nextWeekly;
            state.day = day; state.week = week;
            cleanup(now);
        }
        return changed;
    }
    /** Explicit admin reroll: new identities even when a template returns within the same day. */
    public void reroll(Instant now, boolean daily, boolean weekly) {
        if (!daily && !weekly) throw new IllegalArgumentException("Wybierz dzienne, tygodniowe albo wszystkie.");
        List<Quest> nextDaily = daily ? chooseDaily(periods.day(now).toString()) : state.daily;
        List<Quest> nextWeekly = state.weekly;
        if (weekly) {
            Set<String> previous = templateIds(state.weekly);
            List<Template> choices = candidates(Tier.WEEKLY, Set.of()).stream().filter(t -> !previous.contains(t.id())).toList();
            if (choices.isEmpty()) throw new IllegalArgumentException("Brak innego zadania tygodniowego. Dodaj co najmniej dwa szablony WEEKLY do config.yml i użyj /wyzwanie reload.");
            nextWeekly = List.of(quest(choices.get(random.nextInt(choices.size())), "W:" + periods.week(now)));
        }
        state.daily = nextDaily; state.weekly = nextWeekly;
        if (daily) state.day = periods.day(now).toString();
        if (weekly) state.week = periods.week(now).toString();
        cleanup(now);
    }
    private void cleanup(Instant now) {
        Set<String> ids = new HashSet<>(); active().forEach(q -> ids.add(q.id()));
        state.players.values().forEach(p -> {
            p.progress.keySet().retainAll(ids);
            p.receipts.removeIf(r -> r.delivery == Delivery.CLAIMED && !ids.contains(r.id));
        });
        long cutoff = now.minusSeconds(86400).toEpochMilli();
        state.pvpKills.values().removeIf(t -> t < cutoff);
        state.pvpDamage.values().removeIf(t -> t < cutoff);
    }
    public static String templateId(Quest quest) {
        // Supports both old D:date:template and new D:date:template:UUID snapshots.
        return quest.id().split(":", 4)[2];
    }
    private static Set<String> templateIds(List<Quest> quests) {
        Set<String> ids = new HashSet<>(); quests.forEach(q -> ids.add(templateId(q))); return ids;
    }
    private List<List<Template>> dailyCombinations() {
        List<List<Template>> combinations = new ArrayList<>();
        for (Template a : candidates(Tier.EASY, Set.of())) {
            for (Template b : candidates(Tier.MEDIUM, Set.of(a.objectives().getFirst().type()))) {
                Set<Type> used = Set.of(a.objectives().getFirst().type(), b.objectives().getFirst().type());
                for (Template c : candidates(Tier.HARD, used)) combinations.add(List.of(a, b, c));
            }
        }
        return combinations;
    }
    private List<List<Template>> dailyChoices() {
        Set<String> previous = templateIds(state.daily);
        List<List<Template>> all = dailyCombinations();
        List<List<Template>> available = new ArrayList<>(all.stream()
                .filter(combo -> combo.stream().noneMatch(t -> previous.contains(t.id()))).toList());
        Collections.shuffle(available, random);
        // Pick the first viable shuffled set instead of comparing every pair in a large pool.
        for (List<Template> combo : available) {
            if (all.stream().anyMatch(other -> combo.stream()
                    .noneMatch(t -> other.stream().anyMatch(o -> o.id().equals(t.id()))))) return List.of(combo);
        }
        return List.of();
    }
    public void validateDailyPool() {
        if (dailyChoices().isEmpty()) throw new IllegalArgumentException(
                "Za mała pula dzienna: potrzebne są dwa rozłączne zestawy EASY/MEDIUM/HARD, każdy o trzech różnych typach, oraz zestaw bez obecnych zadań. Dodaj szablony do config.yml.");
    }
    private List<Quest> chooseDaily(String day) {
        List<List<Template>> choices = dailyChoices();
        if (choices.isEmpty()) { validateDailyPool(); throw new IllegalStateException("Brak zestawu dziennego."); }
        return choices.get(random.nextInt(choices.size())).stream().map(t -> quest(t, "D:" + day)).toList();
    }
    private List<Template> candidates(Tier tier, Set<Type> excluded) {
        return new ArrayList<>(templates.stream().filter(t -> t.tier() == tier)
                .filter(t -> !excluded.contains(t.objectives().getFirst().type())).toList());
    }
    private Quest choose(Tier tier, String period, Set<Type> excluded) {
        List<Template> pool = candidates(tier, excluded);
        if (pool.isEmpty()) throw new IllegalArgumentException("Brak zadań dla " + tier);
        return quest(pool.get(random.nextInt(pool.size())), period);
    }
    private Quest quest(Template template, String period) {
        return new Quest(period + ":" + template.id() + ":" + UUID.randomUUID(), template.tier(), template.title(),
                template.objectives(), rewardFactory.apply(template.tier()));
    }
    public PlayerData player(UUID id, String name) {
        PlayerData data = state.players.computeIfAbsent(id.toString(), unused -> new PlayerData());
        data.name = name;
        return data;
    }
    public Progress progress(PlayerData data, Quest quest) {
        return data.progress.computeIfAbsent(quest.id(), unused -> new Progress(quest.objectives().size()));
    }
    public List<Quest> advance(UUID id, String name, Type type, Set<String> targets, double amount, Instant now) {
        if (!Double.isFinite(amount) || amount <= 0) return List.of();
        PlayerData data = player(id, name);
        List<Quest> completed = new ArrayList<>();
        for (Quest quest : active()) {
            Progress p = progress(data, quest);
            if (p.completed) continue;
            for (int i = 0; i < quest.objectives().size(); i++) {
                Objective o = quest.objectives().get(i);
                if (o.matches(type, targets)) {
                    if (type == Type.EAT_UNIQUE || type == Type.BIOME) {
                        String identity = targets.stream().filter(t -> !t.equals("ANY")).sorted().findFirst().orElse(null);
                        if (identity == null) continue;
                        if (p.seen == null) p.seen = new HashMap<>();
                        if (!p.seen.computeIfAbsent(Integer.toString(i), unused -> new HashSet<>()).add(identity)) continue;
                        p.values[i] = Math.min(o.goal(), p.values[i] + 1);
                    } else p.values[i] = Math.min(o.goal(), p.values[i] + amount);
                }
            }
            boolean all = true;
            for (int i = 0; i < p.values.length; i++) all &= p.values[i] >= quest.objectives().get(i).goal();
            if (all) {
                p.completed = true;
                data.points += quest.reward().points();
                String month = periods.month(now);
                if (!month.equals(data.month)) { data.month = month; data.monthPoints = 0; }
                data.monthPoints += quest.reward().points();
                data.receipts.add(new Receipt(quest.id(), quest.title(), quest.reward()));
                completed.add(quest);
            }
        }
        return completed;
    }
}
