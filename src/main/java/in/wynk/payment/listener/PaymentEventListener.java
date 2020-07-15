package in.wynk.payment.listener;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.payment.core.event.RecurringPaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PaymentEventListener {

    @EventListener
    @AnalyseTransaction(name = "recurringPaymentEvent")
    public void onRecurringPaymentEvent(RecurringPaymentEvent event) { // for auditing and stop recurring in external payment gateway
    }

}
