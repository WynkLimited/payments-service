package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.common.dto.EmptyResponse;
import in.wynk.payment.scheduler.PaymentRenewalsScheduler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("wynk/v1/scheduler")
public class SchedulerController {

    private final PaymentRenewalsScheduler paymentRenewalsScheduler;

    public SchedulerController(PaymentRenewalsScheduler paymentRenewalsScheduler) {
        this.paymentRenewalsScheduler = paymentRenewalsScheduler;
    }

    @GetMapping("/start")
    @AnalyseTransaction(name = "paymentRenew")
    public void startPaymentRenew() {
        paymentRenewalsScheduler.paymentRenew();
    }

    @GetMapping("/start/seRenewal")
    @AnalyseTransaction(name = "paymentRenew")
    public EmptyResponse startSEPaymentRenew() {
        paymentRenewalsScheduler.startSeRenewals();
        return EmptyResponse.response();
    }

}
