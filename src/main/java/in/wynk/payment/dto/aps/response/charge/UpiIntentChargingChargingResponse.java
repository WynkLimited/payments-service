package in.wynk.payment.dto.aps.response.charge;


import lombok.*;

@Getter
@ToString
@NoArgsConstructor
public class UpiIntentChargingChargingResponse extends AbstractUpiChargingResponse {
    private String upiLink;
}
