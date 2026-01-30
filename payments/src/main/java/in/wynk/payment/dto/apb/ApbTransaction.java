package in.wynk.payment.dto.apb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
@RequiredArgsConstructor
public class ApbTransaction {
    private String status;
    @JsonProperty("txnid")
    private String txnId;
    private String txnDate;
    private String txnAmount;
}
