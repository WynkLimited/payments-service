package in.wynk.payment.dto.response;

import in.wynk.commons.enums.Status;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class ConsultBalanceResponse extends CustomResponse {

    private BigDecimal deficitAmount;

    public ConsultBalanceResponse(Status status, String statusMessage, String responseCode,
                                  String statusCode, String message, BigDecimal deficitAmount) {
        super(status, statusMessage, responseCode, statusCode, message);
        this.deficitAmount = deficitAmount;
    }

    @Override
    public String toString() {
        return "ConsultBalanceResponse{" +
                "deficitAmount=" + deficitAmount +
                "} " + super.toString();
    }
}
