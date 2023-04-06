package in.wynk.payment.dto.response.paymentoption;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
public abstract class AbstractSavedPaymentDTO {
    private String id;
    private String code;
    private String group;
    private Integer order;
    @JsonProperty("is_preferred")
    private boolean preferred;
    @JsonProperty("is_favourite")
    private boolean favorite;
    @JsonProperty("is_recommended")
    private boolean recommended;
    @JsonProperty("is_express_checkout")
    private boolean expressCheckout;
    private String health;
    @JsonProperty("is_auto_pay_enabled")
    private boolean autoPayEnabled;// upi collect will never support autoPay. Wallet might support.So, this will always be false for UPI and wallet
}
