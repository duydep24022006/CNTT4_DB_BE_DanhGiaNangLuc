package vn.rikkei.cinemabox.customer;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.rikkei.cinemabox.common.BusinessException;
import vn.rikkei.cinemabox.common.PageResponse;
import vn.rikkei.cinemabox.common.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest req) {
        String email = req.email().trim();
        if (customerRepository.existsByEmailIgnoreCase(email)) {
            throw duplicate(email);
        }
        Customer c = new Customer();
        c.setFullName(req.fullName().trim());
        c.setEmail(email);
        c.setPhone(req.phone());
        c.setMemberLevel(req.memberLevel()); // null -> Customer.prePersist đặt STANDARD
        try {
            return CustomerResponse.from(customerRepository.saveAndFlush(c));
        } catch (DataIntegrityViolationException e) {
            throw duplicate(email);
        }
    }

    public CustomerResponse getById(Long id) {
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng id=" + id));
        return CustomerResponse.from(c);
    }

    public PageResponse<CustomerResponse> list(int page, int size) {
        if (page < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "page phải >= 0");
        }
        if (size < 1 || size > 50) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "size phải từ 1 đến 50");
        }
        Page<CustomerResponse> result = customerRepository
                .findAll(PageRequest.of(page, size, Sort.by("id").ascending()))
                .map(CustomerResponse::from);
        return PageResponse.from(result);
    }

    private static BusinessException duplicate(String email) {
        return new BusinessException(HttpStatus.CONFLICT, "CONFLICT", "Email đã tồn tại: " + email);
    }
}