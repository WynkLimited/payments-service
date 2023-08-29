package in.wynk.payment.dto.invoice;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.event.GenerateInvoiceEvent;
import in.wynk.stream.advice.KafkaEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@RequiredArgsConstructor
@KafkaEvent(topic = "${wynk.kafka.producers.invoice.generate.topic}")
public class GenerateInvoiceKafkaMessage extends InvoiceKafkaMessage {
    @Analysed
    private String invoiceId;
    @Analysed
    private String msisdn;
    @Analysed
    private String clientAlias;
    @Analysed
    private String txnId;
    /*@Analysed
    private TransactionDTO transaction;
    @Autowired
    private PurchaseDetails purchaseDetails;*/

    public static GenerateInvoiceKafkaMessage from(GenerateInvoiceEvent event){
        return GenerateInvoiceKafkaMessage.builder()
                .invoiceId(event.getInvoiceId())
                .msisdn(event.getMsisdn())
                .txnId(event.getTxnId())
                //.transaction(event.getTransaction())
                .clientAlias(event.getClientAlias())
                //.purchaseDetails(event.getPurchaseDetails())
                .build();
    }
}
