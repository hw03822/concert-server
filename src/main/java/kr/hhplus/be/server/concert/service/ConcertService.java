package kr.hhplus.be.server.concert.service;

import kr.hhplus.be.server.concert.dto.ConcertResponseDto;
import kr.hhplus.be.server.concert.repository.ConcertJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertService {
    private final ConcertJpaRepository concertJpaRepository;

    /**
     * 예약 가능한 콘서트 목록 조회
     * @return 콘서트 목록
     */
    public List<ConcertResponseDto> getAvailableConcerts() {
        LocalDateTime now = LocalDateTime.now();
        return concertJpaRepository.findAll().stream()
                .filter(concert -> concert.getConcertAt().isAfter(now))
                .map(ConcertResponseDto::from)
                .collect(Collectors.toList());
    }
}
