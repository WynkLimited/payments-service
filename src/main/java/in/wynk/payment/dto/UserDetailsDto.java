package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDetailsDto {
    private String msisdn;
    private String subscriberId;
    private String dslId;
    private String countryCode;
    private String si;
    private BillingSiDetail billingSiDetail;

    @Builder
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BillingSiDetail {
        private String billingSi;
        private String lob;
    }
}
