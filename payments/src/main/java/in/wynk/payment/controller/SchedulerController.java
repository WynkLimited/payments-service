package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.dto.EmptyResponse;
import in.wynk.payment.scheduler.PaymentRenewalsScheduler;
import in.wynk.payment.scheduler.PaymentTDRScheduler;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Query;
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


    @GetMapping("/start/renewals/custom")
    @AnalyseTransaction(name = "customPaymentRenew")
    public EmptyResponse startPaymentRenew(@RequestParam String startDateTime, @RequestParam String endDateTime) {
        String requestId = MDC.get(REQUEST_ID);
        String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        String clientAlias = cachingService.getClientById(clientId).getAlias();
        executorService.submit(() -> paymentRenewalsScheduler.paymentRenewCustom(requestId, clientAlias, startDateTime, endDateTime));
        return EmptyResponse.builder().build();
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
    @GetMapping("/send/notifications/custom")
    @AnalyseTransaction(name = "customRenewNotification")
    public EmptyResponse startRenewNotificationCustom(@RequestParam String startDateTime, @RequestParam String endDateTime) {
        String requestId = MDC.get(REQUEST_ID);
        String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        String clientAlias = cachingService.getClientById(clientId).getAlias();
        executorService.submit(() -> paymentRenewalsScheduler.sendNotificationsCustom(requestId, clientAlias, startDateTime, endDateTime));
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