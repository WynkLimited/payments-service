package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.common.dto.EmptyResponse;
import in.wynk.payment.scheduler.PaymentDumpService;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutorService;

import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/s2s/v1/payment")
public class PaymentDumpController {

    private final ExecutorService executorService;
    private final PaymentDumpService paymentDumpService;

    @GetMapping("/dump/{days}")
    @AnalyseTransaction(name = "transactionWeeklyDump")
    public EmptyResponse transactionWeeklyDump(@PathVariable int days) {
        String requestId = MDC.get(REQUEST_ID);
        executorService.submit(() -> paymentDumpService.startPaymentDumpS3Export(requestId, days));
        return EmptyResponse.response();
    }

}