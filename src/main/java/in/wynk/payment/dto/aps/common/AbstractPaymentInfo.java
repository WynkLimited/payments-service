package in.wynk.payment.dto.aps.common;

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
    private String lob;
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
