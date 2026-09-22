package com.moongcheap_backend.demand.unit.demand.application.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.demand.application.demand.DemandService;
import com.moongcheap_backend.demand.domain.demand.Demand;
import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoardStatus;
import com.moongcheap_backend.demand.domain.rejectHistory.RejectHistory;
import com.moongcheap_backend.demand.infrastructure.demand.DemandQueryRepository;
import com.moongcheap_backend.demand.infrastructure.demand.DemandRepository;
import com.moongcheap_backend.demand.infrastructure.demandBoard.DemandBoardRepository;
import com.moongcheap_backend.demand.infrastructure.rejectHistory.RejectHistoryRepository;
import com.moongcheap_backend.demand.presentation.demand.dto.DemandCreateRequestDto;
import com.moongcheap_backend.demand.presentation.demand.dto.DemandListDto;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodRepository;
import com.moongcheap_backend.product.domain.productCatalog.ProductCatalogStatus;
import com.moongcheap_backend.product.infrastructure.product.ProductRepository;
import com.moongcheap_backend.product.infrastructure.productCatalog.ProductCatalogRespository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class DemandServiceSuccessTest {

    @Mock
    private DemandRepository demandRepository;
    @Mock
    private DemandQueryRepository demandQueryRepository;
    @Mock
    private ProductCatalogRespository productCatalogRespository;
    @Mock
    private DemandBoardRepository demandBoardRepository;
    @Mock
    private RejectHistoryRepository rejectHistoryRepository;
    @Mock
    private BrandPayMethodRepository brandPayMethodRepository;
    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private DemandService service;

    private DemandCreateRequestDto createRequest(Long catalogId, Long payMethodId) {
        return new DemandCreateRequestDto(
            catalogId, payMethodId, 10000, 20000, 1, null, true, true, true, true, true);
    }

    private DemandListDto.DemandItemDto itemDto(Long id) {
        return new DemandListDto.DemandItemDto(
            id, DemandStatus.UNASSIGNED, 10000, 20000,
            LocalDateTime.now().plusDays(1), 1, null, false,
            LocalDateTime.now(), null, null, null);
    }

    @Nested
    @DisplayName("create - 성공")
    class CreateTest {

        @Test
        void 유효한_catalog와_payMethod로_수요를_등록한다() {
            Long memberId = 1L;
            when(productCatalogRespository.existsByIdAndStatus(10L, ProductCatalogStatus.ACTIVE))
                .thenReturn(true);
            when(brandPayMethodRepository.existsByIdAndMemberIdAndStatus(
                20L, memberId, PaymentsMethodStatus.ACTIVE)).thenReturn(true);
            Demand saved = mock(Demand.class);
            when(saved.getId()).thenReturn(100L);
            when(demandRepository.save(any(Demand.class))).thenReturn(saved);

            Long result = service.create(createRequest(10L, 20L), memberId);

            assertThat(result).isEqualTo(100L);
            verify(demandRepository).save(any(Demand.class));
        }
    }

    @Nested
    @DisplayName("get - 성공")
    class GetTest {

        @Test
        void 본인의_수요를_단건_조회한다() {
            DemandListDto.DemandItemDto item = itemDto(1L);
            when(demandQueryRepository.findDemandItemByIdAndMemberId(1L, 10L))
                .thenReturn(Optional.of(item));

            DemandListDto.DemandItemDto result = service.get(10L, 1L);

            assertThat(result).isSameAs(item);
        }
    }

    @Nested
    @DisplayName("list - 성공")
    class ListTest {

        @Test
        void statuses를_지정하여_수요_목록을_조회한다() {
            Long memberId = 1L;
            Pageable pageable = PageRequest.of(0, 10);
            List<DemandStatus> statuses = List.of(DemandStatus.UNASSIGNED);
            when(demandQueryRepository.findDemandItemsByMemberId(
                eq(memberId), eq(statuses), any())).thenReturn(List.of(itemDto(1L)));

            DemandListDto result = service.list(memberId, statuses, pageable);

            assertThat(result.demands()).hasSize(1);
            verify(demandQueryRepository).findDemandItemsByMemberId(eq(memberId), eq(statuses),
                any());
        }

        @Test
        void statuses가_null이면_ACTIVE_STATUSES로_조회된다() {
            Long memberId = 1L;
            Pageable pageable = PageRequest.of(0, 10);
            List<DemandStatus> active = List.of(
                DemandStatus.UNASSIGNED, DemandStatus.SUBSTITUTE_OFFERED,
                DemandStatus.ASSIGNED, DemandStatus.PAYMENT_PENDING);
            when(demandQueryRepository.findDemandItemsByMemberId(
                eq(memberId), eq(active), any())).thenReturn(List.of());

            service.list(memberId, null, pageable);

            verify(demandQueryRepository).findDemandItemsByMemberId(eq(memberId), eq(active),
                any());
        }

        @Test
        void statuses가_empty이면_ACTIVE_STATUSES로_조회된다() {
            Long memberId = 1L;
            Pageable pageable = PageRequest.of(0, 10);
            List<DemandStatus> active = List.of(
                DemandStatus.UNASSIGNED, DemandStatus.SUBSTITUTE_OFFERED,
                DemandStatus.ASSIGNED, DemandStatus.PAYMENT_PENDING);
            when(demandQueryRepository.findDemandItemsByMemberId(
                eq(memberId), eq(active), any())).thenReturn(List.of());

            service.list(memberId, List.of(), pageable);

            verify(demandQueryRepository).findDemandItemsByMemberId(eq(memberId), eq(active),
                any());
        }

        @Test
        void 페이지_크기_이상의_결과이면_hasNext가_true이다() {
            Long memberId = 1L;
            Pageable pageable = PageRequest.of(0, 2);
            when(demandQueryRepository.findDemandItemsByMemberId(any(), any(), any()))
                .thenReturn(List.of(itemDto(1L), itemDto(2L), itemDto(3L))); // pageSize + 1

            DemandListDto result = service.list(memberId, null, pageable);

            assertThat(result.hasNext()).isTrue();
            assertThat(result.demands()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("cancel - 성공")
    class CancelTest {

        @Test
        void UNASSIGNED_상태의_수요를_취소한다() {
            Long memberId = 1L;
            Long demandId = 100L;
            Demand demand = mock(Demand.class);
            when(demand.getMemberId()).thenReturn(memberId);
            when(demand.getStatus()).thenReturn(DemandStatus.UNASSIGNED);
            when(demand.getDemandBoardId()).thenReturn(null);
            when(demandRepository.findByIdForUpdate(demandId)).thenReturn(Optional.of(demand));

            service.cancel(memberId, demandId);

            verify(demand).cancel();
            verify(demandBoardRepository, never()).decrementParticipantCount(any());
        }

        @Test
        void SUBSTITUTE_OFFERED_상태의_수요를_취소한다() {
            Long memberId = 1L;
            Long demandId = 100L;
            Demand demand = mock(Demand.class);
            when(demand.getMemberId()).thenReturn(memberId);
            when(demand.getStatus()).thenReturn(DemandStatus.SUBSTITUTE_OFFERED);
            when(demand.getDemandBoardId()).thenReturn(200L);
            when(demandRepository.findByIdForUpdate(demandId)).thenReturn(Optional.of(demand));

            service.cancel(memberId, demandId);

            verify(demand).cancel();
            verify(demandBoardRepository, never()).decrementParticipantCount(any());
        }

        @Test
        void ASSIGNED_상태이고_demandBoardId가_있는_수요를_취소한다() {
            Long memberId = 1L;
            Long demandId = 100L;
            Long boardId = 200L;
            Demand demand = mock(Demand.class);
            when(demand.getMemberId()).thenReturn(memberId);
            when(demand.getStatus()).thenReturn(DemandStatus.ASSIGNED);
            when(demand.getDemandBoardId()).thenReturn(boardId);
            when(demandRepository.findByIdForUpdate(demandId)).thenReturn(Optional.of(demand));

            service.cancel(memberId, demandId);

            verify(demand).cancel();
            verify(demandBoardRepository).decrementParticipantCount(boardId);
        }
    }

    @Nested
    @DisplayName("acceptOffer - 성공")
    class AcceptOfferTest {

        @Test
        void 유효한_대체_제안을_수락하고_보드가_활성_상태이다() {
            Long memberId = 1L;
            Long demandId = 100L;
            Long boardId = 200L;
            Demand demand = mock(Demand.class);
            when(demand.getDesireEndAt()).thenReturn(LocalDateTime.now().plusDays(1));
            when(demand.getDemandBoardId()).thenReturn(boardId);
            when(demandRepository.findByIdAndStatusForUpdate(
                demandId, memberId, DemandStatus.SUBSTITUTE_OFFERED))
                .thenReturn(Optional.of(demand));
            when(demandBoardRepository.increaseParticipantCountIfActive(
                boardId, DemandBoardStatus.GB_GATHERING)).thenReturn(1);

            service.acceptOffer(memberId, demandId);

            verify(demand).acceptOffer();
            verify(demand, never()).rejectOffer();
        }

        @Test
        void 대체_제안_수락_후_보드가_이미_마감된_경우_rejectOffer로_롤백된다() {
            Long memberId = 1L;
            Long demandId = 100L;
            Long boardId = 200L;
            Demand demand = mock(Demand.class);
            when(demand.getDesireEndAt()).thenReturn(LocalDateTime.now().plusDays(1));
            when(demand.getDemandBoardId()).thenReturn(boardId);
            when(demandRepository.findByIdAndStatusForUpdate(
                demandId, memberId, DemandStatus.SUBSTITUTE_OFFERED))
                .thenReturn(Optional.of(demand));
            when(demandBoardRepository.increaseParticipantCountIfActive(
                boardId, DemandBoardStatus.GB_GATHERING)).thenReturn(0);

            service.acceptOffer(memberId, demandId);

            verify(demand).rejectOffer();
            verify(demand, never()).acceptOffer();
        }
    }

    @Nested
    @DisplayName("rejectOffer - 성공")
    class RejectOfferTest {

        @Test
        void 유효한_대체_제안을_거절한다() {
            Long memberId = 1L;
            Long demandId = 100L;
            Long boardId = 200L;
            Demand demand = mock(Demand.class);
            when(demand.getId()).thenReturn(demandId);
            when(demand.getDesireEndAt()).thenReturn(LocalDateTime.now().plusDays(1));
            when(demand.getDemandBoardId()).thenReturn(boardId);
            when(demandRepository.findByIdAndStatusForUpdate(
                demandId, memberId, DemandStatus.SUBSTITUTE_OFFERED))
                .thenReturn(Optional.of(demand));

            service.rejectOffer(memberId, demandId);

            verify(rejectHistoryRepository).save(any(RejectHistory.class));
            verify(demand).rejectOffer();
        }
    }
}
