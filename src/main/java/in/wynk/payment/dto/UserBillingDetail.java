package in.wynk.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static in.wynk.common.constant.BaseConstants.ADDTOBILL;

@SuperBuilder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UserBillingDetail extends UserDetails {

    private BillingSiDetail billingSiDetail;

    @Builder
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BillingSiDetail {
        private String billingSi;
        private String lob;
    }

    @Override
    public String getType() {
        return ADDTOBILL;
    }
}
