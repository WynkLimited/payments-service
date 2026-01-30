package in.wynk.payment.dto.response;

import in.wynk.common.enums.TransactionStatus;
import in.wynk.error.codes.core.dao.entity.ErrorCode;
import in.wynk.payment.dto.AbstractPack;
import in.wynk.payment.dto.response.presentation.paymentstatus.AbstractPayUPaymentStatus;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
public class FailureChargingStatusResponseV2 extends AbstractPayUPaymentStatus {
    private String failureType;
    private String title;
    private String subtitle;
    private String description;
    private String buttonText;
    private boolean buttonArrow;

    public static FailureChargingStatusResponseV2 populate (ErrorCode errorCode, String subtitle, String buttonText, boolean buttonArrow, String tid, int planId, AbstractPack packDetails,
                                                            TransactionStatus transactionStatus, String redirectUrl) {
        return FailureChargingStatusResponseV2.builder().buttonArrow(buttonArrow)
                .buttonText(buttonText)
                .description(errorCode.getInternalMessage())
                .failureType(errorCode.getExternalCode())
                .subtitle(subtitle)
                .title(errorCode.getExternalMessage())
                .packDetails(packDetails)
                .tid(tid)
                .redirectUrl(redirectUrl)
                .planId(planId)
                .transactionStatus(transactionStatus)
                .build();
    }
}
