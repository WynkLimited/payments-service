package in.wynk.payment.presentation.dto.status;

import in.wynk.common.enums.TransactionStatus;
import in.wynk.error.codes.core.dao.entity.ErrorCode;
import in.wynk.payment.dto.AbstractPack;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.Objects;

@Getter
@SuperBuilder
public class FailurePaymentStatusResponse extends PaymentStatusResponse {

    private String failureType;
    private String title;
    private String subtitle;
    private String description;
    private String buttonText;
    private boolean buttonArrow;

    public static FailurePaymentStatusResponse populate (ErrorCode errorCode, String subtitle, String buttonText, boolean buttonArrow, String tid, Integer planId,
                                                         AbstractPack packDetails, TransactionStatus transactionStatus, String redirectUrl, String paymentGroup, String itemId) {
        FailurePaymentStatusResponseBuilder<?, ?> builder = FailurePaymentStatusResponse.builder().buttonArrow(buttonArrow)
                .buttonText(buttonText)
                .description(errorCode.getInternalMessage())
                .failureType(errorCode.getExternalCode())
                .subtitle(subtitle)
                .title(errorCode.getExternalMessage())
                .tid(tid)
                .redirectUrl(redirectUrl)
                .transactionStatus(transactionStatus)
                .paymentGroup(paymentGroup);
        if (Objects.nonNull(planId)) {
            builder.planId(planId);
            builder.packDetails(packDetails);
        } else if (Objects.nonNull(itemId)) {
            builder.itemId(itemId);
        }
        return builder.build();
    }
}