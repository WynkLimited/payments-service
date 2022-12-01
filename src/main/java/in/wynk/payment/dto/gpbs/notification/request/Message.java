package in.wynk.payment.dto.gpbs.notification.request;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
public class Message {
    private Map<String, String> attributes;
    private String data;
    private String messageId;
}
