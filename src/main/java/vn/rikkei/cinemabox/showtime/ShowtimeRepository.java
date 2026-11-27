package vn.rikkei.cinemabox.showtime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    /** Trừ ghế nguyên tử: chỉ trừ khi còn đủ ghế. Trả về số dòng đổi (0 = không đủ ghế). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Showtime s SET s.availableSeats = s.availableSeats - :qty "
            + "WHERE s.id = :id AND s.availableSeats >= :qty")
    int decreaseSeats(@Param("id") Long id, @Param("qty") int qty);

    /** Trả ghế nguyên tử (khi huỷ vé). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Showtime s SET s.availableSeats = s.availableSeats + :qty WHERE s.id = :id")
    int increaseSeats(@Param("id") Long id, @Param("qty") int qty);
}
