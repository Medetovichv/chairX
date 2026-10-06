package kg.chairx.purchase.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CargoAllocationTests {

    @Test
    void distributesCargoWithoutLosingCents() {
        var allocation = CargoAllocation.byQuantity(
                new BigDecimal("1000.00"),
                List.of(1L, 1L, 1L)
        );

        assertThat(allocation)
                .containsExactly(
                        new BigDecimal("333.33"),
                        new BigDecimal("333.34"),
                        new BigDecimal("333.33")
                );

        var total = allocation.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(total)
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void partialReceiptsPreserveExactItemCargoTotal() {
        var itemCargo = new BigDecimal("1000.00");

        var first = CargoAllocation.receiptShare(
                itemCargo,
                3,
                0,
                1
        );

        var second = CargoAllocation.receiptShare(
                itemCargo,
                3,
                1,
                1
        );

        var third = CargoAllocation.receiptShare(
                itemCargo,
                3,
                2,
                1
        );

        assertThat(first).isEqualByComparingTo("333.33");
        assertThat(second).isEqualByComparingTo("333.34");
        assertThat(third).isEqualByComparingTo("333.33");

        assertThat(first.add(second).add(third))
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void unitCostUsesMaximumTwoDecimalPlaces() {
        var unitCost = CargoAllocation.unitCost(
                new BigDecimal("5000.00"),
                new BigDecimal("1000.00"),
                3
        );

        assertThat(unitCost)
                .isEqualByComparingTo("5333.33");

        assertThat(unitCost.scale())
                .isEqualTo(2);
    }
}