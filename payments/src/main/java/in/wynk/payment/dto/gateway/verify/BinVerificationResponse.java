package in.wynk.payment.dto.gateway.verify;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.payu.PayUCardInfo;
import in.wynk.payment.dto.payu.VerificationType;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BinVerificationResponse extends AbstractVerificationResponse {
    private String cardType;
    private boolean isDomestic;
    private String issuingBank;
    private String cardCategory;
    private String siSupport;
    private String zeroRedirectSupport;
    private boolean isATMPINCard;
    private boolean authorizedByBank;
    private boolean autoPayEnable;
    private boolean inAppOtpSupport;

    public static BinVerificationResponse from(PayUCardInfo binVerificationResponse){
        return in.wynk.payment.dto.gateway.verify.BinVerificationResponse.builder()
                .valid(binVerificationResponse.isValid())
                .verificationType(VerificationType.BIN)
                .cardType(binVerificationResponse.getCardType())
                .isDomestic("Y".equals(binVerificationResponse.getIsDomestic()))
                .issuingBank(binVerificationResponse.getIssuingBank())
                .cardCategory(binVerificationResponse.getCardCategory())
                .siSupport(binVerificationResponse.getSiSupport())
                .autoRenewSupported(binVerificationResponse.isAutoRenewSupported())
                .build();
    }

    public static BinVerificationResponse fromAps(in.wynk.payment.dto.aps.response.verify.BinVerificationResponse binVerificationResponse){
        return in.wynk.payment.dto.gateway.verify.BinVerificationResponse.builder()
                .valid(!binVerificationResponse.isBlocked())
                .verificationType(VerificationType.BIN)
                .cardType(binVerificationResponse.getCardNetwork())
                .isDomestic(binVerificationResponse.isDomestic())
                .issuingBank(binVerificationResponse.getBankName())
                .cardCategory(binVerificationResponse.getCardCategory())
                .siSupport(binVerificationResponse.isAutoPayEnable() ? "YES": "No")
                .autoRenewSupported(binVerificationResponse.isAutoPayEnable())
                .build();
    }
}
