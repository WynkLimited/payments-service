package in.wynk.payment.consumer;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.dto.invoice.GenerateInvoiceEvent;
import in.wynk.payment.dto.invoice.GenerateInvoiceRequest;
import in.wynk.payment.dto.invoice.InvoiceCallbackEvent;
import in.wynk.payment.dto.invoice.InvoiceEvent;
import in.wynk.payment.service.InvoiceManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import java.util.Objects;

@Service
@Slf4j
public class InvoiceConsumptionHandler implements InvoiceHandler<InvoiceEvent> {

    private final InvoiceManagerService invoiceManager;

    public InvoiceConsumptionHandler (InvoiceManagerService invoiceManager) {
        this.invoiceManager = invoiceManager;
    }

    @Override
    @AnalyseTransaction(name="generateInvoice")
    public void generateInvoice(InvoiceEvent event) {
        try {
            GenerateInvoiceEvent dto = (GenerateInvoiceEvent) event;
            AnalyticService.update(dto);
            if (ObjectUtils.isEmpty(dto) || Objects.isNull(dto.getTransaction()) || Objects.isNull(dto.getPurchaseDetails())) {
                throw new WynkRuntimeException(PaymentErrorType.PAY440);
            }
            invoiceManager.generate(GenerateInvoiceRequest.builder()
                    .transaction(dto.getTransaction())
                    .purchaseDetails(dto.getPurchaseDetails())
                    .build());
        } catch (WynkRuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error(PaymentLoggingMarker.KAFKA_CONSUMPTION_HANDLING_ERROR, ex.getMessage(), ex);
            throw new WynkRuntimeException(PaymentErrorType.PAY444, ex);
        }
    }

    @Override
    @AnalyseTransaction(name="invoiceCallback")
    public void processCallback(InvoiceEvent event) {
        try{
            InvoiceCallbackEvent dto = (InvoiceCallbackEvent) event;
            AnalyticService.update(dto);

        } catch(Exception ex){
            log.error(PaymentLoggingMarker.KAFKA_CONSUMPTION_HANDLING_ERROR, ex.getMessage(), ex);
            throw new WynkRuntimeException(PaymentErrorType.PAY444, ex);
        }
    }
}
