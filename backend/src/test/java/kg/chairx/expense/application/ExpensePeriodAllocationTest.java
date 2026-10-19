package kg.chairx.expense.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpensePeriodAllocationTest {
    @Test void exactMonthlyCostAllocatesEveryDayWithoutPostingMoney() {
        var from = LocalDate.of(2026, 11, 1);
        var allocation = ExpensePeriodAllocation.allocate(
                new BigDecimal("3000"), from, from.plusDays(30));
        assertThat(allocation).hasSize(30);
        assertThat(allocation.values()).allMatch(day -> day.compareTo(new BigDecimal("100")) == 0);
        assertThat(allocation.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("3000");
    }

    @Test void roundingRemainderBelongsToLastDayAndNeverGetsLost() {
        var from = LocalDate.of(2026, 10, 30);
        var allocation = ExpensePeriodAllocation.allocate(
                new BigDecimal("100"), from, from.plusDays(3));
        assertThat(allocation.values()).containsExactly(
                new BigDecimal("33"), new BigDecimal("33"), new BigDecimal("34"));
        assertThat(allocation.keySet()).containsExactly(from, from.plusDays(1), from.plusDays(2));
    }

    @Test void crossingMonthDoesNotChangePeriodOrTotal() {
        var from = LocalDate.of(2026, 12, 31);
        var allocation = ExpensePeriodAllocation.allocate(
                new BigDecimal("5"), from, from.plusDays(2));
        assertThat(allocation.keySet()).containsExactly(
                LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1));
        assertThat(allocation.values()).containsExactly(
                new BigDecimal("2"), new BigDecimal("3"));
    }

    @Test void invalidPeriodsAndAmountsAreRejected() {
        var date = LocalDate.of(2026, 10, 19);
        assertThatThrownBy(() -> ExpensePeriodAllocation.allocate(BigDecimal.ZERO, date, date.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExpensePeriodAllocation.allocate(new BigDecimal("1.25"), date, date.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExpensePeriodAllocation.allocate(BigDecimal.ONE, date, date))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExpensePeriodAllocation.allocate(BigDecimal.ONE, date, date.plusDays(367)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
