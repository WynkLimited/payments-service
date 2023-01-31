package in.wynk.payment.dto.response.payu;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.IVerificationResponse;
import in.wynk.payment.json.deserializer.NumericBooleanDeserializer;
import lombok.*;

@Getter
@Builder
@AnalysedEntity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class PayUVpaVerificationResponse implements IVerificationResponse {

    @Setter
    @Analysed
    private boolean isValid;

    @Analysed
    private int isVPAValid;

    @Analysed
    private String vpa;

    @Analysed
    private String status;

    @Analysed
    private String payerAccountName;

    /**
     * Indicate whether the VPA has registered for Recurring Payments or AutoPay:
     * 1: Indicates that VPA has registered for Recurring Payments
     * 0: Indicates that VPA has not registered for Recurring Payments
     */
    @JsonProperty("isAutoPayVPAValid")
    @JsonDeserialize(using = NumericBooleanDeserializer.class)
    private boolean autoPayVPAValid;

    /**
     * Indicate whether the corresponding bank account has registered for Recurring Payments or AutoPay:
     * 1: Indicates that bank account has registered for Recurring Payments
     * 0: Indicates that bank account has not registered for Recurring Payments
     */
    @JsonProperty("isAutoPayBankValid")
    @JsonDeserialize(using = NumericBooleanDeserializer.class)
    private boolean autoPayBankValid;

}