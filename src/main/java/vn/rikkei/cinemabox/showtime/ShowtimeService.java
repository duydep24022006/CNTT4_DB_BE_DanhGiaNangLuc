package vn.rikkei.cinemabox.showtime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.rikkei.cinemabox.common.BusinessException;

@Service
@Transactional(readOnly = true)

public class ShowtimeService {
    private final ShowtimeRepository showtimeRepository;
    public ShowtimeService(ShowtimeRepository showtimeRepository) {
        this.showtimeRepository = showtimeRepository;
    }
    public Showtime getById(Long id) {
        return showtimeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(org.springframework.http.HttpStatus.NOT_FOUND, "Showtime not found with id: " + id));
    }
}
