package kg.chairx.sale.application;

import kg.chairx.sale.api.CreateSaleRequest;
import kg.chairx.sale.domain.FulfillmentType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Keeps the existing canonical sale request fingerprint format unchanged. */
@Component
public class SaleRequestFingerprint {
    private final JsonMapper mapper;

    public SaleRequestFingerprint(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public String fingerprint(
            CreateSaleRequest request
    ) {
        List<FingerprintSaleItem> sorted =
                request.items()
                        .stream()
                        .map(item ->
                                new FingerprintSaleItem(
                                        item.productVariantId(),
                                        item.warehouseId(),
                                        item.quantity(),
                                        normalizeMoney(
                                                item.unitSalePrice()
                                        )
                                )
                        )
                        .sorted(
                                Comparator
                                        .comparing(
                                                (FingerprintSaleItem item) ->
                                                        item.productVariantId()
                                                                .toString()
                                        )
                                        .thenComparing(
                                                item ->
                                                        item.warehouseId()
                                                                .toString()
                                        )
                        )
                        .toList();

        String json = mapper.writeValueAsString(
                new SaleContent(
                        request.customerId(),
                        request.fulfillmentType(),
                        sorted
                )
        );

        try {
            return HexFormat.of().formatHex(
                    MessageDigest
                            .getInstance("SHA-256")
                            .digest(
                                    json.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 unavailable",
                    exception
            );
        }
    }

    private BigDecimal normalizeMoney(
            BigDecimal value
    ) {
        return value.setScale(
                0,
                RoundingMode.UNNECESSARY
        );
    }


    private record FingerprintSaleItem(
            UUID productVariantId,
            UUID warehouseId,
            long quantity,
            BigDecimal unitSalePrice
    ) {
    }

    private record SaleContent(
            UUID customerId,
            FulfillmentType fulfillmentType,
            List<FingerprintSaleItem> items
    ) {
    }
}
