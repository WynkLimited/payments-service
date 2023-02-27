package in.wynk.payment.dto.aps.verify;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.payment.dto.aps.response.verify.ApsVpaVerificationData;
import in.wynk.payment.dto.common.ApiResponse;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApsVpaVerificationResponseWrapper<T extends ApsVpaVerificationData> extends ApiResponse {
    private T data;
    private String vpa;
    private String payeeAccountName;
    @JsonProperty("isAutoPayHandleValid")
    private boolean autoPayHandleValid;
    private String errorMessage;
    private boolean vpaValid;
}
