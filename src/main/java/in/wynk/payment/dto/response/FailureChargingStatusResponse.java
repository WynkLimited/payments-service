package in.wynk.payment.dto.response;

import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.dto.AbstractPack;
import in.wynk.payment.dto.ErrorCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class FailureChargingStatusResponse extends AbstractChargingStatusResponse {

    private String failureType;
    private String title;
    private String subtitle;
    private String description;
    private String buttonText;
    private boolean buttonArrow;

    public static FailureChargingStatusResponse populate(ErrorCode errorCode, String subtitle, String buttonText, boolean buttonArrow, String tid, int planId, AbstractPack packDetails, TransactionStatus transactionStatus, String redirectUrl) {
        return FailureChargingStatusResponse.builder()
                .tid(tid)
                .planId(planId)
                .subtitle(subtitle)
                .buttonText(buttonText)
                .buttonArrow(buttonArrow)
                .packDetails(packDetails)
                .redirectUrl(redirectUrl)
                .transactionStatus(transactionStatus)
                .title(errorCode.getExternalMessage())
                .failureType(errorCode.getExternalCode())
                .description(errorCode.getInternalMessage())
                .build();
    }

}