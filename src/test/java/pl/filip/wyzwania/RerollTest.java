package pl.filip.wyzwania;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static pl.filip.wyzwania.Model.*;

class RerollTest {
    @TempDir Path temporary;
    private QuestEngine engine() {
        QuestEngine e = QuestEngineTest.engine(new State()); e.rotate(QuestEngineTest.MONDAY); return e;
    }
    private Set<String> ids(List<Quest> quests) {
        Set<String> result = new HashSet<>(); quests.forEach(q -> result.add(QuestEngine.templateId(q))); return result;
    }
    @Test void nextDayCannotRepeatAnyOfThePreviousThreeTemplates() {
        QuestEngine e = engine();
        for (int day = 1; day <= 100; day++) {
            Set<String> previous = ids(e.state.daily);
            e.rotate(QuestEngineTest.MONDAY.plusSeconds(day * 86400L));
            assertTrue(Collections.disjoint(previous, ids(e.state.daily)));
        }
    }
    @Test void fourAmResetChangesTemplatesAndKeepsWeeklyProgress() {
        QuestEngine e = engine();
        e.rotate(Instant.parse("2026-10-28T02:59:59Z"));
        Set<String> before = ids(e.state.daily);
        Quest weekly = e.state.weekly.getFirst();
        QuestEngineTest.add(e, QuestEngineTest.ALICE, Type.MINE, "IRON_ORE", 1, Instant.parse("2026-10-28T02:59:59Z"));
        assertTrue(e.rotate(Instant.parse("2026-10-28T03:00:00Z")));
        assertTrue(Collections.disjoint(before, ids(e.state.daily)));
        assertSame(weekly, e.state.weekly.getFirst());
        assertEquals(1, e.progress(e.player(QuestEngineTest.ALICE, "Alice"), weekly).values[0]);
        assertEquals("2026-10-28", e.state.day);
    }
    @Test void restartRemembersPreviousTemplatesIncludingLegacyIds() throws Exception {
        QuestEngine e = engine();
        e.state.daily = e.state.daily.stream().map(q -> new Quest("D:" + e.state.day + ":" + QuestEngine.templateId(q),
                q.tier(), q.title(), q.objectives(), q.reward())).toList();
        Set<String> before = ids(e.state.daily);
        try (SqliteStore store = new SqliteStore(temporary.resolve("reroll.db"))) {
            store.saveNow(e.state);
            QuestEngine restored = QuestEngineTest.engine(store.load());
            assertFalse(restored.rotate(QuestEngineTest.MONDAY));
            restored.rotate(QuestEngineTest.MONDAY.plusSeconds(86400));
            assertTrue(Collections.disjoint(before, ids(restored.state.daily)));
        }
    }
    @Test void dailyRerollKeepsWeeklyProgressPointsAndPendingRewards() {
        QuestEngine e = engine();
        QuestEngineTest.add(e, QuestEngineTest.ALICE, Type.MINE, "IRON_ORE", 2, QuestEngineTest.MONDAY);
        PlayerData player = e.player(QuestEngineTest.ALICE, "Alice");
        Receipt receipt = player.receipts.getFirst();
        Quest weekly = e.state.weekly.getFirst();
        Set<String> previous = ids(e.state.daily);
        e.reroll(QuestEngineTest.MONDAY, true, false);
        assertTrue(Collections.disjoint(previous, ids(e.state.daily)));
        assertSame(weekly, e.state.weekly.getFirst());
        assertEquals(2, e.progress(player, weekly).values[0]);
        assertEquals(10, player.points); assertSame(receipt, player.receipts.getFirst());
        assertEquals(Delivery.READY, receipt.delivery);
        assertTrue(e.state.daily.stream().noneMatch(q -> e.progress(player, q).completed));
    }
    @Test void repeatedManualRerollsNeverReuseProgressOrReceiptIdentity() {
        QuestEngine e = engine(); Set<String> unique = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            for (Quest quest : e.state.daily) assertTrue(unique.add(quest.id()));
            e.reroll(QuestEngineTest.MONDAY, true, false);
        }
    }
    @Test void tooSmallPoolIsRejectedBeforeAnyProgressOrPeriodChanges() {
        QuestEngine e = engine();
        QuestEngineTest.add(e, QuestEngineTest.ALICE, Type.MINE, "IRON_ORE", 1, QuestEngineTest.MONDAY);
        var original = e.active(); String day = e.state.day;
        List<Template> single = original.stream().map(q -> new Template(QuestEngine.templateId(q), q.tier(), q.title(), q.objectives())).toList();
        QuestEngine limited = new QuestEngine(e.state, e.periods, single, QuestEngineTest::reward, new Random());
        assertThrows(IllegalArgumentException.class, limited::validateDailyPool);
        assertThrows(IllegalArgumentException.class, () -> limited.reroll(QuestEngineTest.MONDAY, true, false));
        assertThrows(IllegalArgumentException.class, () -> limited.rotate(QuestEngineTest.MONDAY.plusSeconds(86400)));
        assertEquals(day, e.state.day); assertEquals(original, e.active());
        assertEquals(1, e.progress(e.player(QuestEngineTest.ALICE, "Alice"), e.state.daily.getFirst()).values[0]);
    }
    @Test void failedWeeklyPartOfAllRerollCannotResetDailyProgress() {
        QuestEngine e = engine(); var original = e.active();
        assertThrows(IllegalArgumentException.class, () -> e.reroll(QuestEngineTest.MONDAY, true, true));
        assertEquals(original, e.active());
    }
    @Test void weeklyOnlyAndAllRerollsSelectNewQuestsWithoutChangingResetTime() {
        QuestEngine e = engine();
        List<Template> pool = new ArrayList<>();
        e.active().forEach(q -> {
            pool.add(new Template(QuestEngine.templateId(q), q.tier(), q.title(), q.objectives()));
            pool.add(new Template(QuestEngine.templateId(q) + "_other", q.tier(), "Inne", q.objectives()));
        });
        e = new QuestEngine(e.state, e.periods, pool, QuestEngineTest::reward, new Random());
        var before = e.state.daily; Quest oldWeek = e.state.weekly.getFirst();
        e.reroll(QuestEngineTest.MONDAY, false, true);
        assertEquals(before, e.state.daily);
        assertNotEquals(QuestEngine.templateId(oldWeek), QuestEngine.templateId(e.state.weekly.getFirst()));
        Instant nextReset = e.periods.nextDay(QuestEngineTest.MONDAY).toInstant();
        var previous = e.active();
        e.reroll(QuestEngineTest.MONDAY, true, true);
        assertTrue(Collections.disjoint(ids(previous), ids(e.active())));
        assertEquals(nextReset, e.periods.nextDay(QuestEngineTest.MONDAY).toInstant());
        assertFalse(e.rotate(QuestEngineTest.MONDAY));
    }
}
