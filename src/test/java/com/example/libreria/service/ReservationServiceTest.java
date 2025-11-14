package com.example.libreria.service;

import com.example.libreria.dto.ReservationRequestDTO;
import com.example.libreria.dto.ReservationResponseDTO;
import com.example.libreria.dto.ReturnBookRequestDTO;
import com.example.libreria.model.Book;
import com.example.libreria.model.Reservation;
import com.example.libreria.model.User;
import com.example.libreria.repository.BookRepository;
import com.example.libreria.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookService bookService;

    @Mock
    private UserService userService;

    @InjectMocks
    private ReservationService reservationService;

    private User testUser;
    private Book testBook;
    private Reservation testReservation;
    private ReservationRequestDTO reservationRequestDTO;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setName("Juan Pérez");
        testUser.setEmail("juan@example.com");

        testBook = new Book();
        testBook.setExternalId(258027L);
        testBook.setTitle("The Lord of the Rings");
        testBook.setAuthorName(Arrays.asList("J.R.R. Tolkien")); // IMPORTANTE: Agregar author
        testBook.setPrice(new BigDecimal("15.99"));
        testBook.setStockQuantity(10);
        testBook.setAvailableQuantity(5);

        testReservation = new Reservation();
        testReservation.setId(1L);
        testReservation.setUser(testUser);
        testReservation.setBook(testBook);
        testReservation.setRentalDays(7);
        testReservation.setStartDate(LocalDate.of(2024, 1, 15));
        testReservation.setExpectedReturnDate(LocalDate.of(2024, 1, 22));
        testReservation.setDailyRate(new BigDecimal("15.99"));
        testReservation.setTotalFee(new BigDecimal("111.93"));
        testReservation.setLateFee(BigDecimal.ZERO);
        testReservation.setStatus(Reservation.ReservationStatus.ACTIVE);
        testReservation.setCreatedAt(LocalDateTime.now());

        reservationRequestDTO = new ReservationRequestDTO();
        reservationRequestDTO.setUserId(1L);
        reservationRequestDTO.setBookExternalId(258027L);
        reservationRequestDTO.setRentalDays(7);
        reservationRequestDTO.setStartDate(LocalDate.of(2024, 1, 15));
    }

    @Test
    void testCreateReservation_Success() {
        // Arrange
        when(userService.getUserEntity(1L)).thenReturn(testUser);
        when(bookRepository.findByExternalId(258027L)).thenReturn(Optional.of(testBook));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(testReservation);
        doNothing().when(bookService).decreaseAvailableQuantity(258027L);

        // Act
        ReservationResponseDTO result = reservationService.createReservation(reservationRequestDTO);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Juan Pérez", result.getUserName());
        assertEquals("The Lord of the Rings", result.getBookTitle());
        assertEquals(7, result.getRentalDays());
        assertEquals(Reservation.ReservationStatus.ACTIVE, result.getStatus());

        verify(userService, times(1)).getUserEntity(1L);
        verify(bookRepository, times(1)).findByExternalId(258027L);
        verify(reservationRepository, times(1)).save(any(Reservation.class));
        verify(bookService, times(1)).decreaseAvailableQuantity(258027L);
    }

    @Test
    void testCreateReservation_BookNotAvailable() {
        // Arrange
        testBook.setAvailableQuantity(0);
        when(userService.getUserEntity(1L)).thenReturn(testUser);
        when(bookRepository.findByExternalId(258027L)).thenReturn(Optional.of(testBook));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            reservationService.createReservation(reservationRequestDTO);
        });

        assertTrue(exception.getMessage().contains("No hay copias disponibles"));
        verify(bookService, never()).decreaseAvailableQuantity(anyLong());
    }

    @Test
    void testReturnBook_OnTime() {
        // Arrange
        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO();
        returnRequest.setReturnDate(LocalDate.of(2024, 1, 22)); // Fecha esperada

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(bookService).increaseAvailableQuantity(258027L);

        // Act
        ReservationResponseDTO result = reservationService.returnBook(1L, returnRequest);

        // Assert
        assertNotNull(result);
        assertEquals(LocalDate.of(2024, 1, 22), result.getActualReturnDate());
        assertEquals(BigDecimal.ZERO, result.getLateFee());
        assertEquals(Reservation.ReservationStatus.RETURNED, result.getStatus());

        verify(reservationRepository, times(1)).findById(1L);
        verify(reservationRepository, times(1)).save(any(Reservation.class));
        verify(bookService, times(1)).increaseAvailableQuantity(258027L);
    }

    @Test
    void testReturnBook_Overdue() {
        // Arrange
        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO();
        returnRequest.setReturnDate(LocalDate.of(2024, 1, 25)); // 3 días tarde

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(bookService).increaseAvailableQuantity(258027L);

        // Act
        ReservationResponseDTO result = reservationService.returnBook(1L, returnRequest);

        // Assert
        assertNotNull(result);
        assertEquals(LocalDate.of(2024, 1, 25), result.getActualReturnDate());
        assertEquals(Reservation.ReservationStatus.OVERDUE, result.getStatus());

        // Multa = 15.99 * 0.15 * 3 = 7.20
        BigDecimal expectedLateFee = new BigDecimal("7.20");
        assertEquals(expectedLateFee, result.getLateFee());

        verify(reservationRepository, times(1)).findById(1L);
        verify(reservationRepository, times(1)).save(any(Reservation.class));
        verify(bookService, times(1)).increaseAvailableQuantity(258027L);
    }

    @Test
    void testGetReservationById_Success() {
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));

        ReservationResponseDTO result = reservationService.getReservationById(1L);

        assertNotNull(result);
        assertEquals(testReservation.getId(), result.getId());
    }

    @Test
    void testGetAllReservations() {
        Reservation reservation2 = new Reservation();
        reservation2.setId(2L);
        reservation2.setUser(testUser);  // IMPORTANTE: Agregar user
        reservation2.setBook(testBook);  // IMPORTANTE: Agregar book
        reservation2.setStatus(Reservation.ReservationStatus.ACTIVE);
        reservation2.setRentalDays(5);
        reservation2.setStartDate(LocalDate.now());
        reservation2.setExpectedReturnDate(LocalDate.now().plusDays(5));
        reservation2.setDailyRate(new BigDecimal("10.00"));
        reservation2.setTotalFee(new BigDecimal("50.00"));
        reservation2.setLateFee(BigDecimal.ZERO);
        reservation2.setCreatedAt(LocalDateTime.now());

        when(reservationRepository.findAll()).thenReturn(Arrays.asList(testReservation, reservation2));

        List<ReservationResponseDTO> result = reservationService.getAllReservations();

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    void testGetReservationsByUserId() {
        when(reservationRepository.findByUserId(1L)).thenReturn(Arrays.asList(testReservation));

        List<ReservationResponseDTO> result = reservationService.getReservationsByUserId(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testGetActiveReservations() {
        when(reservationRepository.findByStatus(Reservation.ReservationStatus.ACTIVE))
                .thenReturn(Arrays.asList(testReservation));

        List<ReservationResponseDTO> result = reservationService.getActiveReservations();

        assertNotNull(result);
        assertEquals(1, result.size());
    }
}