package in.wynk.payment.dto.gpbs.notification.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.IAPNotification;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @author Nishesh Pandey
 */

@Getter
@Setter
@AnalysedEntity
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GooglePlayNotificationMessage{
    private Message message;
    private String subscription;
}
