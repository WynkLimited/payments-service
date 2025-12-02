package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.dto.EmptyResponse;
import in.wynk.payment.dto.request.PaymentRenewalRequest;
import in.wynk.payment.dto.request.RenewNotificationRequest;
import in.wynk.payment.scheduler.PaymentRenewalsScheduler;
import in.wynk.payment.scheduler.PaymentTDRScheduler;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutorService;

import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;

@RestController
@RequiredArgsConstructor
@RequestMapping("wynk/s2s/v1/scheduler")
public class SchedulerController {

    private final ExecutorService executorService;
    private final ClientDetailsCachingService cachingService;
    private final PaymentRenewalsScheduler paymentRenewalsScheduler;

    @Autowired
    private final PaymentTDRScheduler paymentTDRScheduler;

    @GetMapping("/start/renewals")
    @AnalyseTransaction(name = "paymentRenew")
    public EmptyResponse startPaymentRenew() {
        String requestId = MDC.get(REQUEST_ID);
        String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        executorService.submit(() -> paymentRenewalsScheduler.paymentRenew(requestId, cachingService.getClientById(clientId).getAlias()));
        return EmptyResponse.response();
    }


    @GetMapping("/start/renewals/param")
    @AnalyseTransaction(name = "paramPaymentRenew")
    public EmptyResponse startPaymentRenew(@RequestParam int offsetDay, @RequestParam int offsetTime) {
        String requestId = MDC.get(REQUEST_ID);
        PaymentRenewalRequest request = new PaymentRenewalRequest();
        request.setOffsetDay(offsetDay);
        request.setOffsetTime(offsetTime);
        executorService.submit(() -> paymentRenewalsScheduler.paymentRenew(requestId, request));
        return EmptyResponse.response();
    }

    @GetMapping("/start/prepareRenewals")
    @AnalyseTransaction(name = "prepareNextDayRenewalWindow")
    public EmptyResponse prepareNextDayRenewals() {
        String requestId = MDC.get(REQUEST_ID);
        String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        executorService.submit(() -> paymentRenewalsScheduler.prepareNextDayRenewals(requestId, cachingService.getClientById(clientId).getAlias()));
        return EmptyResponse.response();
    }


    @GetMapping("/start/seRenewal")
    @AnalyseTransaction(name = "sePaymentRenew")
    public EmptyResponse startSEPaymentRenew() {
        String requestId = MDC.get(REQUEST_ID);
        String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        executorService.submit(() -> paymentRenewalsScheduler.startSeRenewals(requestId, clientId));
        return EmptyResponse.response();
    }

    @GetMapping("/send/notifications")
    @AnalyseTransaction(name = "renewNotification")
    public EmptyResponse startRenewNotification() {
        String requestId = MDC.get(REQUEST_ID);
        String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        executorService.submit(() -> paymentRenewalsScheduler.sendNotifications(requestId, cachingService.getClientById(clientId).getAlias()));
        return EmptyResponse.response();
    }

    @GetMapping("/send/notifications/param")
    @AnalyseTransaction(name = "paramRenewNotification")
    public EmptyResponse startRenewNotification(@RequestParam int offsetDay, @RequestParam int offsetHour, @RequestParam int preOffsetDay) {
        String requestId = MDC.get(REQUEST_ID);

        RenewNotificationRequest request = new RenewNotificationRequest();
        request.setOffsetDay(offsetDay);
        request.setOffsetHour(offsetHour);
        request.setPreOffsetDay(preOffsetDay);
        executorService.submit(() -> paymentRenewalsScheduler.sendNotifications(requestId, request));
        return EmptyResponse.response();
    }

    @GetMapping("/start/fetchingTdr")
    @AnalyseTransaction(name = "fetchTdrAfterDelay")
    public EmptyResponse fetchTDR() {
        String requestId = MDC.get(REQUEST_ID);
        String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        executorService.submit(() -> paymentTDRScheduler.fecthTDR(requestId, cachingService.getClientById(clientId).getAlias()));
        return EmptyResponse.response();
    }

}