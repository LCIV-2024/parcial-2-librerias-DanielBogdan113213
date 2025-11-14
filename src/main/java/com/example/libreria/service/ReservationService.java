package com.example.libreria.service;

import com.example.libreria.dto.ReservationRequestDTO;
import com.example.libreria.dto.ReservationResponseDTO;
import com.example.libreria.dto.ReturnBookRequestDTO;
import com.example.libreria.model.Book;
import com.example.libreria.model.Reservation;
import com.example.libreria.model.User;
import com.example.libreria.repository.BookRepository;
import com.example.libreria.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private static final BigDecimal LATE_FEE_PERCENTAGE = new BigDecimal("0.15");

    private final ReservationRepository reservationRepository;
    private final BookRepository bookRepository;
    private final BookService bookService;
    private final UserService userService;

    @Transactional
    public ReservationResponseDTO createReservation(ReservationRequestDTO requestDTO) {
        log.info("Creando reserva para usuario {} y libro {}", requestDTO.getUserId(), requestDTO.getBookExternalId());

        User user = userService.getUserEntity(requestDTO.getUserId());

        Book book = bookRepository.findByExternalId(requestDTO.getBookExternalId())
                .orElseThrow(() -> new RuntimeException("Libro no encontrado con ID externo: " + requestDTO.getBookExternalId()));

        if (book.getAvailableQuantity() <= 0) {
            throw new RuntimeException("No hay copias disponibles del libro: " + book.getTitle());
        }

        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setBook(book);
        reservation.setRentalDays(requestDTO.getRentalDays());
        reservation.setStartDate(requestDTO.getStartDate());
        reservation.setExpectedReturnDate(requestDTO.getStartDate().plusDays(requestDTO.getRentalDays()));
        reservation.setDailyRate(book.getPrice());

        // Calcular la tarifa total
        BigDecimal totalFee = calculateTotalFee(book.getPrice(), requestDTO.getRentalDays());
        reservation.setTotalFee(totalFee);

        reservation.setLateFee(BigDecimal.ZERO);
        reservation.setStatus(Reservation.ReservationStatus.ACTIVE);

        Reservation savedReservation = reservationRepository.save(reservation);
        bookService.decreaseAvailableQuantity(book.getExternalId());

        log.info("Reserva creada exitosamente con id: {}", savedReservation.getId());
        return convertToDTO(savedReservation);
    }

    @Transactional
    public ReservationResponseDTO returnBook(Long reservationId, ReturnBookRequestDTO returnRequest) {
        log.info("Procesando devolucion de reserva {}", reservationId);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada con ID: " + reservationId));

        if (reservation.getStatus() != Reservation.ReservationStatus.ACTIVE) {
            throw new RuntimeException("La reserva ya fue devuelta");
        }

        LocalDate returnDate = returnRequest.getReturnDate();

        if (returnDate.isBefore(reservation.getStartDate())) {
            throw new RuntimeException("La fecha de devolución no puede ser anterior a la fecha de inicio de la reserva");
        }

        reservation.setActualReturnDate(returnDate);

        // Verificar si hay retraso y calcular multa
        if (returnDate.isAfter(reservation.getExpectedReturnDate())) {
            long daysLate = ChronoUnit.DAYS.between(reservation.getExpectedReturnDate(), returnDate);
            BigDecimal lateFee = calculateLateFee(reservation.getBook().getPrice(), daysLate);
            reservation.setLateFee(lateFee);
            reservation.setTotalFee(reservation.getTotalFee().add(lateFee));
            reservation.setStatus(Reservation.ReservationStatus.OVERDUE);
            log.info("Libro devuelto {} dias tarde. Multa: {}", daysLate, lateFee);
        } else {
            reservation.setStatus(Reservation.ReservationStatus.RETURNED);
            log.info("Libro devuelto a tiempo");
        }

        Reservation updatedReservation = reservationRepository.save(reservation);
        bookService.increaseAvailableQuantity(reservation.getBook().getExternalId());

        return convertToDTO(updatedReservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponseDTO getReservationById(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada con ID: " + id));
        return convertToDTO(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getAllReservations() {
        return reservationRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getReservationsByUserId(Long userId) {
        return reservationRepository.findByUserId(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getActiveReservations() {
        return reservationRepository.findByStatus(Reservation.ReservationStatus.ACTIVE).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getOverdueReservations() {
        LocalDate today = LocalDate.now();
        return reservationRepository.findOverdueReservations(today).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private BigDecimal calculateTotalFee(BigDecimal dailyRate, Integer rentalDays) {
        return dailyRate.multiply(new BigDecimal(rentalDays))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // Calcula la multa: 15% del precio por cada dia de demora
    private BigDecimal calculateLateFee(BigDecimal bookPrice, long daysLate) {
        return bookPrice.multiply(LATE_FEE_PERCENTAGE)
                .multiply(new BigDecimal(daysLate))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private ReservationResponseDTO convertToDTO(Reservation reservation) {
        ReservationResponseDTO dto = new ReservationResponseDTO();
        dto.setId(reservation.getId());
        dto.setUserId(reservation.getUser().getId());
        dto.setUserName(reservation.getUser().getName());
        dto.setBookExternalId(reservation.getBook().getExternalId());
        dto.setBookTitle(reservation.getBook().getTitle());
        dto.setRentalDays(reservation.getRentalDays());
        dto.setStartDate(reservation.getStartDate());
        dto.setExpectedReturnDate(reservation.getExpectedReturnDate());
        dto.setActualReturnDate(reservation.getActualReturnDate());
        dto.setDailyRate(reservation.getDailyRate());
        dto.setTotalFee(reservation.getTotalFee());
        dto.setLateFee(reservation.getLateFee());
        dto.setStatus(reservation.getStatus());
        dto.setCreatedAt(reservation.getCreatedAt());
        return dto;
    }
}