package pl.filip.wyzwania;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class PeriodsTest {
    private final Periods periods = QuestEngineTest.PERIODS;
    @Test void resetBoundaryIsInclusive() {
        assertEquals(LocalDate.of(2026, 10, 25), periods.day(Instant.parse("2026-10-26T02:59:59Z")));
        assertEquals(LocalDate.of(2026, 10, 26), periods.day(Instant.parse("2026-10-26T03:00:00Z")));
    }
    @Test void autumnDstUsesCalendarDays() {
        Instant before = Instant.parse("2026-10-24T02:00:00Z");
        assertEquals(Duration.ofHours(25), Duration.between(before, periods.nextDay(before).toInstant()));
    }
    @Test void springDstUsesCalendarDays() {
        Instant before = Instant.parse("2026-03-28T03:00:00Z");
        assertEquals(Duration.ofHours(23), Duration.between(before, periods.nextDay(before).toInstant()));
    }
    @Test void repeatedHourDoesNotResetTwice() {
        Periods early = new Periods(ZoneId.of("Europe/Warsaw"), LocalTime.of(2, 30));
        assertEquals(LocalDate.of(2026, 10, 25), early.day(Instant.parse("2026-10-25T00:45:00Z")));
        assertEquals(LocalDate.of(2026, 10, 25), early.day(Instant.parse("2026-10-25T01:15:00Z")));
    }
}
