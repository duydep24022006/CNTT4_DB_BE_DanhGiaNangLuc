# Bài làm CinemaBox – Database + Backend

Họ tên: Ba Duy
Mã sinh viên:
Lớp:

## Pha 1 (không dùng AI)

Tự đánh dấu phần đã xong trước mốc 1 (chỉ để GV tham khảo, không thay cho kết quả chấm):

- [x] I1  - [x] I2  - [x] I3  - [x] Endpoint GET /api/showtimes/{id}
- [x] F1  - [x] F2  - [x] F3  - [x] API khách hàng (tạo, xem, danh sách, validate, lỗi)

Ghi chú (nếu có): Một số lỗi nhỏ (sai tên cột `avaliable_seats` trong I2, cột `content` của F1, ngưỡng `>=` của F3, định dạng `startTime`, mã lỗi trong GlobalExceptionHandler) được rà soát và sửa lại ở Pha 2 (có dùng AI).

## Pha 2 (được dùng AI)

### Cách bạn chống bán vượt số ghế khi nhiều người đặt cùng lúc

Hai lớp bảo vệ. (1) `BookingService` giữ một `ReentrantLock` (công bằng) riêng cho từng suất chiếu, lấy **ngoài** transaction, nên các request cùng suất xếp hàng và request sau chỉ thấy dữ liệu đã commit/rollback; suất khác không bị chặn. Cách này tránh lỗi timeout khoá của H2 khi cổng thanh toán chậm 80 ms, và không từ chối nhầm vì ghế của một request thanh toán lỗi đã được hoàn trả trước khi request sau chạy. (2) Trong transaction, ghế được trừ bằng một câu `UPDATE showtimes SET available_seats = available_seats - :qty WHERE id = :id AND available_seats >= :qty`; trả về 0 dòng thì ném 409 NOT_ENOUGH_SEATS. Dù có nhiều instance, DB vẫn không bao giờ cho ghế âm (thêm `CHECK (available_seats >= 0)` trong schema.sql). Kiểm chứng bằng test 16 luồng đặt cùng suất 8 ghế: đúng 8 vé 201, 8 vé 409, ghế = 0.

### Cách bạn hoàn tác khi thanh toán lỗi

Toàn bộ thao tác đặt vé (kiểm tra, trừ ghế, gọi `PaymentGateway.charge`, lưu vé) nằm trong một transaction (`TransactionTemplate`). Ghế được trừ trước rồi mới thanh toán; nếu `PaymentFailedException` được ném ra thì transaction rollback, ghế trở về như cũ và không có dòng booking nào được lưu. `GlobalExceptionHandler` đổi lỗi thành 402 PAYMENT_FAILED.

### Test bạn đã viết (tên lớp, tên test, mỗi test kiểm tra điều gì)

Lớp `BookingApiTest`:
- `datVeHopLe_khachStandard_traVe201VaTruGhe`: đặt hợp lệ trả 201, đúng tiền, CONFIRMED, có paymentRef, trừ ghế.
- `datVe_khachGold_giamMuoiPhanTram`: khách GOLD được giảm 10%.
- `thanhToanBiTuChoi_hoanTacDuLieu`: thanh toán bị từ chối trả 402, ghế giữ nguyên, không còn booking của lần đặt đó.
- `khongDuGhe_tra409VaKhongDoiDuLieu`: suất hết ghế trả 409 NOT_ENOUGH_SEATS.
- `duLieuSaiVaKhongTonTai`: quantity 0 hoặc 11, body rỗng trả 400; khách/suất không tồn tại trả 404.
- `nhieuLuongCungDatMotSuat_khongBanVuotSoGhe`: 16 luồng đặt đồng thời, bán đúng bằng số ghế, ghế không âm, ghế đầu = ghế còn + vé mới.
- `dongThoiLanCaThanhToanLoi_duLieuVanDung`: đồng thời lẫn request bị từ chối, bất biến ghế vẫn đúng, vé CONFIRMED đều có payment_ref.
- `huyVe_traGheVaChanHuyLanHai`: huỷ vé trả ghế, huỷ lần hai 409 INVALID_STATE, vé không tồn tại 404.

### Nhật ký dùng AI

| # | Bạn hỏi AI điều gì | AI gợi ý gì | Bạn giữ / sửa / bỏ, vì sao |
|---|---|---|---|
| 1 | Cách chống bán vượt ghế khi đặt đồng thời | `@Lock(PESSIMISTIC_WRITE)` trên suất chiếu giữ suốt lúc gọi thanh toán | Sửa: giữ khoá DB suốt 80 ms thanh toán dễ timeout khi nhiều request. Dùng khoá theo suất ở tầng service cộng `UPDATE` có điều kiện. Kiểm chứng bằng test 16 luồng, kết quả đúng 8 vé bán ra. |
| 2 | Rà soát lỗi trong code/SQL đã làm | Chỉ ra lỗi tên cột `avaliable_seats`, cột `context`, F3 dùng `>` thay vì `>=`, ShowtimeService không biên dịch được, handler trùng `MethodArgumentNotValidException`, PAYMENT_FAILED trả 400 | Giữ và sửa hết, vì đều trái đề; kiểm chứng bằng `mvn test` và chạy lại SQL. |
| 3 | Hoàn tác khi thanh toán lỗi | Gọi thanh toán ngoài transaction rồi hoàn tiền | Bỏ: `PaymentGateway` không có hàm hoàn tiền. Dùng transaction, rollback khi `PaymentFailedException`; kiểm chứng bằng test `thanhToanBiTuChoi_hoanTacDuLieu`. |
