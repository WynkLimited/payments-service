package in.wynk.payment.dto.aps.verify;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.aps.response.verify.ApsBinVerificationResponseData;
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
public class ApsCardVerificationResponseWrapper<T extends ApsBinVerificationResponseData> extends ApiResponse {
    private T data;
}
