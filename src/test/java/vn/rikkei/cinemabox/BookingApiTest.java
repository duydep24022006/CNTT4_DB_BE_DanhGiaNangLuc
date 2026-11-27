package vn.rikkei.cinemabox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test đặt vé. Mỗi test dùng một suất chiếu riêng trong dữ liệu mẫu để không ảnh hưởng nhau.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookingApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private int seats(long showtimeId) {
        return jdbc.queryForObject("SELECT available_seats FROM showtimes WHERE id = ?", Integer.class, showtimeId);
    }

    private int confirmedCount(long showtimeId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantity), 0) FROM bookings WHERE showtime_id = ? AND status = 'CONFIRMED'",
                Integer.class, showtimeId);
    }

    private int bookingCount(long showtimeId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE showtime_id = ?", Integer.class, showtimeId);
    }

    private org.springframework.test.web.servlet.ResultActions book(long customerId, long showtimeId, int qty)
            throws Exception {
        String body = "{\"customerId\":" + customerId + ",\"showtimeId\":" + showtimeId + ",\"quantity\":" + qty + "}";
        return mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void datVeHopLe_khachStandard_traVe201VaTruGhe() throws Exception {
        // suất 9: giá 80.000, 100 ghế; khách 2 là STANDARD
        book(2, 9, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(160000))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentRef").isNotEmpty());
        assertEquals(98, seats(9));
    }

    @Test
    void datVe_khachGold_giamMuoiPhanTram() throws Exception {
        // suất 6: giá 110.000; khách 1 là GOLD: 2 x 110.000 x 0,9 = 198.000
        book(1, 6, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(198000));
        assertEquals(18, seats(6));
    }

    @Test
    void thanhToanBiTuChoi_hoanTacDuLieu() throws Exception {
        // suất 11: giá 130.000; 8 vé = 1.040.000 > hạn mức 1.000.000 -> cổng từ chối
        int seatsBefore = seats(11);
        int bookingsBefore = bookingCount(11);
        book(2, 11, 8)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("PAYMENT_FAILED"));
        assertEquals(seatsBefore, seats(11), "số ghế phải giữ nguyên");
        assertEquals(bookingsBefore, bookingCount(11), "không được còn vé nào của lần đặt lỗi");
    }

    @Test
    void khongDuGhe_tra409VaKhongDoiDuLieu() throws Exception {
        // suất 10 còn 0 ghế
        book(2, 10, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_ENOUGH_SEATS"));
        assertEquals(0, seats(10));
    }

    @Test
    void duLieuSaiVaKhongTonTai() throws Exception {
        book(2, 9, 0).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
        book(2, 9, 11).andExpect(status().isBadRequest());
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(3));
        book(9999, 9, 1).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
        book(2, 9999, 1).andExpect(status().isNotFound());
    }

    @Test
    void nhieuLuongCungDatMotSuat_khongBanVuotSoGhe() throws Exception {
        // suất 7: còn 8 ghế, giá 90.000. 16 request đồng thời, mỗi request 1 vé.
        int seatsBefore = seats(7);
        int confirmedBefore = confirmedCount(7);
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger full = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Void> task = () -> {
                int code = book(2, 7, 1).andReturn().getResponse().getStatus();
                if (code == 201) {
                    ok.incrementAndGet();
                } else if (code == 409) {
                    full.incrementAndGet();
                }
                return null;
            };
            futures.add(pool.submit(task));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertEquals(seatsBefore, ok.get(), "bán đúng bằng số ghế còn lại");
        assertEquals(threads - seatsBefore, full.get());
        assertEquals(0, seats(7));
        assertEquals(seatsBefore, confirmedCount(7) - confirmedBefore, "ghế đầu = ghế còn + vé mới");
    }

    @Test
    void dongThoiLanCaThanhToanLoi_duLieuVanDung() throws Exception {
        // suất 11: giá 130.000. 6 request 1 vé (hợp lệ) trộn 6 request 8 vé = 1.040.000 (bị từ chối).
        int seatsBefore = seats(11);
        int confirmedBefore = confirmedCount(11);
        ExecutorService pool = Executors.newFixedThreadPool(12);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            final boolean bad = i % 2 == 0;
            futures.add(pool.submit(() -> book(2, 11, bad ? 8 : 1).andReturn().getResponse().getStatus()));
        }
        int created = 0;
        for (Future<Integer> f : futures) {
            int code = f.get();
            if (code == 201) {
                created++;
            }
        }
        pool.shutdown();
        assertEquals(seatsBefore, seats(11) + (confirmedCount(11) - confirmedBefore));
        assertEquals(true, seats(11) >= 0);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE status = 'CONFIRMED' AND payment_ref IS NULL", Integer.class));
        assertEquals(6, created, "6 request 1 vé phải đặt được, 6 request 8 vé bị từ chối");
    }

    @Test
    void huyVe_traGheVaChanHuyLanHai() throws Exception {
        // suất 5: 55 ghế, giá 75.000
        int before = seats(5);
        String json = book(2, 5, 3).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = json.replaceAll(".*\"id\":(\\d+).*", "$1");
        assertEquals(before - 3, seats(5));

        mvc.perform(post("/api/bookings/" + id + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals(before, seats(5));

        mvc.perform(post("/api/bookings/" + id + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE"));
        assertEquals(before, seats(5), "huỷ lần hai không được cộng ghế thêm");

        mvc.perform(post("/api/bookings/999999/cancel")).andExpect(status().isNotFound());
    }
}
