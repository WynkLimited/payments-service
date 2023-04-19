package in.wynk.payment.dto.aps.response.option.savedOptions;

import in.wynk.payment.constant.NetBankingConstants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

import static in.wynk.payment.dto.aps.common.ApsConstant.APS;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@NoArgsConstructor
public class NetBankingSavedPayOptions extends AbstractSavedPayOptions implements Serializable {
    @Override
    public String getId() {
        return APS.concat("_").concat(NetBankingConstants.NET_BANKING);
    }
}
