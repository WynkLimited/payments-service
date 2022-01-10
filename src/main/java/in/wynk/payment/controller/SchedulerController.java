package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.dto.EmptyResponse;
import in.wynk.payment.scheduler.PaymentRenewalsScheduler;
import in.wynk.payment.utils.LoadClientUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutorService;

import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@RestController
@RequiredArgsConstructor
@RequestMapping("wynk/s2s/v1/scheduler")
public class SchedulerController {

    private final ExecutorService executorService;
    private final PaymentRenewalsScheduler paymentRenewalsScheduler;

    @GetMapping("/start/renewals")
    @AnalyseTransaction(name = "paymentRenew")
    public EmptyResponse startPaymentRenew() {
        LoadClientUtils.loadClient(true);
        String requestId = MDC.get(REQUEST_ID);
        executorService.submit(() -> paymentRenewalsScheduler.paymentRenew(requestId, ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT)));
        return EmptyResponse.response();
    }

    @GetMapping("/start/seRenewal")
    @AnalyseTransaction(name = "sePaymentRenew")
    public EmptyResponse startSEPaymentRenew() {
        LoadClientUtils.loadClient(true);
        String requestId = MDC.get(REQUEST_ID);
        executorService.submit(() -> paymentRenewalsScheduler.startSeRenewals(requestId, ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT)));
        return EmptyResponse.response();
    }

    @GetMapping("/send/notifications")
    @AnalyseTransaction(name = "renewNotification")
    public EmptyResponse startRenewNotification() {
        LoadClientUtils.loadClient(true);
        String requestId = MDC.get(REQUEST_ID);
        executorService.submit(() -> paymentRenewalsScheduler.sendNotifications(requestId, ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT)));
        return EmptyResponse.response();
    }

}