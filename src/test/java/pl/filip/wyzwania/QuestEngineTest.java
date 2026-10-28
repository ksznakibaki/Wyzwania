package pl.filip.wyzwania;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static pl.filip.wyzwania.Model.*;

class QuestEngineTest {
    static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final Instant MONDAY = Instant.parse("2026-10-26T12:00:00Z");
    static final Periods PERIODS = new Periods(ZoneId.of("Europe/Warsaw"), LocalTime.of(4, 0));
    static Reward reward(Tier tier) {
        return new Reward("Test", List.of(new ItemSpec("DIAMOND", 1, "", 1, "")), 100, tier == Tier.WEEKLY ? 50 : 10);
    }
    static QuestEngine engine(State state) {
        List<Template> templates = List.of(
                new Template("mine", Tier.EASY, "Górnik", List.of(new Objective(Type.MINE, "IRON_ORE,DEEPSLATE_IRON_ORE", 2, "Rudy"))),
                new Template("kill", Tier.MEDIUM, "Łowca", List.of(new Objective(Type.KILL, "ZOMBIE", 3, "Zombie"))),
                new Template("move", Tier.HARD, "Podróż", List.of(new Objective(Type.MOVE, "ANY", 10, "Bloki"))),
                new Template("week", Tier.WEEKLY, "Tydzień", List.of(
                        new Objective(Type.MINE, "IRON_ORE,DEEPSLATE_IRON_ORE", 4, "Rudy"),
                        new Objective(Type.KILL, "ZOMBIE", 5, "Zombie"))));
        List<Template> pool = new ArrayList<>(templates);
        templates.stream().filter(t -> t.tier() != Tier.WEEKLY).forEach(t ->
                pool.add(new Template(t.id() + "_alt", t.tier(), t.title(), t.objectives())));
        return new QuestEngine(state, PERIODS, pool, QuestEngineTest::reward, new Random(123));
    }
    static void add(QuestEngine e, UUID player, Type type, String target, double amount, Instant now) {
        e.advance(player, player.equals(ALICE) ? "Alice" : "Bob", type,
                target.equals("ANY") ? Set.of("ANY") : Set.of("ANY", target), amount, now);
    }
    @Test void dailyResetDoesNotRerollWeeklyQuest() {
        QuestEngine e = engine(new State());
        assertTrue(e.rotate(MONDAY));
        Quest weekly = e.state.weekly.getFirst();
        assertFalse(e.rotate(MONDAY.plusSeconds(30)));
        assertTrue(e.rotate(MONDAY.plusSeconds(86400)));
        assertSame(weekly, e.state.weekly.getFirst());
        assertEquals(3, e.state.daily.size());
        assertEquals(3, e.state.daily.stream().map(q -> q.objectives().getFirst().type()).distinct().count());
    }
    @Test void weeklyChangesOnlyOnMondayAtFourLocal() {
        QuestEngine e = engine(new State()); e.rotate(MONDAY);
        Quest weekly = e.state.weekly.getFirst();
        e.rotate(Instant.parse("2026-11-02T02:59:59Z"));
        assertSame(weekly, e.state.weekly.getFirst());
        e.rotate(Instant.parse("2026-11-02T03:00:00Z"));
        assertNotEquals(weekly.id(), e.state.weekly.getFirst().id());
    }
    @Test void progressIsIndividualAndRequiresMatchingMaterial() {
        QuestEngine e = engine(new State()); e.rotate(MONDAY);
        add(e, ALICE, Type.MINE, "DIAMOND_ORE", 100, MONDAY);
        assertEquals(0, e.player(ALICE, "Alice").points);
        add(e, ALICE, Type.MINE, "IRON_ORE", 1, MONDAY);
        add(e, BOB, Type.MINE, "IRON_ORE", 1, MONDAY);
        add(e, ALICE, Type.MINE, "DEEPSLATE_IRON_ORE", 1, MONDAY);
        assertEquals(10, e.player(ALICE, "Alice").points);
        assertEquals(0, e.player(BOB, "Bob").points);
    }
    @Test void completionAwardsPointsAndReceiptExactlyOnce() {
        QuestEngine e = engine(new State()); e.rotate(MONDAY);
        for (int i = 0; i < 10; i++) add(e, ALICE, Type.MINE, "IRON_ORE", 1, MONDAY);
        PlayerData p = e.player(ALICE, "Alice");
        assertEquals(10, p.points); assertEquals(1, p.receipts.size());
        assertEquals(Delivery.READY, p.receipts.getFirst().delivery);
        assertEquals(10, p.monthPoints);
    }
    @Test void weeklyNeedsAllObjectivesAndCountsAlongsideDaily() {
        QuestEngine e = engine(new State()); e.rotate(MONDAY);
        add(e, ALICE, Type.MINE, "IRON_ORE", 4, MONDAY);
        assertEquals(10, e.player(ALICE, "Alice").points);
        add(e, ALICE, Type.KILL, "ZOMBIE", 5, MONDAY);
        assertEquals(70, e.player(ALICE, "Alice").points);
        assertEquals(3, e.player(ALICE, "Alice").receipts.size());
    }
    @Test void resetKeepsUnclaimedRewardsAndWeeklyProgress() {
        QuestEngine e = engine(new State()); e.rotate(MONDAY);
        add(e, ALICE, Type.MINE, "IRON_ORE", 2, MONDAY);
        Receipt pending = e.player(ALICE, "Alice").receipts.getFirst();
        Quest weekly = e.state.weekly.getFirst();
        e.rotate(MONDAY.plusSeconds(86400));
        assertSame(pending, e.player(ALICE, "Alice").receipts.getFirst());
        assertEquals(2, e.progress(e.player(ALICE, "Alice"), weekly).values[0]);
        assertFalse(e.progress(e.player(ALICE, "Alice"), e.state.daily.getFirst()).completed);
    }
    @Test void downtimeSkipsExpiredPeriodsWithoutCreatingBacklog() {
        QuestEngine e = engine(new State()); e.rotate(MONDAY);
        e.rotate(MONDAY.plus(Duration.ofDays(45)));
        assertEquals(4, e.active().size());
        assertEquals(PERIODS.day(MONDAY.plus(Duration.ofDays(45))).toString(), e.state.day);
    }
    @Test void fractionalMovementAccumulatesAndCapsAtGoal() {
        QuestEngine e = engine(new State()); e.rotate(MONDAY);
        add(e, ALICE, Type.MOVE, "ANY", 0.4, MONDAY);
        add(e, ALICE, Type.MOVE, "ANY", 9.7, MONDAY);
        Progress p = e.progress(e.player(ALICE, "Alice"), e.state.daily.get(2));
        assertEquals(10, p.values[0]); assertTrue(p.completed);
    }
    @Test void invalidAmountsCannotChangeProgress() {
        QuestEngine e = engine(new State()); e.rotate(MONDAY);
        for (double amount : new double[]{-1, 0, Double.NaN, Double.POSITIVE_INFINITY}) add(e, ALICE, Type.MINE, "IRON_ORE", amount, MONDAY);
        assertTrue(e.state.players.isEmpty());
    }
    @Test void monthlyScoreRollsOverWithoutRemovingLifetimePoints() {
        QuestEngine e = engine(new State()); e.rotate(MONDAY);
        add(e, ALICE, Type.MINE, "IRON_ORE", 2, MONDAY);
        Instant november = Instant.parse("2026-11-01T12:00:00Z"); e.rotate(november);
        add(e, ALICE, Type.MINE, "IRON_ORE", 2, november);
        assertEquals(20, e.player(ALICE, "Alice").points);
        assertEquals(10, e.player(ALICE, "Alice").monthPoints);
        assertEquals("2026-11", e.player(ALICE, "Alice").month);
    }
    @Test void claimedReceiptsArePrunedButAmbiguousReceiptsRemain() {
        QuestEngine e = engine(new State()); e.rotate(MONDAY);
        add(e, ALICE, Type.MINE, "IRON_ORE", 2, MONDAY);
        add(e, ALICE, Type.KILL, "ZOMBIE", 3, MONDAY);
        var p = e.player(ALICE, "Alice");
        p.receipts.get(0).delivery = Delivery.CLAIMED;
        p.receipts.get(1).delivery = Delivery.DELIVERING;
        e.rotate(MONDAY.plusSeconds(86400));
        assertEquals(1, p.receipts.size());
        assertEquals(Delivery.DELIVERING, p.receipts.getFirst().delivery);
    }
    @Test void invalidDailyPoolFailsInsteadOfDuplicatingActivityTypes() {
        List<Template> templates = Arrays.stream(Tier.values()).map(t -> new Template(t.name(), t, t.name(),
                List.of(new Objective(Type.MINE, "ANY", 1, "Test")))).toList();
        QuestEngine e = new QuestEngine(new State(), PERIODS, templates, QuestEngineTest::reward, new Random());
        assertThrows(IllegalArgumentException.class, () -> e.rotate(MONDAY));
    }
    @Test void replacingConfigurationKeepsActiveRewardsProgressAndCompletionHistory() {
        QuestEngine old = engine(new State()); old.rotate(MONDAY);
        add(old, ALICE, Type.MINE, "IRON_ORE", 2, MONDAY);
        var original = old.active();
        List<Template> replacement = new ArrayList<>();
        original.forEach(q -> {
            replacement.add(new Template("new_" + q.tier(), q.tier(), "Nowe zadanie", q.objectives()));
            if (q.tier() != Tier.WEEKLY) replacement.add(new Template("new_alt_" + q.tier(), q.tier(), "Inne zadanie", q.objectives()));
        });
        QuestEngine reloaded = new QuestEngine(old.state, PERIODS, replacement,
                tier -> new Reward("Nowa nagroda", reward(tier).items(), 999, 99), new Random(0));
        assertFalse(reloaded.rotate(MONDAY));
        assertEquals(original, reloaded.active());
        assertEquals(10, reloaded.player(ALICE, "Alice").points);
        assertEquals(1, reloaded.player(ALICE, "Alice").receipts.size());
        add(reloaded, ALICE, Type.MINE, "IRON_ORE", 1, MONDAY);
        assertEquals(10, reloaded.player(ALICE, "Alice").points);
        assertEquals(3, reloaded.progress(reloaded.player(ALICE, "Alice"), reloaded.state.weekly.getFirst()).values[0]);
        reloaded.rotate(MONDAY.plusSeconds(86400));
        assertEquals("Nowa nagroda", reloaded.state.daily.getFirst().reward().label());
        assertEquals(99, reloaded.state.daily.getFirst().reward().points());
        assertEquals(original.getLast(), reloaded.state.weekly.getFirst());
    }
}
