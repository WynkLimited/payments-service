package in.wynk.payment.dto.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.payu.PayUCardInfo;
import in.wynk.payment.dto.payu.VerificationType;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BinVerificationResponse extends AbstractVerificationResponse {
    private String cardType;
    private String isDomestic;
    private String issuingBank;
    private String cardCategory;
    private String siSupport;
    private String zeroRedirectSupport;
    private String isATMPINCard;
    private boolean authorizedByBank;

    public static BinVerificationResponse from(PayUCardInfo binVerificationResponse){
        return BinVerificationResponse.builder()
                .valid(binVerificationResponse.isValid())
                .verificationType(VerificationType.BIN)
                .cardType(binVerificationResponse.getCardType())
                .isDomestic(binVerificationResponse.getIsDomestic())
                .issuingBank(binVerificationResponse.getIssuingBank())
                .cardCategory(binVerificationResponse.getCardCategory())
                .siSupport(binVerificationResponse.getSiSupport())
                .autoRenewSupported(binVerificationResponse.isAutoRenewSupported())
                .build();
    }
}
