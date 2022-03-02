package in.wynk.payment.dto.aps.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApsApiResponseWrapper<T> {

    private T data;
    private String requestId;
    private boolean result;
    private String status;
    private String errorCode;
    private String errorMessage;
    private String responseCode;

}
