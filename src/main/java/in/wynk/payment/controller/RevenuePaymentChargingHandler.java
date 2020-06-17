package in.wynk.payment.controller;

import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.IMerchantPaymentChargingService;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payment")
public class RevenuePaymentChargingHandler {

    private ApplicationContext context;

    @PostMapping("/charge/{sid}")
    public ResponseEntity doCharging(@PathVariable String sid, @RequestBody ChargingRequest request) {
        try {
            IMerchantPaymentChargingService chargingService = this.context.getBean(request.getPaymentOption().getType(), IMerchantPaymentChargingService.class);
            BaseResponse baseResponse = chargingService.doCharging(request);
            return new ResponseEntity(baseResponse.getBody(), baseResponse.getStatus());
        } catch (Exception e) {
            throw new WynkRuntimeException("unsupported payment method");
        }
    }

}
