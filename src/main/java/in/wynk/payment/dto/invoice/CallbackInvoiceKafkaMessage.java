package in.wynk.payment.dto.invoice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@AnalysedEntity
@RequiredArgsConstructor
public class CallbackInvoiceKafkaMessage extends InvoiceKafkaMessage {
    @Analysed
    @Field("LOB")
    private String lob;
    @Analysed
    @Field("customerAccountNo")
    private String customerAccountNumber;
    @Analysed
    @Field("invoiceNumber")
    private String invoiceId;
    @Analysed
    private String status;
    @Analysed
    private String description;
}