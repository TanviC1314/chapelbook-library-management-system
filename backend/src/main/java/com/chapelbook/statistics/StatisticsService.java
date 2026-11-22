package com.chapelbook.statistics;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatisticsService {

    private final StatisticsRepository repository;
    private final Clock clock;

    public StatisticsService(StatisticsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Live dashboard numbers. Read-only, and every figure comes straight from the database. */
    @Transactional(readOnly = true)
    public OverviewResponse overview() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Instant startOfToday = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfTomorrow = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new OverviewResponse(now, repository.users(), repository.catalogue(),
                repository.circulation(now, startOfToday, startOfTomorrow), repository.reservations(),
                repository.fines(), repository.seats(now, startOfToday, startOfTomorrow));
    }
}
