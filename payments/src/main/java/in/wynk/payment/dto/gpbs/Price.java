package in.wynk.payment.dto.gpbs;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Price {
    private String priceMicros;
    @Builder.Default
    private String currency = "INR";
}
