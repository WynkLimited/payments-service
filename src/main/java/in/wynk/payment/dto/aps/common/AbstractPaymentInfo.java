package in.wynk.payment.dto.aps.common;

import in.wynk.common.constant.BaseConstants;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public abstract class AbstractPaymentInfo {
    /**
    * Static Value - UPI
    */
    private String paymentMode;
    /**
     * Static Value - TBD
     */
    @Builder.Default
    private String lob = BaseConstants.WYNK;
    /**
     * Static Value - INR
     */
    @Builder.Default
    private String currency = "INR";
    /**
     * Amount in Double Format
     */
    private double paymentAmount;
}
