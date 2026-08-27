package com.booking.controller;

import com.booking.dto.reservation.ReservationRequest;
import com.booking.dto.reservation.ReservationResponse;
import com.booking.dto.reservation.ReservationUpdateRequest;
import com.booking.entity.ReservationStatus;
import com.booking.entity.User;
import com.booking.service.ReservationService;
import com.booking.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "Reservation booking. USER sees/manages only their own; ADMIN sees/manages all.")
@SecurityRequirement(name = "bearerAuth")
public class ReservationController {

    private final ReservationService reservationService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "List reservations with filtering, pagination & sorting. " +
            "ADMIN sees all reservations; USER sees only their own.")
    public ResponseEntity<Page<ReservationResponse>> getReservations(
            @Parameter(description = "Filter by reservation status") @RequestParam(required = false) ReservationStatus status,
            @Parameter(description = "Minimum price filter") @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Maximum price filter") @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 10, sort = "id") Pageable pageable) {

        User currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(reservationService.getReservations(currentUser, status, minPrice, maxPrice, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single reservation by id (owner or ADMIN only)")
    public ResponseEntity<ReservationResponse> getReservationById(@PathVariable Long id) {
        User currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(reservationService.getReservationById(id, currentUser));
    }

    @PostMapping
    @Operation(summary = "Create a reservation. The owner is always the authenticated JWT user, never a client-supplied field.")
    public ResponseEntity<ReservationResponse> createReservation(@Valid @RequestBody ReservationRequest request) {
        User currentUser = userService.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.createReservation(request, currentUser));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Fully update a reservation, including status - ADMIN only")
    public ResponseEntity<ReservationResponse> updateReservation(@PathVariable Long id, @Valid @RequestBody ReservationUpdateRequest request) {
        return ResponseEntity.ok(reservationService.updateReservation(id, request));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel a reservation (owner or ADMIN only)")
    public ResponseEntity<ReservationResponse> cancelReservation(@PathVariable Long id) {
        User currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(reservationService.cancelReservation(id, currentUser));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Permanently delete a reservation - ADMIN only")
    public ResponseEntity<Void> deleteReservation(@PathVariable Long id) {
        reservationService.deleteReservation(id);
        return ResponseEntity.noContent().build();
    }
}
