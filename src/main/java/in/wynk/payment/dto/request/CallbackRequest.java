package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallbackRequest<T> {

    private String transactionId;
    private T body;
    private String returnUrl;
    private String transactionEvent;
    private String amount;
    private String title;
    private String id;
}
