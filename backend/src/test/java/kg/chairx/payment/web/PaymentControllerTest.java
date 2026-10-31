package kg.chairx.payment.web;

import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.api.PaymentResponse;
import kg.chairx.payment.application.PaymentService;
import kg.chairx.payment.application.PickupCheckoutService;
import kg.chairx.payment.domain.PaymentMethod;
import kg.chairx.sale.api.SaleResponse;
import kg.chairx.sale.application.SaleService;
import kg.chairx.sale.domain.FulfillmentType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PaymentControllerTest {

    @Test
    void genericPaymentEndpointUsesAtomicCheckoutForPickup() {
        var payments = mock(PaymentService.class);
        var checkout = mock(PickupCheckoutService.class);
        var sales = mock(SaleService.class);
        UUID saleId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        var sale = mock(SaleResponse.class);
        when(sale.fulfillmentType()).thenReturn(FulfillmentType.SELF_PICKUP);
        when(sales.get(saleId)).thenReturn(sale);
        var request = new CreatePaymentRequest(saleId, PaymentMethod.CASH, null, null);
        var paid = mock(PaymentResponse.class);
        when(paid.id()).thenReturn(paymentId);
        when(checkout.checkout(request)).thenReturn(paid);

        var response = new PaymentController(payments, checkout, sales).create(request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isSameAs(paid);
        verify(checkout).checkout(request);
        verify(payments, never()).create(any());
    }

    @Test
    void genericPaymentEndpointKeepsDeliveryPaymentFlow() {
        var payments = mock(PaymentService.class);
        var checkout = mock(PickupCheckoutService.class);
        var sales = mock(SaleService.class);
        UUID saleId = UUID.randomUUID();
        var sale = mock(SaleResponse.class);
        when(sale.fulfillmentType()).thenReturn(FulfillmentType.CITY_DELIVERY);
        when(sales.get(saleId)).thenReturn(sale);
        var request = new CreatePaymentRequest(saleId, PaymentMethod.TRANSFER, null, null);
        var paid = mock(PaymentResponse.class);
        when(paid.id()).thenReturn(UUID.randomUUID());
        when(payments.create(request)).thenReturn(paid);

        var response = new PaymentController(payments, checkout, sales).create(request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        verify(payments).create(request);
        verifyNoInteractions(checkout);
    }
}
