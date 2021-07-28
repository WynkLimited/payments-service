package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.utils.MsisdnUtils;
import in.wynk.payment.dto.AppDetails;
import in.wynk.payment.dto.UserDetails;
import in.wynk.session.context.SessionContextHolder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static in.wynk.common.constant.BaseConstants.*;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class WalletLinkRequest extends WalletRequest {
    @Analysed
    private String encSi;

    @Override
    @JsonIgnore
    public AppDetails getAppDetails() {
        final SessionDTO session = SessionContextHolder.getBody();
        return AppDetails.builder().deviceType(session.get(DEVICE_TYPE)).deviceId(session.get(DEVICE_ID)).buildNo(session.get(BUILD_NO)).service(session.get(SERVICE)).appId(session.get(APP_ID)).appVersion(APP_VERSION).os(session.get(OS)).build();
    }

    @Override
    @JsonIgnore
    public UserDetails getUserDetails() {
        final SessionDTO session = SessionContextHolder.getBody();
        return UserDetails.builder().msisdn(MsisdnUtils.normalizePhoneNumber(session.get(MSISDN))).dslId(session.get(DSL_ID)).subscriberId(session.get(SUBSCRIBER_ID)).build();
    }
}