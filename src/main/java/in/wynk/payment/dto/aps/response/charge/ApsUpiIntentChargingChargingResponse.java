package in.wynk.payment.dto.aps.response.charge;


import lombok.*;

@Getter
@ToString
@NoArgsConstructor
public class ApsUpiIntentChargingChargingResponse extends AbstractApsUpiChargingResponse {
    private String upiLink;
}
