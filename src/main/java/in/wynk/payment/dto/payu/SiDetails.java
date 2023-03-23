package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.common.enums.BillingCycle;
import lombok.Getter;

import java.text.SimpleDateFormat;
import java.util.Date;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SiDetails {

    private String billingAmount;
    private String billingCurrency;
    private BillingCycle billingCycle;
    private Integer billingInterval;
    private String paymentStartDate;
    private String paymentEndDate;
    private String remarks;

    private SiDetails() {
    }

    public SiDetails(BillingCycle billingCycle, Double billingAmount, Date paymentStartDate, Date paymentEndDate) {
        this();
        this.billingCycle = billingCycle;
        this.billingInterval = 1;
        // catpure mandate for 2x amount up-to 5k
        this.billingAmount = String.format("%.2f", billingAmount);
        this.billingCurrency = "INR";
        this.paymentStartDate = new SimpleDateFormat("yyyy-MM-dd").format(paymentStartDate);
        this.paymentEndDate = new SimpleDateFormat("yyyy-MM-dd").format(paymentEndDate);
    }

    public SiDetails(BillingCycle billingCycle, Integer billingInterval, Double billingAmount, Date paymentStartDate, Date paymentEndDate) {
        this(billingCycle, billingAmount, paymentStartDate, paymentEndDate);
        if (billingCycle != BillingCycle.ADHOC && billingCycle != BillingCycle.ONCE) {
            this.billingInterval = billingInterval;
        }
    }

    public SiDetails(BillingCycle billingCycle, Integer billingInterval, Double billingAmount, Date paymentStartDate, Date paymentEndDate, String remarks) {
        this(billingCycle, billingInterval, billingAmount, paymentStartDate, paymentEndDate);
        this.remarks = remarks;
    }

}
