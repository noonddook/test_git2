package net.dima.project.service;

import lombok.RequiredArgsConstructor;
import net.dima.project.dto.DashboardMetricsDto;
import net.dima.project.dto.ForwarderInfoDto;
import net.dima.project.dto.VolumeDto;
import net.dima.project.entity.*;
import net.dima.project.repository.ContainerRepository;
import net.dima.project.repository.OfferRepository;
import net.dima.project.repository.RequestRepository;
import net.dima.project.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminService {

    private final OfferRepository offerRepository;
    private final ContainerRepository containerRepository;
    private final UserRepository userRepository;
    private final RequestRepository requestRepository;
    
    // [추가] 포워더 목록 조회
    @Transactional(readOnly = true)
    public List<ForwarderInfoDto> getForwarderList() {
    	List<String> targetRoles = List.of("ROLE_fwd", "ROLE_PENDING");
    	List<UserEntity> forwarders = userRepository.findByRolesIn(targetRoles);
        List<OfferStatus> acceptedStatuses = List.of(OfferStatus.ACCEPTED, OfferStatus.RESOLD, OfferStatus.CONFIRMED, OfferStatus.SHIPPED, OfferStatus.COMPLETED);

        return forwarders.stream().map(fwd -> {
            long containerCount = containerRepository.countByForwarder(fwd);
            long totalOffers = offerRepository.countByForwarder(fwd);
            long acceptedOffers = offerRepository.countByForwarderAndStatusIn(fwd, acceptedStatuses);
            return ForwarderInfoDto.from(fwd, containerCount, totalOffers, acceptedOffers);
        }).collect(Collectors.toList());
    }

    // [추가] 포워더 승인
    public void updateUserStatus(Integer userSeq, String status) {
        UserEntity user = userRepository.findById(userSeq)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        user.setApprovalStatus(status);
        userRepository.save(user);
    }
    

    // [추가] 대시보드 지표 계산 메서드
    @Transactional(readOnly = true)
    public DashboardMetricsDto getDashboardMetrics() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        LocalDateTime deadlineThreshold = LocalDateTime.now().plusDays(1); // 마감 1일 전

        long todayRequests = requestRepository.countByCreatedAtBetween(startOfDay, endOfDay);
        long todayDeals = offerRepository.countByStatusAndCreatedAtBetween(OfferStatus.ACCEPTED, startOfDay, endOfDay);

        long totalFwdUsers = userRepository.countByRoles("ROLE_fwd");
        long totalCusUsers = userRepository.countByRoles("ROLE_cus");
        long pendingUsers = userRepository.countByRoles("ROLE_PENDING"); // '승인 대기' 역할 기준
        long noBidRequests = requestRepository.countOpenRequestsWithNoBids(deadlineThreshold);

        return DashboardMetricsDto.builder()
                .todayRequests(todayRequests)
                .todayDeals(todayDeals)
                .totalFwdUsers(totalFwdUsers)
                .totalCusUsers(totalCusUsers)
                .pendingUsers(pendingUsers)
                .noBidRequests(noBidRequests)
                .build();
    }

    // 기존 물동량 그래프 계산 메서드
    @Transactional(readOnly = true)
    public VolumeDto getSystemVolume() {
        List<OfferEntity> offersInScheduledContainers = offerRepository.findAllByContainerStatus(ContainerStatus.SCHEDULED);
        LocalDateTime now = LocalDateTime.now();

        double confirmedCbm = 0;
        double resaleCbm = 0;
        double biddingCbm = 0;

        for (OfferEntity offer : offersInScheduledContainers) {
            OfferStatus status = offer.getStatus();
            double cbm = offer.getRequest().getCargo().getTotalCbm();

            if (status == OfferStatus.ACCEPTED) {
                confirmedCbm += cbm;
            } else if (status == OfferStatus.FOR_SALE) {
                resaleCbm += cbm;
            } else if (status == OfferStatus.PENDING && offer.getRequest().getDeadline().isAfter(now)) {
                biddingCbm += cbm;
            }
        }

        List<ContainerEntity> scheduledContainers = containerRepository.findByStatus(ContainerStatus.SCHEDULED);
        double totalScheduledCapacity = scheduledContainers.stream()
                .mapToDouble(ContainerEntity::getCapacityCbm)
                .sum();
        double totalUsedCbm = confirmedCbm + resaleCbm + biddingCbm;
        double availableCbm = totalScheduledCapacity - totalUsedCbm;

        return VolumeDto.builder()
                .confirmedCbm(confirmedCbm)
                .resaleCbm(resaleCbm)
                .biddingCbm(biddingCbm)
                .availableCbm(availableCbm)
                .build();
    }
}