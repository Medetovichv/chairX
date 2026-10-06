package kg.chairx.sale.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItem(
        UUID id,
        UUID saleId,
        UUID productVariantId,
        UUID warehouseId,
        long quantity,
        BigDecimal unitSalePrice
) {

    public SaleItem {
        if (id == null) {
            throw new IllegalArgumentException(
                    "Sale item id обязателен"
            );
        }

        if (saleId == null) {
            throw new IllegalArgumentException(
                    "Sale id обязателен"
            );
        }

        if (productVariantId == null) {
            throw new IllegalArgumentException(
                    "Вариант товара обязателен"
            );
        }

        if (warehouseId == null) {
            throw new IllegalArgumentException(
                    "Склад обязателен"
            );
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Количество должно быть положительным"
            );
        }

        if (unitSalePrice == null) {
            throw new IllegalArgumentException(
                    "Цена продажи обязательна"
            );
        }

        if (unitSalePrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "Цена продажи не может быть отрицательной"
            );
        }
    }

    public BigDecimal total() {
        return unitSalePrice.multiply(
                BigDecimal.valueOf(quantity)
        );
    }
}