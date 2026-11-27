package vn.rikkei.cinemabox.booking;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(
        @NotNull(message = "customerId là bắt buộc")
        Long customerId,

        @NotNull(message = "showtimeId là bắt buộc")
        Long showtimeId,

        @NotNull(message = "quantity là bắt buộc")
        @Min(value = 1, message = "quantity tối thiểu là 1")
        @Max(value = 10, message = "quantity tối đa là 10")
        Integer quantity) {
}
