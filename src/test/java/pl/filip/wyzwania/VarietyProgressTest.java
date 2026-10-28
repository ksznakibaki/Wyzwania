package pl.filip.wyzwania;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static pl.filip.wyzwania.Model.*;

class VarietyProgressTest {
    @TempDir Path temporary;
    private QuestEngine engine(Type type, String target, int goal) {
        State state = new State();
        state.day = "2026-10-26"; state.week = "2026-10-26";
        state.daily = List.of(new Quest("D:2026-10-26:test", Tier.EASY, "Test",
                List.of(new Objective(type, target, goal, "Cel")), QuestEngineTest.reward(Tier.EASY)));
        return QuestEngineTest.engine(state);
    }
    private void add(QuestEngine engine, UUID player, Type type, String target) {
        engine.advance(player, "Gracz", type, Set.of("ANY", target), 1, QuestEngineTest.MONDAY);
    }
    @Test void sameFoodCountsOnceAndOnlyDistinctFoodCompletesQuest() {
        QuestEngine e = engine(Type.EAT_UNIQUE, "ANY", 2);
        add(e, QuestEngineTest.ALICE, Type.EAT_UNIQUE, "APPLE");
        add(e, QuestEngineTest.ALICE, Type.EAT_UNIQUE, "APPLE");
        assertEquals(0, e.player(QuestEngineTest.ALICE, "Alice").points);
        add(e, QuestEngineTest.ALICE, Type.EAT_UNIQUE, "BREAD");
        assertEquals(10, e.player(QuestEngineTest.ALICE, "Alice").points);
        assertEquals(1, e.player(QuestEngineTest.ALICE, "Alice").receipts.size());
    }
    @Test void foodListsArePerPlayerAndPerObjective() {
        QuestEngine e = engine(Type.EAT_UNIQUE, "ANY", 2);
        Quest daily = e.state.daily.getFirst();
        e.state.weekly = List.of(new Quest("W:2026-10-26:test", Tier.WEEKLY, "Dieta",
                List.of(new Objective(Type.EAT_UNIQUE, "APPLE,BREAD", 2, "Jedzenie")), QuestEngineTest.reward(Tier.WEEKLY)));
        add(e, QuestEngineTest.ALICE, Type.EAT_UNIQUE, "APPLE");
        add(e, QuestEngineTest.BOB, Type.EAT_UNIQUE, "BREAD");
        assertEquals(1, e.progress(e.player(QuestEngineTest.ALICE, "Alice"), daily).values[0]);
        assertEquals(1, e.progress(e.player(QuestEngineTest.BOB, "Bob"), daily).values[0]);
        add(e, QuestEngineTest.ALICE, Type.EAT_UNIQUE, "BREAD");
        assertEquals(60, e.player(QuestEngineTest.ALICE, "Alice").points);
        assertEquals(0, e.player(QuestEngineTest.BOB, "Bob").points);
    }
    @Test void duplicateBiomesRemainDuplicateAfterSqliteRestart() throws Exception {
        QuestEngine e = engine(Type.BIOME, "ANY", 2);
        add(e, QuestEngineTest.ALICE, Type.BIOME, "MINECRAFT:PLAINS");
        try (SqliteStore store = new SqliteStore(temporary.resolve("biomes.db"))) {
            store.saveNow(e.state); e = QuestEngineTest.engine(store.load());
            add(e, QuestEngineTest.ALICE, Type.BIOME, "MINECRAFT:PLAINS");
            assertEquals(0, e.player(QuestEngineTest.ALICE, "Alice").points);
            add(e, QuestEngineTest.ALICE, Type.BIOME, "MINECRAFT:DESERT");
            assertEquals(10, e.player(QuestEngineTest.ALICE, "Alice").points);
        }
    }
    @Test void duplicateFoodsRemainDuplicateAfterSqliteRestart() throws Exception {
        QuestEngine e = engine(Type.EAT_UNIQUE, "ANY", 2);
        add(e, QuestEngineTest.ALICE, Type.EAT_UNIQUE, "APPLE");
        try (SqliteStore store = new SqliteStore(temporary.resolve("food.db"))) {
            store.saveNow(e.state); e = QuestEngineTest.engine(store.load());
            add(e, QuestEngineTest.ALICE, Type.EAT_UNIQUE, "APPLE");
            assertEquals(1, e.progress(e.player(QuestEngineTest.ALICE, "Alice"), e.state.daily.getFirst()).values[0]);
        }
    }
    @Test void oldSnapshotsWithoutSeenFieldAreInitializedLazily() {
        QuestEngine e = engine(Type.EAT_UNIQUE, "ANY", 2);
        Progress p = e.progress(e.player(QuestEngineTest.ALICE, "Alice"), e.state.daily.getFirst()); p.seen = null;
        add(e, QuestEngineTest.ALICE, Type.EAT_UNIQUE, "APPLE");
        add(e, QuestEngineTest.ALICE, Type.EAT_UNIQUE, "APPLE");
        assertEquals(1, p.values[0]); assertNotNull(p.seen);
    }
    @Test void visitingSameBiomeWithBothTargetAliasesStillCountsOnce() {
        QuestEngine e = engine(Type.BIOME, "PLAINS", 2);
        for (int i = 0; i < 3; i++) e.advance(QuestEngineTest.ALICE, "Alice", Type.BIOME,
                Set.of("ANY", "PLAINS", "MINECRAFT:PLAINS"), 1, QuestEngineTest.MONDAY);
        assertEquals(1, e.progress(e.player(QuestEngineTest.ALICE, "Alice"), e.state.daily.getFirst()).values[0]);
    }
    @Test void uniqueTargetsAreFilteredBeforeRememberingAndMissingIdentityDoesNotCount() {
        QuestEngine e = engine(Type.EAT_UNIQUE, "APPLE,BREAD", 2);
        add(e, QuestEngineTest.ALICE, Type.EAT_UNIQUE, "CARROT");
        e.advance(QuestEngineTest.ALICE, "Alice", Type.EAT_UNIQUE, Set.of("ANY"), 100, QuestEngineTest.MONDAY);
        assertEquals(0, e.progress(e.player(QuestEngineTest.ALICE, "Alice"), e.state.daily.getFirst()).values[0]);
    }
    @Test void harvestBrewRepairAndTradeUseIndependentCountersAndRespectTargets() {
        for (Type type : List.of(Type.HARVEST, Type.BREW, Type.ANVIL_REPAIR, Type.TRADE)) {
            QuestEngine e = engine(type, "MATCH", 2);
            add(e, QuestEngineTest.ALICE, type, "OTHER");
            add(e, QuestEngineTest.ALICE, type, "MATCH");
            add(e, QuestEngineTest.ALICE, type, "MATCH");
            assertEquals(10, e.player(QuestEngineTest.ALICE, "Alice").points, type.toString());
        }
    }
    @Test void newQuestHasIndependentFoodList() {
        QuestEngine e = engine(Type.EAT_UNIQUE, "ANY", 2);
        add(e, QuestEngineTest.ALICE, Type.EAT_UNIQUE, "APPLE");
        Quest old = e.state.daily.getFirst();
        e.state.daily = List.of(new Quest("D:2026-10-27:test", old.tier(), old.title(), old.objectives(), old.reward()));
        add(e, QuestEngineTest.ALICE, Type.EAT_UNIQUE, "APPLE");
        assertEquals(1, e.progress(e.player(QuestEngineTest.ALICE, "Alice"), e.state.daily.getFirst()).values[0]);
    }
}
