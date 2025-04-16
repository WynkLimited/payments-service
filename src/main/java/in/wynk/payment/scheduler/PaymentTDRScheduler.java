package in.wynk.payment.scheduler;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.data.aspect.advice.Transactional;
import in.wynk.payment.service.IPaymentTDRManager;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;

@Service
@Slf4j
public class PaymentTDRScheduler {
    @Autowired
    private IPaymentTDRManager paymentTDRManager;

    @ClientAware(clientAlias = "#clientAlias")
    @AnalyseTransaction(name = "fetchTdrAfterDelayInternal")
    @Transactional(transactionManager = "#clientAlias", source = "payments")
    public void fecthTDR(String requestId, String clientAlias) {
        MDC.put(REQUEST_ID, requestId);
        AnalyticService.update(REQUEST_ID, requestId);
        AnalyticService.update("clientAlias", clientAlias);
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("fetchTDRWithDelayInit", true);
        paymentTDRManager.fetchTDR(requestId);
    }
}
