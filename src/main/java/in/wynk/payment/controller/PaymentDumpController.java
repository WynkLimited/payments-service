package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.dto.EmptyResponse;
import in.wynk.payment.scheduler.PaymentDumpService;
import in.wynk.payment.utils.LoadClientUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutorService;

import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/s2s/v1/payment")
public class PaymentDumpController {

    private final ExecutorService executorService;
    private final PaymentDumpService paymentDumpService;

    @GetMapping("/dump/{days}")
    @AnalyseTransaction(name = "transactionWeeklyDump")
    public EmptyResponse transactionWeeklyDump(@PathVariable int days) {
        LoadClientUtils.loadClient(true);
        String requestId = MDC.get(REQUEST_ID);
        executorService.submit(() -> paymentDumpService.startPaymentDumpS3Export(requestId, days, ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT)));
        return EmptyResponse.response();
    }

    @GetMapping("/dump")
    @AnalyseTransaction(name = "transactionDailyDump")
    public EmptyResponse transactionDailyDump(@RequestParam long startTime) {
        LoadClientUtils.loadClient(true);
        String requestId = MDC.get(REQUEST_ID);
        executorService.submit(() -> paymentDumpService.startPaymentDumpS3Export(requestId, startTime, ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT)));
        return EmptyResponse.response();
    }

}