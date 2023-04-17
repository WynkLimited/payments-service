package in.wynk.payment.dto.aps.request.predebit;

import in.wynk.payment.dto.apb.ApbConstants;
import in.wynk.payment.dto.aps.common.ApsConstant;
import in.wynk.payment.dto.aps.common.SiPaymentInfo;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
public class PreDebitNotificationRequest extends SiPaymentInfo {
    private String preDebitRequestId;
    private String debitDate;
    private double amount;
    @Builder.Default
    private Integer circleId = ApsConstant.DEFAULT_CIRCLE_ID;
}
