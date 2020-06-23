package in.wynk.payment.controller;

import in.wynk.commons.dto.SessionDTO;
import in.wynk.payment.dto.PaymentOptionsDTO;
import in.wynk.payment.service.IPaymentOptionService;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wynk/v1/payment/")
public class PaymentOptionsHanlder {

    @Autowired
    private IPaymentOptionService paymentMethodService;

    @GetMapping("options/{sid}")
    @ManageSession(sessionId = "#sid")
    public PaymentOptionsDTO getPaymentMethods(@PathVariable String sid, @RequestParam String planId) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        return paymentMethodService.getPaymentOptions(sessionDTO, planId);
    }
}


