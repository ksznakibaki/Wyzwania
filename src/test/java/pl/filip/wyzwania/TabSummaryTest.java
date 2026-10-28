package pl.filip.wyzwania;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static pl.filip.wyzwania.Model.*;

class TabSummaryTest {
    @Test void displaysEveryWeeklyObjectiveAndIndividualProgressWithoutCreatingProgress() {
        QuestEngine engine = QuestEngineTest.engine(new State()); engine.rotate(QuestEngineTest.MONDAY);
        PlayerData alice = engine.player(QuestEngineTest.ALICE, "Alice");
        PlayerData bob = engine.player(QuestEngineTest.BOB, "Bob");
        QuestEngineTest.add(engine, QuestEngineTest.ALICE, Type.MINE, "IRON_ORE", 2, QuestEngineTest.MONDAY);
        String a = TabSummary.text(engine.active(), alice);
        String b = TabSummary.text(engine.active(), bob);
        assertTrue(a.contains("[Tygodniowe] Tydzień"));
        assertTrue(a.contains("Rudy: 2/4"));
        assertTrue(a.contains("Zombie: 0/5"));
        assertTrue(a.contains("✓ Ukończone"));
        assertTrue(a.contains("Punkty: 10 | Nagrody: 1"));
        assertTrue(b.contains("Rudy: 0/4"));
        assertTrue(b.contains("Punkty: 0 | Nagrody: 0"));
        assertTrue(bob.progress.isEmpty());
    }
    @Test void claimedAndUncertainRewardsAreNotCountedAsReady() {
        QuestEngine engine = QuestEngineTest.engine(new State()); engine.rotate(QuestEngineTest.MONDAY);
        QuestEngineTest.add(engine, QuestEngineTest.ALICE, Type.MINE, "IRON_ORE", 4, QuestEngineTest.MONDAY);
        QuestEngineTest.add(engine, QuestEngineTest.ALICE, Type.KILL, "ZOMBIE", 5, QuestEngineTest.MONDAY);
        PlayerData player = engine.player(QuestEngineTest.ALICE, "Alice");
        player.receipts.get(0).delivery = Delivery.CLAIMED;
        player.receipts.get(1).delivery = Delivery.DELIVERING;
        assertTrue(TabSummary.text(engine.active(), player).contains("Nagrody: 1"));
    }
}
