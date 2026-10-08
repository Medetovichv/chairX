package kg.chairx.finance;

import kg.chairx.finance.application.CashFlowService;
import kg.chairx.finance.infrastructure.CashFlowRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashFlowServiceTests {

    @Mock
    CashFlowRepository repository;

    @InjectMocks
    CashFlowService service;

    private static final Instant FROM =
            Instant.parse("2026-09-30T18:00:00Z");

    private static final Instant TO =
            Instant.parse("2026-10-31T18:00:00Z");

    private static final LocalDate FROM_DATE =
            LocalDate.of(2026, 10, 1);

    private static final LocalDate TO_DATE =
            LocalDate.of(2026, 11, 1);

    @Test
    void calculatesCashFlowIncludingExpenses() {
        when(repository.payments(FROM, TO))
                .thenReturn(new BigDecimal("200000"));
        when(repository.paymentCorrections(FROM, TO))
                .thenReturn(BigDecimal.ZERO);

        when(repository.refunds(FROM, TO))
                .thenReturn(new BigDecimal("15000"));

        when(repository.exchangePayments(FROM, TO))
                .thenReturn(new BigDecimal("5000"));

        when(repository.exchangeRefunds(FROM, TO))
                .thenReturn(new BigDecimal("2000"));

        when(repository.operatingExpenses(FROM_DATE, TO_DATE))
                .thenReturn(new BigDecimal("30000"));

        var result = service.summary(FROM, TO);

        assertThat(result.payments())
                .isEqualByComparingTo("200000");

        assertThat(result.operatingExpenses())
                .isEqualByComparingTo("30000");

        assertThat(result.totalIn())
                .isEqualByComparingTo("205000");

        assertThat(result.totalOut())
                .isEqualByComparingTo("47000");

        assertThat(result.netCashFlow())
                .isEqualByComparingTo("158000");
    }

    @Test
    void negativeCashFlowIsAllowed() {
        when(repository.payments(FROM, TO))
                .thenReturn(new BigDecimal("10000"));
        when(repository.paymentCorrections(FROM, TO))
                .thenReturn(BigDecimal.ZERO);

        when(repository.refunds(FROM, TO))
                .thenReturn(BigDecimal.ZERO);

        when(repository.exchangePayments(FROM, TO))
                .thenReturn(BigDecimal.ZERO);

        when(repository.exchangeRefunds(FROM, TO))
                .thenReturn(BigDecimal.ZERO);

        when(repository.operatingExpenses(FROM_DATE, TO_DATE))
                .thenReturn(new BigDecimal("15000"));

        var result = service.summary(FROM, TO);

        assertThat(result.netCashFlow())
                .isEqualByComparingTo("-5000");
    }

    @Test
    void emptyPeriodReturnsZero() {
        when(repository.payments(FROM, TO))
                .thenReturn(BigDecimal.ZERO);
        when(repository.paymentCorrections(FROM, TO))
                .thenReturn(BigDecimal.ZERO);

        when(repository.refunds(FROM, TO))
                .thenReturn(BigDecimal.ZERO);

        when(repository.exchangePayments(FROM, TO))
                .thenReturn(BigDecimal.ZERO);

        when(repository.exchangeRefunds(FROM, TO))
                .thenReturn(BigDecimal.ZERO);

        when(repository.operatingExpenses(FROM_DATE, TO_DATE))
                .thenReturn(BigDecimal.ZERO);

        var result = service.summary(FROM, TO);

        assertThat(result.totalIn())
                .isEqualByComparingTo("0");

        assertThat(result.totalOut())
                .isEqualByComparingTo("0");

        assertThat(result.netCashFlow())
                .isEqualByComparingTo("0");
    }

    @Test
    void rejectsInvalidPeriod() {
        assertThatThrownBy(() -> service.summary(TO, FROM))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.summary(FROM, FROM))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPartialDayPeriod() {
        Instant invalidFrom =
                Instant.parse("2026-10-01T00:00:00Z");

        assertThatThrownBy(() -> service.summary(invalidFrom, TO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}