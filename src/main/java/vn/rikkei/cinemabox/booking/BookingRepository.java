package vn.rikkei.cinemabox.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Chuyển CONFIRMED -> CANCELLED nguyên tử; trả 0 nếu vé không còn CONFIRMED. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Booking b SET b.status = 'CANCELLED' WHERE b.id = :id AND b.status = 'CONFIRMED'")
    int cancelIfConfirmed(@Param("id") Long id);
}
