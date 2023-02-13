package in.wynk.payment.dto.response.gateway.upi;
import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@ToString
@SuperBuilder
@Payment(groupId = "UPI", mode = "inApp")
public class UpiCollectInAppChargingResponse extends AbstractSeamlessUpiChargingResponse {
    @Field("package_name")
    private String packageName;
}
