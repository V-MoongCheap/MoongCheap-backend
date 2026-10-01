package com.moongcheap_backend.demand.infrastructure.demand;

import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import com.moongcheap_backend.demand.presentation.demand.dto.DemandListDto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

public interface DemandQueryRepository {

    List<DemandListDto.DemandItemDto> findDemandItemsByMemberId(Long memberId,
        List<DemandStatus> statuses, Pageable pageable);

    Optional<DemandListDto.DemandItemDto> findDemandItemByIdAndMemberId(Long demandId, Long memberId);
}
