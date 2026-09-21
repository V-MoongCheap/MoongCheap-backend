package com.moongcheap_backend.payments.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.payments.infrastructure.BrandPayMethodClient;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodClient.MethodsResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreatePayMethodServiceUnitTest {

    @Mock
    private BrandPayTokenService brandPayTokenService;
    @Mock
    private BrandPayMethodClient brandPayMethodClient;
    @Mock
    private BrandPayMethodSyncService syncService;

    @InjectMocks
    private CreatePayMethodService service;

    @Test
    void 유효한_AccessToken으로_토스_결제수단을_조회해_동기화한다() {
        MethodsResponse response = new MethodsResponse(
            true, null, List.of(), List.of());
        when(brandPayTokenService.getValidAccessToken(1L))
            .thenReturn("valid-access-token");
        when(brandPayMethodClient.getAll("valid-access-token"))
            .thenReturn(response);

        service.synchronize(1L);

        verify(syncService).synchronize(1L, response);
    }
}
