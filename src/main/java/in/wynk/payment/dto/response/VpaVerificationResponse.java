package in.wynk.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VpaVerificationResponse {

    String status;
    String vpa;
    int isVPAValid;
    String payerAccountName;
}
