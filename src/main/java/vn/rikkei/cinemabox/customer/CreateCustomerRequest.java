package vn.rikkei.cinemabox.customer;

import jakarta.validation.constraints.*;

public record CreateCustomerRequest(

        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName,

        @NotBlank(message = "Email không được để trống")
        @Size(max = 150, message = "Email tối đa 150 ký tự")
        @Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "Email không đúng định dạng")
        String email,

        @Pattern(regexp = "^0[0-9]{9}$", message = "Số điện thoại gồm đúng 10 chữ số, bắt đầu bằng 0")
        String phone,

        @Pattern(regexp = "^(STANDARD|GOLD)$", message = "memberLevel chỉ nhận STANDARD hoặc GOLD")
        String memberLevel) {
}