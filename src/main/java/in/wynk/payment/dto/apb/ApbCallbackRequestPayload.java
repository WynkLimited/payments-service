package in.wynk.payment.dto.apb;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.payment.dto.request.CallbackRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class ApbCallbackRequestPayload extends CallbackRequest {

    @JsonProperty("MID")
    private String mid;

    @JsonProperty("MSG")
    private String msg;

    @JsonProperty("CODE")
    private String code;

    @JsonProperty("HASH")
    private String hash;

    @JsonProperty("STATUS")
    private String status;

    @JsonProperty("TXN_REF_NO")
    private String transactionId;

    @JsonProperty("TRAN_AMT")
    private String transactionAmount;

    @JsonProperty("TRAN_DATE")
    private String transactionDate;

    public ApbStatus getStatus() {
        return ApbStatus.valueOf(status);
    }

}