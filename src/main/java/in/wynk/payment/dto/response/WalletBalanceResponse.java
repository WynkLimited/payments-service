package in.wynk.payment.dto.response;

import in.wynk.payment.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class WalletBalanceResponse extends CustomResponse {

    private ConsultBalanceResponseHead head;

    private ConsultBalanceResponseBody body;

    public WalletBalanceResponse(Status status, String statusMessage, String responseCode,
                                 String statusCode, String message,
                                 ConsultBalanceResponseHead head, ConsultBalanceResponseBody body) {
        super(status, statusMessage, responseCode, statusCode, message);
        this.head = head;
        this.body = body;
    }
}
