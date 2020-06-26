package in.wynk.payment.dto.response;

import in.wynk.commons.enums.Status;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
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
