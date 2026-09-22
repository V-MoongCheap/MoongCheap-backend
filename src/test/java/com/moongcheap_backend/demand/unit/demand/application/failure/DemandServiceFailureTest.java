package com.moongcheap_backend.demand.unit.demand.application.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.demand.application.demand.DemandService;
import com.moongcheap_backend.demand.domain.demand.Demand;
import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import com.moongcheap_backend.demand.infrastructure.demand.DemandQueryRepository;
import com.moongcheap_backend.demand.infrastructure.demand.DemandRepository;
import com.moongcheap_backend.demand.infrastructure.demandBoard.DemandBoardRepository;
import com.moongcheap_backend.demand.infrastructure.rejectHistory.RejectHistoryRepository;
import com.moongcheap_backend.demand.presentation.demand.dto.DemandCreateRequestDto;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodRepository;
import com.moongcheap_backend.product.domain.productCatalog.ProductCatalogStatus;
import com.moongcheap_backend.product.infrastructure.productCatalog.ProductCatalogRespository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class DemandServiceFailureTest {

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

    @InjectMocks
    private DemandService service;

    private DemandCreateRequestDto createRequest() {
        return new DemandCreateRequestDto(
            10L, 20L, 10000, 20000, 1, null, true, true, true, true, true);
    }

    @Nested
    @DisplayName("create - 실패")
    class CreateTest {

        @Test
        void 존재하지_않거나_INACTIVE_상태_catalog로_수요를_등록한다() {
            when(productCatalogRespository.existsByIdAndStatus(10L, ProductCatalogStatus.ACTIVE))
                .thenReturn(false);

            assertThatThrownBy(() -> service.create(createRequest(), 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_CATALOG_NOT_FOUND);
        }

        @Test
        void 유효하지_않은_payMethod로_수요를_등록한다() {
            when(productCatalogRespository.existsByIdAndStatus(10L, ProductCatalogStatus.ACTIVE))
                .thenReturn(true);
            when(brandPayMethodRepository.existsByIdAndMemberIdAndStatus(
                20L, 1L, PaymentsMethodStatus.ACTIVE)).thenReturn(false);

            assertThatThrownBy(() -> service.create(createRequest(), 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BRAND_PAY_METHOD_NOT_FOUND);
        }

        @Test
        void 이미_활성_상태의_동일_수요가_존재한다() {
            when(productCatalogRespository.existsByIdAndStatus(10L, ProductCatalogStatus.ACTIVE))
                .thenReturn(true);
            when(brandPayMethodRepository.existsByIdAndMemberIdAndStatus(
                20L, 1L, PaymentsMethodStatus.ACTIVE)).thenReturn(true);
            when(demandRepository.save(any(Demand.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

            assertThatThrownBy(() -> service.create(createRequest(), 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMAND_ALREADY_EXISTS);
        }
    }

    @Nested
    @DisplayName("get - 실패")
    class GetTest {

        @Test
        void 존재하지_않거나_본인_것이_아닌_수요를_조회한다() {
            when(demandQueryRepository.findDemandItemByIdAndMemberId(1L, 10L))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.get(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMAND_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("cancel - 실패")
    class CancelTest {

        @Test
        void 존재하지_않는_수요를_취소한다() {
            when(demandRepository.findByIdForUpdate(100L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancel(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMAND_NOT_FOUND);
        }

        @Test
        void 다른_회원_소유의_수요를_취소한다() {
            Demand demand = mock(Demand.class);
            when(demand.getMemberId()).thenReturn(99L);
            when(demandRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(demand));

            assertThatThrownBy(() -> service.cancel(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMAND_FORBIDDEN);
        }

        @Test
        void CANCELABLE_STATUSES에_없는_CANCELED_상태의_수요를_취소한다() {
            Demand demand = mock(Demand.class);
            when(demand.getMemberId()).thenReturn(1L);
            when(demand.getStatus()).thenReturn(DemandStatus.CANCELED);
            when(demandRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(demand));

            assertThatThrownBy(() -> service.cancel(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMAND_CANCEL_NOT_ALLOWED);
        }

        @Test
        void 이미_실패_처리된_FAILED_상태의_수요를_취소한다() {
            Demand demand = mock(Demand.class);
            when(demand.getMemberId()).thenReturn(1L);
            when(demand.getStatus()).thenReturn(DemandStatus.FAILED);
            when(demandRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(demand));

            assertThatThrownBy(() -> service.cancel(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMAND_CANCEL_NOT_ALLOWED);
        }
    }

    @Nested
    @DisplayName("acceptOffer - 실패")
    class AcceptOfferTest {

        @Test
        void SUBSTITUTE_OFFERED_상태가_아닌_수요의_제안을_수락한다() {
            when(demandRepository.findByIdAndStatusForUpdate(100L, 1L,
                DemandStatus.SUBSTITUTE_OFFERED))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.acceptOffer(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMAND_NOT_FOUND);
        }

        @Test
        void 만료_기한이_지난_수요의_제안을_수락한다() {
            Demand demand = mock(Demand.class);
            when(demand.getDesireEndAt()).thenReturn(LocalDateTime.now().minusDays(1));
            when(demandRepository.findByIdAndStatusForUpdate(100L, 1L,
                DemandStatus.SUBSTITUTE_OFFERED))
                .thenReturn(Optional.of(demand));

            assertThatThrownBy(() -> service.acceptOffer(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMAND_DESIRE_EXPIRED);
        }

        @Test
        void demandBoardId가_없는_수요의_제안을_수락한다() {
            Demand demand = mock(Demand.class);
            when(demand.getDesireEndAt()).thenReturn(LocalDateTime.now().plusDays(1));
            when(demand.getDemandBoardId()).thenReturn(null);
            when(demandRepository.findByIdAndStatusForUpdate(100L, 1L,
                DemandStatus.SUBSTITUTE_OFFERED))
                .thenReturn(Optional.of(demand));

            assertThatThrownBy(() -> service.acceptOffer(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMAND_ACCEPT_NOT_ALLOWED);
        }
    }

    @Nested
    @DisplayName("rejectOffer - 실패")
    class RejectOfferTest {

        @Test
        void SUBSTITUTE_OFFERED_상태가_아닌_수요의_제안을_거절한다() {
            when(demandRepository.findByIdAndStatusForUpdate(100L, 1L,
                DemandStatus.SUBSTITUTE_OFFERED))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.rejectOffer(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMAND_NOT_FOUND);
        }

        @Test
        void 만료_기한이_지난_수요의_제안을_거절한다() {
            Demand demand = mock(Demand.class);
            when(demand.getDesireEndAt()).thenReturn(LocalDateTime.now().minusDays(1));
            when(demandRepository.findByIdAndStatusForUpdate(100L, 1L,
                DemandStatus.SUBSTITUTE_OFFERED))
                .thenReturn(Optional.of(demand));

            assertThatThrownBy(() -> service.rejectOffer(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMAND_DESIRE_EXPIRED);
        }

        @Test
        void demandBoardId가_없는_수요의_제안을_거절한다() {
            Demand demand = mock(Demand.class);
            when(demand.getDesireEndAt()).thenReturn(LocalDateTime.now().plusDays(1));
            when(demand.getDemandBoardId()).thenReturn(null);
            when(demandRepository.findByIdAndStatusForUpdate(100L, 1L,
                DemandStatus.SUBSTITUTE_OFFERED))
                .thenReturn(Optional.of(demand));

            assertThatThrownBy(() -> service.rejectOffer(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMAND_ACCEPT_NOT_ALLOWED);
        }
    }
}
