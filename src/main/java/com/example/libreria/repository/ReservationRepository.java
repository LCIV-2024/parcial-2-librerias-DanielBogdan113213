package com.example.libreria.repository;

import com.example.libreria.model.Reservation;
import com.example.libreria.model.Reservation.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByUserId(Long userId);

    List<Reservation> findByStatus(ReservationStatus status);

    @Query("SELECT r FROM Reservation r WHERE r.status = 'ACTIVE'")
    List<Reservation> findActiveReservations();

    @Query("SELECT r FROM Reservation r WHERE r.status = 'ACTIVE' AND r.expectedReturnDate < :currentDate")
    List<Reservation> findOverdueReservations(@Param("currentDate") LocalDate currentDate);

    List<Reservation> findByBookExternalId(Long bookExternalId);

    //Buscar activas
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.user.id = :userId AND r.status = 'ACTIVE'")
    long countActiveReservationsByUserId(@Param("userId") Long userId);

    //Buscar vencidas
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Reservation r WHERE r.book.externalId = :bookExternalId AND r.status = 'ACTIVE'")
    boolean existsActiveReservationByBookExternalId(@Param("bookExternalId") Long bookExternalId);
}