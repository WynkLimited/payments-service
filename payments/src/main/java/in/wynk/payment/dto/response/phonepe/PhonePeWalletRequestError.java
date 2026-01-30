package in.wynk.payment.dto.response.phonepe;

import in.wynk.payment.dto.response.phonepe.PhonePeResponseData;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class PhonePeWalletRequestError {
    private boolean success;
    private String code;
    private String message;
    private PhonePeResponseData data;
}
