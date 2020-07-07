package in.wynk.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayuVpaVerificationResponse {

    String status;
    String vpa;
    int isVPAValid;
    String payerAccountName;
    boolean isValid;
}
