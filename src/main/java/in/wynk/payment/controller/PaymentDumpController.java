package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.EmptyResponse;
import in.wynk.common.dto.WynkResponse;
import in.wynk.payment.scheduler.PaymentDumpService;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/wynk/s2s/v1/payment")
public class PaymentDumpController {
    private final ExecutorService executorService;
    private final PaymentDumpService paymentDumpService;

    public PaymentDumpController(ExecutorService executorService, PaymentDumpService paymentDumpService) {
        this.executorService = executorService;
        this.paymentDumpService = paymentDumpService;
    }

    @GetMapping("/dump")
    @AnalyseTransaction(name = "transactionWeeklyDump")
    public EmptyResponse allPlans() {
        String requestId = MDC.get(REQUEST_ID);
        executorService.submit(()-> paymentDumpService.startPaymentDumpS3Export(requestId));
        return EmptyResponse.response();
    }
}
