package com.moongcheap_backend.payments.presentation;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 백엔드에 함께 패키징된 BrandPay 결제수단 등록 콘솔의 진입점을 제공한다.
 *
 * <p>Spring Security의 기본 인증 규칙을 그대로 적용하므로 로그인 세션이 있는
 * 사용자만 페이지와 정적 자산에 접근할 수 있다.</p>
 */
@Controller
public class BrandPayTestPageController {

    @GetMapping({"/brandpay-test", "/brandpay-test/"})
    public String page() {
        return "forward:/brandpay-test/index.html";
    }
}
