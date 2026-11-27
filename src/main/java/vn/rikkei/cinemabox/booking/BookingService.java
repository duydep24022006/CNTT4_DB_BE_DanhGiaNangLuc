package vn.rikkei.cinemabox.booking;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.rikkei.cinemabox.common.BusinessException;
import vn.rikkei.cinemabox.common.ResourceNotFoundException;
import vn.rikkei.cinemabox.customer.Customer;
import vn.rikkei.cinemabox.customer.CustomerRepository;
import vn.rikkei.cinemabox.payment.PaymentGateway;
import vn.rikkei.cinemabox.showtime.Showtime;
import vn.rikkei.cinemabox.showtime.ShowtimeRepository;

/**
 * Đặt vé an toàn.
 * - Mỗi suất chiếu có một khoá riêng (xếp hàng các request cùng suất, không chặn suất khác),
 *   khoá được lấy NGOÀI transaction nên request sau chỉ thấy dữ liệu đã commit/rollback xong.
 * - Trong transaction: trừ ghế bằng UPDATE có điều kiện (available_seats >= qty), rồi mới gọi thanh toán.
 *   Thanh toán lỗi -> ném exception -> rollback, ghế và vé trở về như cũ.
 */
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final ShowtimeRepository showtimeRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate tx;
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public BookingService(BookingRepository bookingRepository,
                          CustomerRepository customerRepository,
                          ShowtimeRepository showtimeRepository,
                          PaymentGateway paymentGateway,
                          PlatformTransactionManager txManager) {
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
        this.showtimeRepository = showtimeRepository;
        this.paymentGateway = paymentGateway;
        this.tx = new TransactionTemplate(txManager);
    }

    public BookingResponse create(CreateBookingRequest req) {
        ReentrantLock lock = locks.computeIfAbsent(req.showtimeId(), k -> new ReentrantLock(true));
        lock.lock();
        try {
            return tx.execute(status -> doCreate(req));
        } finally {
            lock.unlock();
        }
    }

    private BookingResponse doCreate(CreateBookingRequest req) {
        Customer customer = customerRepository.findById(req.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng id=" + req.customerId()));
        Showtime showtime = showtimeRepository.findById(req.showtimeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy suất chiếu id=" + req.showtimeId()));

        int qty = req.quantity();
        long total = showtime.getPrice() * qty;
        if ("GOLD".equals(customer.getMemberLevel())) {
            total = total * 90 / 100; // giảm 10%, làm tròn xuống
        }

        if (showtimeRepository.decreaseSeats(showtime.getId(), qty) == 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "NOT_ENOUGH_SEATS", "Suất chiếu không còn đủ ghế");
        }

        // Ném PaymentFailedException -> transaction rollback, ghế được hoàn lại.
        String paymentRef = paymentGateway.charge(customer.getId(), total);

        Booking b = new Booking();
        b.setCustomer(customer);
        b.setShowtime(showtimeRepository.getReferenceById(showtime.getId()));
        b.setQuantity(qty);
        b.setTotalAmount(total);
        b.setStatus("CONFIRMED");
        b.setPaymentRef(paymentRef);
        return BookingResponse.from(bookingRepository.saveAndFlush(b));
    }

    public BookingResponse cancel(Long id) {
        return tx.execute(status -> {
            Booking b = bookingRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé id=" + id));
            Long showtimeId = b.getShowtime().getId();
            int qty = b.getQuantity();
            if (bookingRepository.cancelIfConfirmed(id) == 0) {
                throw new BusinessException(HttpStatus.CONFLICT, "INVALID_STATE",
                        "Chỉ huỷ được vé đang CONFIRMED");
            }
            showtimeRepository.increaseSeats(showtimeId, qty);
            return BookingResponse.from(bookingRepository.findById(id).orElseThrow());
        });
    }
}
