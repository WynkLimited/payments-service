package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.common.dto.EmptyResponse;
import in.wynk.payment.scheduler.PaymentRenewalsScheduler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("wynk/s2s/v1/scheduler")
public class SchedulerController {

    private final PaymentRenewalsScheduler paymentRenewalsScheduler;
    private final ExecutorService executorService;

    public SchedulerController(PaymentRenewalsScheduler paymentRenewalsScheduler, ExecutorService executorService) {
        this.paymentRenewalsScheduler = paymentRenewalsScheduler;
        this.executorService = executorService;
    }

    @GetMapping("/start/renewals")
    @AnalyseTransaction(name = "paymentRenew")
    public EmptyResponse startPaymentRenew() {
        executorService.submit(()-> paymentRenewalsScheduler.paymentRenew());
        return EmptyResponse.response();
    }

    @GetMapping("/start/seRenewal")
    @AnalyseTransaction(name = "sePaymentRenew")
    public EmptyResponse startSEPaymentRenew() {
        executorService.submit(()-> paymentRenewalsScheduler.startSeRenewals());
        return EmptyResponse.response();
    }

}
