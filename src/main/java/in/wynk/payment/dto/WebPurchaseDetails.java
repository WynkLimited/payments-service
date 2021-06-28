package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.MsisdnUtils;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;
import in.wynk.session.context.SessionContextHolder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;

import static in.wynk.common.constant.BaseConstants.*;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class WebPurchaseDetails implements IChargingDetails {

    @Analysed
    private PaymentDetails paymentDetails;
    @Analysed
    private AbstractProductDetails productDetails;

    @Override
    public IAppDetails getAppDetails() {
        SessionDTO session = SessionContextHolder.getBody();
        return AppDetails.builder().deviceType(session.get(DEVICE_TYPE)).deviceId(session.get(DEVICE_ID)).buildNo(session.get(BUILD_NO)).service(session.get(SERVICE)).appId(session.get(APP_ID)).appVersion(APP_VERSION).os(session.get(OS)).build();
    }

    @Override
    @Analysed
    public IUserDetails getUserDetails() {
        SessionDTO session = SessionContextHolder.getBody();
        return UserDetails.builder().msisdn(MsisdnUtils.normalizePhoneNumber(session.get(MSISDN))).dslId(session.get(DSL_ID)).subscriberId(session.get(SUBSCRIBER_ID)).build();
    }

    @Override
    public IPageUrlDetails getPageUrlDetails() {
        final String successPagePlaceholder = "${payment.success.page}";
        final String failurePagePlaceholder = "${payment.failure.page}";
        final String pendingPagePlaceholder = "${payment.pending.page}";
        final String unknownPagePlaceholder = "${payment.unknown.page}";
        final IAppDetails appDetails = getAppDetails();
        final ConfigurableBeanFactory beanFactory = BeanLocatorFactory.getBean(ConfigurableBeanFactory.class);
        return PageUrlDetails.builder().successPageUrl(buildUrlFrom(beanFactory.resolveEmbeddedValue(successPagePlaceholder), appDetails)).failurePageUrl(buildUrlFrom(beanFactory.resolveEmbeddedValue(failurePagePlaceholder), appDetails)).pendingPageUrl(buildUrlFrom(beanFactory.resolveEmbeddedValue(pendingPagePlaceholder), appDetails)).unknownPageUrl(buildUrlFrom(beanFactory.resolveEmbeddedValue(unknownPagePlaceholder), appDetails)).build();
    }

    private String buildUrlFrom(String url, IAppDetails appDetails) {
        return url + SessionContextHolder.getId() + SLASH + appDetails.getOs() + QUESTION_MARK + SERVICE + EQUAL + appDetails.getService() + AND + APP_ID + EQUAL + appDetails.getAppId() + AND + BUILD_NO + EQUAL + appDetails.getBuildNo();
    }

}
