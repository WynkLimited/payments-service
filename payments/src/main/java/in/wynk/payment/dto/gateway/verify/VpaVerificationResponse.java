package in.wynk.payment.dto.gateway.verify;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.payu.VerificationType;
import in.wynk.payment.dto.response.payu.PayUVpaVerificationResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VpaVerificationResponse extends AbstractVerificationResponse {
    private String vpa;
    private String status;
    private String payerAccountName;
    @JsonProperty("isAutoPayHandleValid")
    private boolean autoPayHandleValid;
    private boolean autoPayVPAValid;
    private boolean autoPayBankValid;

    public static VpaVerificationResponse from(PayUVpaVerificationResponse vpaVerificationResponse){
        return in.wynk.payment.dto.gateway.verify.VpaVerificationResponse.builder()
                .vpa(vpaVerificationResponse.getVpa())
                .valid(vpaVerificationResponse.getIsVPAValid() == 1)
                .verificationType(VerificationType.VPA)
                .status(vpaVerificationResponse.getStatus())
                .payerAccountName(vpaVerificationResponse.getPayerAccountName())
                .autoPayVPAValid(vpaVerificationResponse.isAutoPayVPAValid())
                .autoPayBankValid(vpaVerificationResponse.isAutoPayBankValid())
                .build();
    }

    public static VpaVerificationResponse fromAps(in.wynk.payment.dto.aps.response.verify.VpaVerificationResponse vpaVerificationResponse){
        return in.wynk.payment.dto.gateway.verify.VpaVerificationResponse.builder()
                .vpa(vpaVerificationResponse.getVpa())
                .valid(vpaVerificationResponse.isVpaValid())
                .verificationType(VerificationType.VPA)
                .status(vpaVerificationResponse.getStatus())
                .payerAccountName(vpaVerificationResponse.getPayeeAccountName())
                .autoPayVPAValid(vpaVerificationResponse.isAutoPayHandleValid())
                .build();
    }
}
