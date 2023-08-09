package in.wynk.payment.dto.aps.common;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    * Static Value - UPI, DEBIT_CARD etc
    */
    private String paymentMode;
    /**
     * For APS this lob will be WYK for one time payment,
     * LOB_AUTO_PAY_REGISTER for mandate creation and SI_WYNK for renewal
     */
    @Builder.Default
    private String lob = ApsConstant.APS_LOB_WYNK;
    /**
     * Static Value - INR
     */
    @Builder.Default
    private String currency = "INR";
    /**
     * Amount in Double Format
     */
    private double paymentAmount;

    /**
     * Product Category for AutoPay
     */

    private String productCategory;

    /**
     * Amount in Double Format
     * Maximum mandate amount for which txn is eligible for autopay.
     */
    private double mandateAmount;
    /**
     * Mandate start date in epoch time format
     */
    private long paymentStartDate;
    /**
     * Mandate end date in epoch time format
     */
    private long paymentEndDate;

    /**
     * Send this flag as true in case of bill payment otherwise false for other/dummy transactions.
     */
    @Builder.Default
    @JsonProperty("isBillPayment")
    private boolean billPayment = true;
}
