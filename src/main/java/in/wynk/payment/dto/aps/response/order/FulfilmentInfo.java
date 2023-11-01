package in.wynk.payment.dto.aps.response.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.constant.PostingStatus;
import in.wynk.payment.dto.aps.common.LOB;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class FulfilmentInfo {
    private String sku;
    private String description;
    private LOB lob;
    private double amount;
    private Meta meta;
    private String fulfilmentId;
    private PostingStatus status;
    private Integer postingTrialsAttempted;
    private Integer refundTrialsAttempted;
    private long createdAt;
    private long updatedAt;

    //fields for S2S Callback
    private String fulfilmentSystemId;
    private String fulfilmentSystemIdentifier;



    @Getter
    @SuperBuilder
    @ToString
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    public static class Meta {
        private String transactionId;
        private String serviceInstance;
        private String accountNumber;
        private Integer circleId;
    }
}
