package kg.chairx.exchange.application;

import kg.chairx.finance.application.FinancePostingService;
import kg.chairx.exchange.domain.Exchange;
import kg.chairx.exchange.domain.ExchangeStatus;
import kg.chairx.exchange.infrastructure.ExchangeRepository;
import kg.chairx.exchange.infrastructure.ExchangeSettlementRepository;
import kg.chairx.exchange.infrastructure.ExchangeSettlementRepository.Settlement;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ExchangeSettlementService {

    private final FinancePostingService finance;
    private final ExchangeRepository exchanges;
    private final ExchangeSettlementRepository settlements;

    public ExchangeSettlementService(
            FinancePostingService finance,
            ExchangeRepository exchanges,
            ExchangeSettlementRepository settlements
    ) {
        this.finance = finance;
        this.exchanges = exchanges;
        this.settlements = settlements;
    }

    @Transactional
    public Settlement settle(
            UUID exchangeId, UUID idempotencyKey, String direction,
            String method, BigDecimal amount, String reference, String actor
    ) {
        throw rule("EXCHANGE_SETTLEMENT_RETIRED",
                "Расчёты старых обменов доступны только для просмотра");
    }

    @Transactional(readOnly = true)
    public List<Settlement> getSettlements(UUID exchangeId) {
        if (exchangeId == null) {
            throw invalid("Exchange ID is required");
        }

        exchanges.find(exchangeId)
                .orElseThrow(ExchangeNotFoundException::new);

        return settlements.findByExchange(exchangeId);
    }

    private static ExchangeRuleViolationException invalid(String message) {
        return rule("INVALID_EXCHANGE_SETTLEMENT", message);
    }

    private static ExchangeRuleViolationException rule(
            String code,
            String message
    ) {
        return new ExchangeRuleViolationException(code, message);
    }

    private static String fingerprint(
            UUID exchangeId,
            String direction,
            String method,
            BigDecimal amount,
            String reference
    ) {
        String canonical = String.join(
                "\n",
                exchangeId.toString(),
                direction,
                method,
                amount.toBigIntegerExact().toString(),
                reference == null ? "" : reference
        );

        try {
            byte[] hash = MessageDigest
                    .getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 unavailable",
                    exception
            );
        }
    }
}