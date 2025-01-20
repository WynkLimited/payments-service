package in.wynk.payment.dto.invoice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
@KafkaEvent(topic = "${wynk.kafka.producers.invoice.generate.topic}")
public class GenerateInvoiceKafkaMessage extends InvoiceKafkaMessage {
    @Analysed
    private String msisdn;
    @Analysed
    private String clientAlias;
    @Analysed
    private String txnId;
    @Analysed
    private String type;
    @Analysed
    private String skip_delivery;

    public static GenerateInvoiceKafkaMessage from(GenerateInvoiceEvent event, String clientAlias){
        return GenerateInvoiceKafkaMessage.builder()
                .msisdn(event.getMsisdn())
                .txnId(event.getTxnId())
                .clientAlias(clientAlias)
                .type(event.getType())
                .skip_delivery(event.getSkipDelivery())
                .build();
    }
}
