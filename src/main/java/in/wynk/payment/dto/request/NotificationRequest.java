package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationRequest {
    private String payload;
    private String clientAlias;
    private PaymentGateway paymentGateway;
}