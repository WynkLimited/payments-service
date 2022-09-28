package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.dto.response.IVerificationResponse;
import in.wynk.payment.service.IMerchantVerificationService;
import in.wynk.payment.utils.LoadClientUtils;
import in.wynk.session.aspect.advice.ManageSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/v2/payment")
public class  RevenuePaymentControllerV2 {
    @PostMapping("/verify/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "verifyUserPaymentBin")
    public WynkResponseEntity<IVerificationResponse> verify(@PathVariable String sid, @Valid @RequestBody VerificationRequest request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(request);
        AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().name());
        WynkResponseEntity<IVerificationResponse> verificationResponseWynkResponseEntity = BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), IMerchantVerificationService.class).doVerify(request);
        AnalyticService.update(verificationResponseWynkResponseEntity.getBody().getData());
        return verificationResponseWynkResponseEntity;
    }
}