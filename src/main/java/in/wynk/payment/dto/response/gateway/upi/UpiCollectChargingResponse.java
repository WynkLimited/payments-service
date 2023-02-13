package in.wynk.payment.dto.response.gateway.upi;

import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@ToString
@SuperBuilder
@Payment(groupId = "UPI", mode = "collect")
public class UpiCollectChargingResponse extends AbstractNonSeamlessUpiChargingResponse{
}
