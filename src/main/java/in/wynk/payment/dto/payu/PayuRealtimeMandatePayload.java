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
    private String status; //active, deleted, pause,
    String authPayuId;
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

    public String getAction() {
        return action.toString();
    }
}
