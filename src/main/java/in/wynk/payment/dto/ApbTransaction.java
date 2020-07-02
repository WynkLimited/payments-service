package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.payment.enums.Apb.ApbStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
@RequiredArgsConstructor
public class ApbTransaction {
    private ApbStatus status;
    @JsonProperty("txnid")
    private String txnId;
    private String txnDate;
    private String txnAmount;
}
