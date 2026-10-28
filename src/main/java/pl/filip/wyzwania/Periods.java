package pl.filip.wyzwania;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

public record Periods(ZoneId zone, LocalTime reset) {
    public LocalDate day(Instant now) {
        ZonedDateTime local = now.atZone(zone);
        LocalDate date = local.toLocalDate();
        // Compare instants so a reset during a repeated DST hour runs only once.
        return now.isBefore(date.atTime(reset).atZone(zone).toInstant()) ? date.minusDays(1) : date;
    }
    public LocalDate week(Instant now) { return day(now).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }
    public String month(Instant now) { return YearMonth.from(day(now)).toString(); }
    public ZonedDateTime nextDay(Instant now) { return day(now).plusDays(1).atTime(reset).atZone(zone); }
    public ZonedDateTime nextWeek(Instant now) { return week(now).plusWeeks(1).atTime(reset).atZone(zone); }
}
