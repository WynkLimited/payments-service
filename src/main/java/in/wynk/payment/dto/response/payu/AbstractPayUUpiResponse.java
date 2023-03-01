package in.wynk.payment.dto.response.payu;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AbstractPayUUpiResponse<T extends AbstractPayUUpiResponse.Result> {

    private T result;
    private Metadata metadata;

    @Getter
    @NoArgsConstructor
    private class Metadata {
        private String txnId;
        private String message;
        private String txnStatus;
        private String statusCode;
        private String referenceId;
        private String unmappedStatus;
    }

    @Getter
    @NoArgsConstructor
    public abstract class Result {
        private String acsTemplate;
    }

}
