package pl.filip.wyzwania;

import java.util.*;
import static pl.filip.wyzwania.Model.*;

/** Plain text keeps quest names/labels literal and makes multi-objective progress explicit. */
public final class TabSummary {
    private TabSummary() {}
    public static String text(List<Quest> quests, PlayerData player) {
        StringBuilder result = new StringBuilder("Wyzwania — Twój postęp");
        for (Quest quest : quests) {
            Progress progress = player.progress.get(quest.id());
            String tier = switch (quest.tier()) {
                case EASY -> "Łatwe"; case MEDIUM -> "Średnie"; case HARD -> "Trudne"; case WEEKLY -> "Tygodniowe";
            };
            result.append('\n').append('[').append(tier).append("] ").append(line(quest.title()));
            if (progress != null && progress.completed) result.append(" ✓ Ukończone");
            for (int i = 0; i < quest.objectives().size(); i++) {
                Objective objective = quest.objectives().get(i);
                double value = progress == null ? 0 : progress.values[i];
                result.append("\n  ").append(line(objective.label())).append(": ")
                        .append(number(value)).append('/').append(number(objective.goal()));
            }
        }
        long pending = player.receipts.stream().filter(r -> r.delivery == Delivery.READY).count();
        return result.append("\nPunkty: ").append(player.points).append(" | Nagrody: ").append(pending)
                .append("\n/wyzwanie odbierz").toString();
    }
    private static String number(double value) { return Long.toString((long) Math.floor(value)); }
    private static String line(String value) { return value.replace('\n', ' ').replace('\r', ' '); }
}
