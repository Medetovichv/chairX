package kg.chairx.purchase.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * BY_QUANTITY:
 * денежные суммы рассчитываются максимум с двумя знаками после запятой.
 * Cumulative rounding гарантирует сохранение общей суммы карго
 * при распределении между позициями и частичными поступлениями.
 */
public final class CargoAllocation {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private CargoAllocation() {
    }

    public static List<BigDecimal> byQuantity(
            BigDecimal cargo,
            List<Long> quantities
    ) {
        if (cargo.signum() < 0
                || quantities.isEmpty()
                || quantities.stream().anyMatch(quantity -> quantity <= 0)) {
            throw new IllegalArgumentException(
                    "Некорректные данные распределения карго"
            );
        }

        BigInteger total = quantities.stream()
                .map(BigInteger::valueOf)
                .reduce(BigInteger.ZERO, BigInteger::add);

        BigInteger cumulative = BigInteger.ZERO;
        BigDecimal assigned = BigDecimal.ZERO;

        List<BigDecimal> result = new ArrayList<>();

        for (long quantity : quantities) {
            cumulative = cumulative.add(
                    BigInteger.valueOf(quantity)
            );

            BigDecimal target = cargo
                    .multiply(new BigDecimal(cumulative))
                    .divide(
                            new BigDecimal(total),
                            MONEY_SCALE,
                            MONEY_ROUNDING
                    );

            result.add(
                    target.subtract(assigned)
            );

            assigned = target;
        }

        return List.copyOf(result);
    }

    public static BigDecimal receiptShare(
            BigDecimal itemCargo,
            long ordered,
            long receivedBefore,
            long quantity
    ) {
        if (ordered <= 0
                || receivedBefore < 0
                || quantity <= 0
                || quantity > ordered - receivedBefore) {
            throw new IllegalArgumentException(
                    "Некорректное количество поступления"
            );
        }

        BigDecimal divisor = BigDecimal.valueOf(ordered);

        BigDecimal before = itemCargo
                .multiply(BigDecimal.valueOf(receivedBefore))
                .divide(
                        divisor,
                        MONEY_SCALE,
                        MONEY_ROUNDING
                );

        BigDecimal after = itemCargo
                .multiply(BigDecimal.valueOf(receivedBefore + quantity))
                .divide(
                        divisor,
                        MONEY_SCALE,
                        MONEY_ROUNDING
                );

        return after.subtract(before);
    }

    public static BigDecimal unitCost(
            BigDecimal purchaseUnitCost,
            BigDecimal itemCargo,
            long ordered
    ) {
        if (purchaseUnitCost == null
                || itemCargo == null
                || purchaseUnitCost.signum() < 0
                || itemCargo.signum() < 0
                || ordered <= 0) {
            throw new IllegalArgumentException(
                    "Некорректные данные себестоимости"
            );
        }

        BigDecimal cargoPerUnit = itemCargo.divide(
                BigDecimal.valueOf(ordered),
                MONEY_SCALE,
                MONEY_ROUNDING
        );

        return purchaseUnitCost
                .add(cargoPerUnit)
                .setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}