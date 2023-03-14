package in.wynk.payment.dto.gateway.card;

import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@Payment(groupId = "CARD", mode = "OTP_LESS")
public class OtpLessCardChargingResponse extends AbstractSeamlessCardChargingResponse{
}
