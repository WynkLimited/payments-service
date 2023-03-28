package in.wynk.payment.dto.aps.request.option;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApsPaymentOptionRequest {
    @Builder.Default
    private String lob ="WYNK";
}
