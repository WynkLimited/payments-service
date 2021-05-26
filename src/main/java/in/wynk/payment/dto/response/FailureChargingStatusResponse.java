package in.wynk.payment.dto.response;

import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.dto.AbstractPack;
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

    public static FailureChargingStatusResponse populate(FailureResponse failureResponse, String tid, int planId, AbstractPack packDetails, TransactionStatus transactionStatus) {
        return FailureChargingStatusResponse.builder().buttonArrow(failureResponse.isButtonArrow())
                                                    .buttonText(failureResponse.getButtonText())
                                                    .description(failureResponse.getDescription())
                                                    .failureType(failureResponse.getFailureType())
                                                    .subtitle(failureResponse.getSubtitle())
                                                    .title(failureResponse.getTitle())
                                                    .packDetails(packDetails)
                                                    .tid(tid)
                                                    .planId(planId)
                                                    .transactionStatus(transactionStatus)
                .build();
    }
}
