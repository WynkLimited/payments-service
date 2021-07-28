package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.utils.MsisdnUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class S2SWalletBalanceRequest extends WalletBalanceRequest {
    @Analysed
    private String deviceId;
    @Analysed
    private String msisdn;

    @Override
    @JsonIgnore
    public String getUid() {
        return MsisdnUtils.getUidFromMsisdn(msisdn);
    }
}
