package pl.filip.wyzwania;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static pl.filip.wyzwania.Model.*;

class PointsTest {
    @TempDir Path temporary;
    private QuestEngine engine() {
        QuestEngine engine = QuestEngineTest.engine(new State());
        engine.rotate(QuestEngineTest.MONDAY);
        return engine;
    }
    private static int configured(Tier tier) {
        return switch (tier) { case EASY -> 17; case MEDIUM -> 29; case HARD -> 43; case WEEKLY -> 123; };
    }
    @Test void reloadUpdatesPointsInEveryTierWithoutRerollingOrLosingProgress() {
        QuestEngine e = engine();
        QuestEngineTest.add(e, QuestEngineTest.ALICE, Type.MINE, "IRON_ORE", 1, QuestEngineTest.MONDAY);
        List<Quest> old = e.active();
        e.refreshPoints(PointsTest::configured);
        for (int i = 0; i < old.size(); i++) {
            Quest before = old.get(i), after = e.active().get(i);
            assertEquals(configured(after.tier()), after.reward().points());
            assertEquals(before.id(), after.id()); assertEquals(before.objectives(), after.objectives());
            assertEquals(before.reward().items(), after.reward().items());
            assertEquals(before.reward().label(), after.reward().label());
            assertEquals(before.reward().xp(), after.reward().xp());
        }
        QuestEngineTest.add(e, QuestEngineTest.ALICE, Type.MINE, "IRON_ORE", 1, QuestEngineTest.MONDAY);
        assertEquals(17, e.player(QuestEngineTest.ALICE, "Alice").points);
        assertEquals(17, e.player(QuestEngineTest.ALICE, "Alice").receipts.getFirst().reward.points());
    }
    @Test void restartOverridesPersistedPointsWithConfigAndPersistsTheUpdatedValues() throws Exception {
        Path file = temporary.resolve("restart.db");
        QuestEngine e = engine();
        try (SqliteStore store = new SqliteStore(file)) { store.saveNow(e.state); }
        try (SqliteStore store = new SqliteStore(file)) {
            QuestEngine restored = QuestEngineTest.engine(store.load());
            assertFalse(restored.rotate(QuestEngineTest.MONDAY));
            restored.refreshPoints(PointsTest::configured);
            assertEquals(17, restored.state.daily.getFirst().reward().points());
            assertEquals(123, restored.state.weekly.getFirst().reward().points());
            store.saveNow(restored.state);
        }
        try (SqliteStore store = new SqliteStore(file)) {
            assertEquals(123, store.load().weekly.getFirst().reward().points());
        }
    }
    @Test void updatingPointsNeverRetroactivelyCreditsOrDuplicatesCompletedRewards() {
        QuestEngine e = engine();
        QuestEngineTest.add(e, QuestEngineTest.ALICE, Type.MINE, "IRON_ORE", 2, QuestEngineTest.MONDAY);
        PlayerData player = e.player(QuestEngineTest.ALICE, "Alice");
        Receipt receipt = player.receipts.getFirst();
        e.refreshPoints(PointsTest::configured);
        QuestEngineTest.add(e, QuestEngineTest.ALICE, Type.MINE, "IRON_ORE", 1, QuestEngineTest.MONDAY);
        assertEquals(10, player.points); assertEquals(10, player.monthPoints);
        assertEquals(1, player.receipts.size()); assertSame(receipt, player.receipts.getFirst());
        assertEquals(10, receipt.reward.points());
    }
    @Test void zeroPointsAreSupportedAndNegativeConfigCannotPartiallyUpdateQuests() {
        QuestEngine e = engine();
        List<Quest> old = e.active();
        assertThrows(IllegalArgumentException.class, () -> e.refreshPoints(t -> t == Tier.HARD ? -1 : 25));
        assertEquals(old, e.active());
        e.refreshPoints(t -> 0);
        QuestEngineTest.add(e, QuestEngineTest.ALICE, Type.MINE, "IRON_ORE", 2, QuestEngineTest.MONDAY);
        assertEquals(0, e.player(QuestEngineTest.ALICE, "Alice").points);
        assertEquals(1, e.player(QuestEngineTest.ALICE, "Alice").receipts.size());
    }
    @Test void adminGrantUpdatesBothRankingsWithoutCompletingQuestsOrIssuingItems() {
        QuestEngine e = engine();
        PlayerData player = e.player(QuestEngineTest.ALICE, "Alice");
        e.addPoints(e.resolvePlayer("aLiCe"), 150, QuestEngineTest.MONDAY);
        assertEquals(150, player.points); assertEquals(150, player.monthPoints);
        assertEquals("2026-10", player.month);
        assertTrue(player.progress.isEmpty()); assertTrue(player.receipts.isEmpty());
        assertEquals(QuestEngineTest.ALICE, e.resolvePlayer(QuestEngineTest.ALICE.toString()));
    }
    @Test void offlineGrantSurvivesRestartAndStartsNewMonthlyTotal() throws Exception {
        QuestEngine e = engine(); e.player(QuestEngineTest.ALICE, "Alice");
        e.addPoints(QuestEngineTest.ALICE, 100, QuestEngineTest.MONDAY);
        e.addPoints(QuestEngineTest.ALICE, 25, Instant.parse("2026-11-01T12:00:00Z"));
        try (SqliteStore store = new SqliteStore(temporary.resolve("grant.db"))) {
            store.saveNow(e.state);
            PlayerData restored = store.load().players.get(QuestEngineTest.ALICE.toString());
            assertEquals(125, restored.points); assertEquals(25, restored.monthPoints);
            assertEquals("2026-11", restored.month);
        }
    }
    @Test void invalidGrantAndOverflowLeaveBothTotalsUnchanged() {
        QuestEngine e = engine(); PlayerData player = e.player(QuestEngineTest.ALICE, "Alice");
        assertThrows(IllegalArgumentException.class, () -> e.addPoints(QuestEngineTest.ALICE, 0, QuestEngineTest.MONDAY));
        assertThrows(IllegalArgumentException.class, () -> e.addPoints(QuestEngineTest.ALICE, -1, QuestEngineTest.MONDAY));
        e.addPoints(QuestEngineTest.ALICE, Long.MAX_VALUE, QuestEngineTest.MONDAY);
        assertThrows(ArithmeticException.class, () -> e.addPoints(QuestEngineTest.ALICE, 1, QuestEngineTest.MONDAY));
        assertEquals(Long.MAX_VALUE, player.points); assertEquals(Long.MAX_VALUE, player.monthPoints);
        player.points = 0;
        assertThrows(ArithmeticException.class, () -> e.addPoints(QuestEngineTest.ALICE, 1, QuestEngineTest.MONDAY));
        assertEquals(0, player.points); assertEquals(Long.MAX_VALUE, player.monthPoints);
    }
    @Test void unknownAndAmbiguousNamesDoNotCreatePhantomPlayers() {
        QuestEngine e = engine();
        assertThrows(IllegalArgumentException.class, () -> e.resolvePlayer("Nobody"));
        assertThrows(IllegalArgumentException.class, () -> e.resolvePlayer(QuestEngineTest.ALICE.toString()));
        assertThrows(IllegalArgumentException.class, () -> e.addPoints(QuestEngineTest.ALICE, 1, QuestEngineTest.MONDAY));
        assertTrue(e.state.players.isEmpty());
        e.player(QuestEngineTest.ALICE, "Alice"); e.player(QuestEngineTest.BOB, "Alice");
        assertThrows(IllegalArgumentException.class, () -> e.resolvePlayer("Alice"));
        assertEquals(QuestEngineTest.BOB, e.resolvePlayer(QuestEngineTest.BOB.toString()));
    }
}
