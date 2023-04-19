package in.wynk.payment.dto.gateway.upi;

import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Map;

@Getter
@ToString
@SuperBuilder
@Payment(groupId = "UPI", mode = "INAPP")
public class UpiCollectInAppChargingResponse extends AbstractSeamlessUpiChargingResponse {
    @Field("package_name")
    private String packageName;
}
