package in.wynk.payment.dto.response.addtobill;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class AddToBillEligibilityAndPricingResponse  {
    private  EligibilityResponseBody body;
    @JsonIgnore
    private  EligibilityErrors errors;
    private  boolean success;



    @Getter
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EligibilityErrors {
        private  String code;
        private  String field;
        private  String message;

    }

}
