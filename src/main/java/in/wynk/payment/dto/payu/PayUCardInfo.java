package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Data
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayUCardInfo {

    private String issuingBank;

    private String isDomestic;

    private String cardType;

    private String cardCategory;

    private boolean isValid;

}
