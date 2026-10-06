package kg.chairx.inventory.domain;

import java.util.UUID;

/** Immutable stock snapshot; available is always derived from its three counters. */
public record InventoryBalance(
        UUID warehouseId,
        UUID productVariantId,
        long onHand,
        long reserved,
        long blocked
) {

    public InventoryBalance {
        if (onHand < 0
                || reserved < 0
                || blocked < 0
                || reserved > onHand
                || blocked > onHand - reserved) {
            throw new IllegalArgumentException(
                    "Недопустимое состояние складского остатка"
            );
        }
    }

    public long available() {
        return onHand - reserved - blocked;
    }

    public InventoryBalance reserve(long quantity) {
        requirePositive(quantity);

        if (quantity > available()) {
            throw new InsufficientStockException(
                    quantity,
                    available()
            );
        }

        final long changed;

        try {
            changed = Math.addExact(reserved, quantity);
        } catch (ArithmeticException exception) {
            throw new StockQuantityLimitException();
        }

        return new InventoryBalance(
                warehouseId,
                productVariantId,
                onHand,
                changed,
                blocked
        );
    }

    public InventoryBalance releaseReservation(long quantity) {
        requirePositive(quantity);

        if (quantity > reserved) {
            throw new IllegalArgumentException(
                    "Нельзя освободить больше товара, чем зарезервировано"
            );
        }

        return new InventoryBalance(
                warehouseId,
                productVariantId,
                onHand,
                reserved - quantity,
                blocked
        );
    }

    public InventoryBalance block(long quantity) {
        requirePositive(quantity);

        if (quantity > available()) {
            throw new InsufficientStockException(
                    quantity,
                    available()
            );
        }

        final long changed;

        try {
            changed = Math.addExact(blocked, quantity);
        } catch (ArithmeticException exception) {
            throw new StockQuantityLimitException();
        }

        return new InventoryBalance(
                warehouseId,
                productVariantId,
                onHand,
                reserved,
                changed
        );
    }

    public InventoryBalance unblock(long quantity) {
        requirePositive(quantity);

        if (quantity > blocked) {
            throw new IllegalArgumentException(
                    "Нельзя разблокировать больше товара, чем заблокировано"
            );
        }

        return new InventoryBalance(
                warehouseId,
                productVariantId,
                onHand,
                reserved,
                blocked - quantity
        );
    }

    public InventoryBalance apply(
            StockMovementType type,
            long quantity
    ) {
        requirePositive(quantity);

        final long changed;

        if (type.isIncoming()) {
            try {
                changed = Math.addExact(onHand, quantity);
            } catch (ArithmeticException exception) {
                throw new StockQuantityLimitException();
            }
        } else {
            if (quantity > available()) {
                throw new InsufficientStockException(
                        quantity,
                        available()
                );
            }

            changed = onHand - quantity;
        }

        return new InventoryBalance(
                warehouseId,
                productVariantId,
                changed,
                reserved,
                blocked
        );
    }

    private static void requirePositive(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Количество должно быть положительным"
            );
        }
    }
}