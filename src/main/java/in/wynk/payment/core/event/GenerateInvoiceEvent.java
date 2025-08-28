package in.wynk.payment.core.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.stream.advice.KafkaEvent;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AnalysedEntity
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@KafkaEvent(topic = "${wynk.data.platform.topic}", transactionName = "generateInvoiceEvent")
public class GenerateInvoiceEvent {
    @Analysed
    private String msisdn;
    @Analysed
    private String txnId;
    @Analysed
    private String clientAlias;
    @Analysed
    private String type;
    @Analysed
    private String skipDelivery;
}
