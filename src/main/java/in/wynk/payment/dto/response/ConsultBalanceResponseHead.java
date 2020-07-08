package in.wynk.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ConsultBalanceResponseHead {

    private String responseTimestamp;

    private String version;

    private String clientId;

    private String signature;

}
