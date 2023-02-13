package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.dto.ApsChargingResponse;
import in.wynk.payment.dto.request.AbstractChargingRequestV2;
import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.dto.response.AbstractChargingResponse;
import in.wynk.payment.dto.response.IVerificationResponse;
import in.wynk.payment.service.IMerchantVerificationService;
import in.wynk.payment.service.PaymentGatewayManager;
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

    private PaymentGatewayManager manager;
    private final IEntityCacheService<PaymentMethod, String> paymentMethodCache;

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

    //This version is for payment refactoring task
    @PostMapping("/charge/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCharging")
    public WynkResponseEntity<AbstractChargingResponse> doCharging(@PathVariable String sid, @RequestBody AbstractChargingRequestV2 request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(PAYMENT_METHOD, paymentMethodCache.get(request.getPaymentDetails().getPaymentId()).getPaymentCode().name());
        AnalyticService.update(request);
        final WynkResponseEntity.WynkResponseEntityBuilder<AbstractChargingResponse> builder = WynkResponseEntity.builder();
        manager.chargeV2(request);
        //return  builder.data(manager.chargeV2(request)).build();
        return null;
    }
}