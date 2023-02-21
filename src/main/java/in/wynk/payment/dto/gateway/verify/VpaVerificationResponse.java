package in.wynk.payment.dto.gateway.verify;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.payu.VerificationType;
import in.wynk.payment.dto.response.payu.PayUVpaVerificationResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VpaVerificationResponse extends AbstractVerificationResponse {
    private int isVPAValid;
    private String vpa;
    private String status;
    private String payerAccountName;
    private boolean autoPayVPAValid;
    private boolean autoPayBankValid;

    public static VpaVerificationResponse from(PayUVpaVerificationResponse vpaVerificationResponse){
        return VpaVerificationResponse.builder()
                .vpa(vpaVerificationResponse.getVpa())
                .valid(vpaVerificationResponse.isValid())
                .verificationType(VerificationType.VPA)
                .isVPAValid(vpaVerificationResponse.getIsVPAValid())
                .status(vpaVerificationResponse.getStatus())
                .payerAccountName(vpaVerificationResponse.getPayerAccountName())
                .autoPayVPAValid(vpaVerificationResponse.isAutoPayVPAValid())
                .autoPayBankValid(vpaVerificationResponse.isAutoPayBankValid())
                .build();
    }
}
