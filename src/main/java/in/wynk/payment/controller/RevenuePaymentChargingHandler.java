package in.wynk.payment.controller;

import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.constant.PaymentErrorType;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.IMerchantPaymentChargingService;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payment")
public class RevenuePaymentChargingHandler {

    private ApplicationContext context;

    @PostMapping("/charge/{sid}")
    public ResponseEntity doCharging(@PathVariable String sid, @RequestBody ChargingRequest request) {
        IMerchantPaymentChargingService chargingService;
        try {
            chargingService = this.context.getBean(request.getPaymentOption().getType(), IMerchantPaymentChargingService.class);
        } catch (BeansException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY001);
        }
        BaseResponse baseResponse = chargingService.doCharging(request);
        return new ResponseEntity(baseResponse.getBody(), baseResponse.getStatus());
    }

}
