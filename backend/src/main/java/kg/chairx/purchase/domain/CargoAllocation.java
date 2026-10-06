package kg.chairx.purchase.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** BY_QUANTITY: cumulative rounding conserves the exact cargo total at both allocation levels. */
public final class CargoAllocation {
    private CargoAllocation() { }

    public static List<BigDecimal> byQuantity(BigDecimal cargo, List<Long> quantities) {
        if (cargo.signum() < 0 || quantities.isEmpty() || quantities.stream().anyMatch(q -> q <= 0)) {
            throw new IllegalArgumentException("Некорректные данные распределения карго");
        }
        BigInteger total = quantities.stream().map(BigInteger::valueOf).reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger cumulative = BigInteger.ZERO;
        BigDecimal assigned = BigDecimal.ZERO;
        List<BigDecimal> result = new ArrayList<>();
        for (long quantity : quantities) {
            cumulative = cumulative.add(BigInteger.valueOf(quantity));
            BigDecimal target = cargo.multiply(new BigDecimal(cumulative)).divide(new BigDecimal(total), 2, RoundingMode.HALF_UP);
            result.add(target.subtract(assigned));
            assigned = target;
        }
        return List.copyOf(result);
    }

    public static BigDecimal receiptShare(BigDecimal itemCargo, long ordered, long receivedBefore, long quantity) {
        if (ordered <= 0 || receivedBefore < 0 || quantity <= 0 || quantity > ordered - receivedBefore) {
            throw new IllegalArgumentException("Некорректное количество поступления");
        }
        BigDecimal divisor = BigDecimal.valueOf(ordered);
        BigDecimal before = itemCargo.multiply(BigDecimal.valueOf(receivedBefore)).divide(divisor, 2, RoundingMode.HALF_UP);
        BigDecimal after = itemCargo.multiply(BigDecimal.valueOf(receivedBefore + quantity)).divide(divisor, 2, RoundingMode.HALF_UP);
        return after.subtract(before);
    }

    public static BigDecimal unitCost(BigDecimal purchaseUnitCost, BigDecimal itemCargo, long ordered) {
        return purchaseUnitCost.add(itemCargo.divide(BigDecimal.valueOf(ordered), 6, RoundingMode.HALF_UP));
    }
}
