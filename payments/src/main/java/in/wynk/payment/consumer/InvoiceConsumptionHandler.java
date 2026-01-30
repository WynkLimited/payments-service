package in.wynk.payment.consumer;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.dto.invoice.*;
import in.wynk.payment.service.InvoiceManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Calendar;
import java.util.Objects;

@Service
@Slf4j
public class InvoiceConsumptionHandler implements InvoiceHandler<InvoiceKafkaMessage> {

    private final InvoiceManagerService invoiceManager;

    public InvoiceConsumptionHandler (InvoiceManagerService invoiceManager) {
        this.invoiceManager = invoiceManager;
    }

    @Override
    public void generateInvoice(InvoiceKafkaMessage message) {
        try {
            GenerateInvoiceKafkaMessage dto = (GenerateInvoiceKafkaMessage) message;
            AnalyticService.update(dto);
            if (ObjectUtils.isEmpty(dto) || Objects.isNull(dto.getTxnId()) || Objects.isNull(dto.getMsisdn()) || Objects.isNull(dto.getType())) {
                throw new WynkRuntimeException(PaymentErrorType.PAY440);
            }
            invoiceManager.generate(GenerateInvoiceRequest.builder()
                    .msisdn(dto.getMsisdn())
                    .clientAlias(dto.getClientAlias())
                    .txnId(dto.getTxnId())
                    .type(dto.getType())
                    .skipDelivery(dto.getSkip_delivery())
                    .build());
        } catch (Exception ex) {
            log.error(PaymentLoggingMarker.INVOICE_GENERATION_FAILED, ex.getMessage(), ex);
        }
    }

    @Override
    public void processCallback(InvoiceKafkaMessage message) {
        try{
            CallbackInvoiceKafkaMessage dto = (CallbackInvoiceKafkaMessage) message;
            AnalyticService.update(dto);
            if (ObjectUtils.isEmpty(dto) || Objects.isNull(dto.getLob()) || Objects.isNull(dto.getStatus())) {
                throw new WynkRuntimeException(PaymentErrorType.PAY444);
            }
            invoiceManager.processCallback(InvoiceCallbackRequest.builder()
                    .lob(dto.getLob())
                    .customerAccountNumber(dto.getCustomerAccountNumber())
                    .invoiceId(dto.getInvoiceId())
                    .status(dto.getStatus())
                    .type(dto.getType())
                    .description(dto.getDescription()).build());
        } catch(Exception ex){
            log.error(PaymentLoggingMarker.INVOICE_PROCESS_CALLBACK_FAILED, ex.getMessage(), ex);
        }
    }
}
