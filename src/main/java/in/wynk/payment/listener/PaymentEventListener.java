package in.wynk.payment.listener;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.PaymentError;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.core.event.RecurringPaymentEvent;
import in.wynk.payment.service.IMerchantTransactionService;
import in.wynk.payment.service.IPaymentErrorService;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PaymentEventListener {

    private final RetryRegistry retryRegistry;
    private final IPaymentErrorService paymentErrorService;
    private final IMerchantTransactionService merchantTransactionService;

    public PaymentEventListener(RetryRegistry retryRegistry, IPaymentErrorService paymentErrorService, IMerchantTransactionService merchantTransactionService) {
        this.retryRegistry = retryRegistry;
        this.paymentErrorService = paymentErrorService;
        this.merchantTransactionService = merchantTransactionService;
    }


    @EventListener
    @AnalyseTransaction(name = "recurringPaymentEvent")
    public void onRecurringPaymentEvent(RecurringPaymentEvent event) { // for auditing and stop recurring in external payment gateway
    }

    @EventListener
    @AnalyseTransaction(name = "merchantTransactionEvent")
    public void onMerchantTransactionEvent(MerchantTransactionEvent event) {
        retryRegistry.retry(PaymentConstants.MERCHANT_TRANSACTION_UPSERT_RETRY_KEY).executeRunnable(() -> {
            merchantTransactionService.upsert(MerchantTransaction.builder()
                    .id(event.getId())
                    .externalTransactionId(event.getExternalTransactionId())
                    .request(event.getRequest())
                    .response(event.getResponse())
                    .build()
            );
        });
    }

    @EventListener
    @AnalyseTransaction(name = "paymentErrorEvent")
    public void onPaymentErrorEvent(PaymentErrorEvent event) {
        retryRegistry.retry(PaymentConstants.PAYMENT_ERROR_UPSERT_RETRY_KEY).executeRunnable(() -> {
            paymentErrorService.upsert(PaymentError.builder()
                    .id(event.getId())
                    .code(event.getCode())
                    .description(event.getDescription())
                    .build());
        });
    }

}
