package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.common.dto.EmptyResponse;
import in.wynk.payment.scheduler.PaymentDumpService;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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
        String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        executorService.submit(() -> paymentDumpService.startPaymentDumpS3Export(requestId, days, clientId));
        return EmptyResponse.response();
    }

    @GetMapping("/dump")
    @AnalyseTransaction(name = "transactionDailyDump")
    public EmptyResponse transactionDailyDump(@RequestParam long startTime) {
        String requestId = MDC.get(REQUEST_ID);
        String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        executorService.submit(() -> paymentDumpService.startPaymentDumpS3Export(requestId, startTime, clientId));
        return EmptyResponse.response();
    }

}