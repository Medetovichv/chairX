package kg.chairx.finance;

import kg.chairx.finance.application.DailyClosingAccessPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.*;

class DailyClosingAccessPolicyTest {
    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 10);

    private static Instant at(int day, int hour, int minute, int second) {
        return LocalDateTime.of(2026, 10, day, hour, minute, second)
                .atZone(BISHKEK).toInstant();
    }

    private static DailyClosingAccessPolicy policy(Instant time) {
        return new DailyClosingAccessPolicy(Clock.fixed(time, BISHKEK));
    }

    @Test
    void normalWindowIncludesTodayAndYesterdayOnlyBefore1300() {
        assertThat(policy(at(10, 0, 0, 0)).normalWriteWindow(DAY, at(10, 0, 0, 0))).isTrue();
        assertThat(policy(at(11, 12, 59, 59)).normalWriteWindow(DAY, at(11, 12, 59, 59))).isTrue();
        assertThat(policy(at(11, 13, 0, 0)).normalWriteWindow(DAY, at(11, 13, 0, 0))).isFalse();
        assertThat(policy(at(12, 0, 0, 0)).normalWriteWindow(DAY, at(12, 0, 0, 0))).isFalse();
    }

    @Test
    void managerCanUnlockThroughFifthCalendarDayButNotSixth() {
        var p = policy(at(15, 23, 59, 59));
        assertThat(p.managerMayUnlock(DAY, at(15, 23, 59, 59))).isTrue();
        assertThat(p.managerMayUnlock(DAY, at(16, 0, 0, 0))).isFalse();
        assertThat(p.adminMayUnlock(DAY, at(16, 0, 0, 0))).isTrue();
        assertThat(p.managerMayUnlock(DAY.plusDays(10), at(15, 23, 59, 59))).isFalse();
    }

    @Test
    void managerGrantAt2330ExpiresOnDaySixBoundary() {
        var p = policy(at(15, 23, 30, 0));
        Instant expiration = p.expiry(DAY, at(15, 23, 30, 0), false);
        assertThat(expiration).isEqualTo(at(16, 0, 0, 0));
        assertThat(p.grantActive(at(15, 23, 59, 59), expiration, null)).isTrue();
        assertThat(p.grantActive(at(16, 0, 0, 0), expiration, null)).isFalse();
    }

    @Test
    void adminGrantCanRunForFullHourOnOlderReport() {
        var p = policy(at(25, 15, 20, 0));
        Instant expiration = p.expiry(DAY, at(25, 15, 20, 0), true);
        assertThat(expiration).isEqualTo(at(25, 16, 20, 0));
        assertThat(p.grantActive(at(25, 16, 19, 59), expiration, null)).isTrue();
        assertThat(p.grantActive(at(25, 16, 20, 0), expiration, null)).isFalse();
        assertThat(p.grantActive(at(25, 15, 30, 0), expiration, at(25, 15, 25, 0))).isFalse();
    }

    @Test
    void expiredManagerCannotReissueAfterCutoff() {
        var p = policy(at(16, 0, 0, 0));
        assertThatThrownBy(() -> p.expiry(DAY, p.now(), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void systemTimezoneDoesNotChangeDecisions() {
        Instant instant = at(11, 12, 59, 59);
        var utcPolicy = new DailyClosingAccessPolicy(Clock.fixed(instant, ZoneId.of("UTC")));
        assertThat(utcPolicy.today()).isEqualTo(DAY.plusDays(1));
        assertThat(utcPolicy.normalWriteWindow(DAY, utcPolicy.now())).isTrue();
    }
}
