package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@NoArgsConstructor
public class PayuRealtimeMandatePayload extends PayUCallbackRequestPayload {
    @JsonProperty("authpayuid")
    Long authPayuId;
    String message;
    String eventDate;
    String key;
    NotificationType notificationType;
    @JsonProperty("si_details")
    SiDetails siDetails;
    UPIMandateAction action;
    String dateTime;
    String endDate;
    String mandateNumber;
    String pauseStartDate;
    String pauseEndDate;
    //status will be active, deleted, pause,
    public String getAction() {
        return this.action.toString();
    }

    public String getFlow() {
        return PayUConstants.REALTIME_MANDATE_CALLBACK_ACTION;
    }
}
