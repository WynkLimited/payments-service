package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

import static in.wynk.common.constant.BaseConstants.ADDTOBILL;

@SuperBuilder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserBillingDetail extends UserDetails {

    private BillingSiDetail billingSiDetail;

    @Builder
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BillingSiDetail implements Serializable {
        private String billingSi;
        private String lob;
    }

    @Override
    public String getType() {
        return ADDTOBILL;
    }
}
