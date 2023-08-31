package in.wynk.payment.dto.invoice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@AnalysedEntity
@RequiredArgsConstructor
public class CallbackInvoiceKafkaMessage extends InvoiceKafkaMessage {

    private String source;
    @Analysed
    private Long timestamp;
    @Analysed
    @JsonProperty("LOB")
    private String lob;
    @Analysed
    @JsonProperty("customerAccountNo")
    private String customerAccountNumber;
    @Analysed
    @JsonProperty("invoiceNumber")
    private String invoiceId;
    @Analysed
    private String status;
    @Analysed
    private String description;
}