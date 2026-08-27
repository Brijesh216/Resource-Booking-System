package com.booking.service;

import com.booking.dto.reservation.ReservationRequest;
import com.booking.dto.reservation.ReservationResponse;
import com.booking.dto.reservation.ReservationUpdateRequest;
import com.booking.entity.*;
import com.booking.exception.BadRequestException;
import com.booking.exception.ResourceNotFoundException;
import com.booking.exception.UnauthorizedAccessException;
import com.booking.repository.ReservationRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ResourceService resourceService;

    @Transactional
    public ReservationResponse createReservation(ReservationRequest request, User currentUser) {
        Resource resource = resourceService.findResourceOrThrow(request.getResourceId());

        if (!resource.isAvailable()) {
            throw new BadRequestException("Resource '" + resource.getName() + "' is not currently available for booking");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BadRequestException("End time must be after start time");
        }

        Reservation reservation = Reservation.builder()
                .resource(resource)
                .user(currentUser) // owner is ALWAYS the authenticated JWT principal, never client-supplied
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .price(request.getPrice())
                .status(ReservationStatus.PENDING)
                .build();

        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional(readOnly = true)
    public Page<ReservationResponse> getReservations(User currentUser, ReservationStatus status,
                                                       BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        Specification<Reservation> spec = buildSpecification(currentUser, status, minPrice, maxPrice);
        return reservationRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(Long id, User currentUser) {
        Reservation reservation = findReservationOrThrow(id);
        assertOwnerOrAdmin(reservation, currentUser);
        return toResponse(reservation);
    }

    /**
     * Full update - ADMIN only (enforced at controller level via @PreAuthorize).
     */
    @Transactional
    public ReservationResponse updateReservation(Long id, ReservationUpdateRequest request) {
        Reservation reservation = findReservationOrThrow(id);

        if (request.getResourceId() != null) {
            reservation.setResource(resourceService.findResourceOrThrow(request.getResourceId()));
        }
        if (request.getStartTime() != null) {
            reservation.setStartTime(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            reservation.setEndTime(request.getEndTime());
        }
        if (reservation.getEndTime() != null && reservation.getStartTime() != null
                && !reservation.getEndTime().isAfter(reservation.getStartTime())) {
            throw new BadRequestException("End time must be after start time");
        }
        if (request.getPrice() != null) {
            reservation.setPrice(request.getPrice());
        }
        if (request.getStatus() != null) {
            reservation.setStatus(request.getStatus());
        }

        return toResponse(reservationRepository.save(reservation));
    }

    /**
     * Cancel a reservation - USER may cancel only their own PENDING/CONFIRMED reservation; ADMIN may cancel any.
     */
    @Transactional
    public ReservationResponse cancelReservation(Long id, User currentUser) {
        Reservation reservation = findReservationOrThrow(id);
        assertOwnerOrAdmin(reservation, currentUser);

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new BadRequestException("Reservation is already cancelled");
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional
    public void deleteReservation(Long id) {
        Reservation reservation = findReservationOrThrow(id);
        reservationRepository.delete(reservation);
    }

    private Reservation findReservationOrThrow(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));
    }

    private void assertOwnerOrAdmin(Reservation reservation, User currentUser) {
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        boolean isOwner = reservation.getUser().getId().equals(currentUser.getId());
        if (!isAdmin && !isOwner) {
            throw new UnauthorizedAccessException("You are not allowed to access this reservation");
        }
    }

    private Specification<Reservation> buildSpecification(User currentUser, ReservationStatus status,
                                                            BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // USER can only ever see their own reservations - enforced here, not trusted from request params
            if (currentUser.getRole() != Role.ADMIN) {
                predicates.add(cb.equal(root.get("user").get("id"), currentUser.getId()));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private ReservationResponse toResponse(Reservation r) {
        return ReservationResponse.builder()
                .id(r.getId())
                .resourceId(r.getResource().getId())
                .resourceName(r.getResource().getName())
                .userId(r.getUser().getId())
                .username(r.getUser().getUsername())
                .startTime(r.getStartTime())
                .endTime(r.getEndTime())
                .price(r.getPrice())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
