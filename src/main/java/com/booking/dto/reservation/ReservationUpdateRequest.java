package com.booking.dto.reservation;

import com.booking.entity.ReservationStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Used by ADMIN to update any field of a reservation, including status.
 * All fields are optional; only non-null fields are applied.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservationUpdateRequest {

    private Long resourceId;

    @Future(message = "Start time must be in the future")
    private LocalDateTime startTime;

    @Future(message = "End time must be in the future")
    private LocalDateTime endTime;

    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private BigDecimal price;

    private ReservationStatus status;
}
