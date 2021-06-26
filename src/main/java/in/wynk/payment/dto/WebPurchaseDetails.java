package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.utils.MsisdnUtils;
import in.wynk.session.context.SessionContextHolder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static in.wynk.common.constant.BaseConstants.*;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class WebPurchaseDetails implements IPurchaseDetails {

    @Analysed
    private PaymentDetails paymentDetails;
    @Analysed
    private IProductDetails productDetails;

    @Override
    public IAppDetails getAppDetails() {
        SessionDTO session = SessionContextHolder.getBody();
        return AppDetails.builder().deviceType(session.get(DEVICE_TYPE)).deviceId(session.get(DEVICE_ID)).buildNo(session.get(BUILD_NO)).service(session.get(SERVICE)).appId(session.get(APP_ID)).os(session.get(OS)).build();
    }

    @Override
    @Analysed
    public IUserDetails getUserDetails() {
        SessionDTO session = SessionContextHolder.getBody();
        return UserDetails.builder().msisdn(MsisdnUtils.normalizePhoneNumber(session.get(MSISDN))).subscriberId(session.get(SUBSCRIBER_ID)).build();
    }

}
