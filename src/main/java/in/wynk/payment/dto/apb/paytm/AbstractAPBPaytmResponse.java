package in.wynk.payment.dto.apb.paytm;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@SuperBuilder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public abstract class AbstractAPBPaytmResponse {
    private String errorCode;
    private String errorMessage;
    private boolean result;
}
