package kg.chairx.delivery.api;

import java.time.LocalDate;

/** A null date clears a non-final delivery's schedule. */
public record UpdatePlannedDeliveryDateRequest(LocalDate plannedDeliveryDate) {
}
