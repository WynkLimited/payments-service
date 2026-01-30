package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.dto.GeoLocation;
import in.wynk.common.dto.IGeoLocation;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.common.utils.MsisdnUtils;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.ISessionDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;
import in.wynk.payment.dto.request.charge.AbstractPaymentDetails;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.session.context.SessionContextHolder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import javax.validation.Valid;

import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.*;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class WebPurchaseDetails implements IChargingDetails {

    @Valid
    @Analysed
    private AbstractPaymentDetails paymentDetails;

    @Valid
    @Analysed
    private AbstractProductDetails productDetails;

    @Valid
    @Analysed
    private UserBillingDetail.BillingSiDetail billingSiDetail;

    @Override
    @Analysed
    @JsonIgnore
    public IAppDetails getAppDetails() {
        SessionDTO session = SessionContextHolder.getBody();
        return AppDetails.builder().deviceType(session.get(DEVICE_TYPE)).deviceId(session.get(DEVICE_ID)).buildNo(session.get(BUILD_NO)).service(session.get(SERVICE)).appId(session.get(APP_ID)).appVersion(session.get(APP_VERSION)).os(session.get(OS)).build();
    }

    @Override
    @Analysed
    @JsonIgnore
    public IUserDetails getUserDetails() {
        final SessionDTO session = SessionContextHolder.getBody();
        if(paymentDetails.getPaymentId().equalsIgnoreCase(ADDTOBILL)){
            return UserBillingDetail.builder().billingSiDetail(billingSiDetail).msisdn(MsisdnUtils.normalizePhoneNumber(session.get(MSISDN))).dslId(session.get(DSL_ID)).subscriberId(session.get(SUBSCRIBER_ID)).countryCode(session.get(COUNTRY_CODE)).si(session.get(SI)).build();
        }
        return UserDetails.builder().msisdn(MsisdnUtils.normalizePhoneNumber(session.get(MSISDN))).dslId(session.get(DSL_ID)).subscriberId(session.get(SUBSCRIBER_ID)).countryCode(session.get(COUNTRY_CODE)).build();
    }

    @Override
    public IGeoLocation getGeoLocation() {
        SessionDTO session = SessionContextHolder.getBody();
        GeoLocation geoLocation = session.get(GEO_LOCATION);
        return Objects.isNull(geoLocation) ? GeoLocation.builder().build() :
                GeoLocation.builder().accessCountryCode(geoLocation.getAccessCountryCode()).stateCode(geoLocation.getStateCode()).ip(geoLocation.getIp()).build();
    }

    @Override
    public ISessionDetails getSessionDetails () {
        return null;
    }

    @Override
    @JsonIgnore
    public IPageUrlDetails getPageUrlDetails() {
        final IAppDetails appDetails = getAppDetails();
        final SessionDTO session = SessionContextHolder.getBody();
        final String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(appDetails.getService());
        final String clientPagePlaceHolder = PaymentConstants.PAYMENT_PAGE_PLACE_HOLDER.replace("%c", clientAlias);
        final String successPage = session.getSessionPayload().containsKey(SUCCESS_WEB_URL) ? session.get(SUCCESS_WEB_URL) : buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "success"), "${payment.success.page}"), appDetails);
        final String failurePage = session.getSessionPayload().containsKey(FAILURE_WEB_URL) ? session.get(FAILURE_WEB_URL) : buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "failure"), "${payment.failure.page}"), appDetails);
        final String pendingPage = session.getSessionPayload().containsKey(PENDING_WEB_URL) ? session.get(PENDING_WEB_URL) : buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "pending"), "${payment.pending.page}"), appDetails);
        final String unknownPage = session.getSessionPayload().containsKey(UNKNOWN_WEB_URL) ? session.get(UNKNOWN_WEB_URL) : buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "unknown"), "${payment.unknown.page}"), appDetails);
        return PageUrlDetails.builder().successPageUrl(successPage).failurePageUrl(failurePage).pendingPageUrl(pendingPage).unknownPageUrl(unknownPage).build();
    }

    @Override
    @JsonIgnore
    public ICallbackDetails getCallbackDetails() {
        return () -> EmbeddedPropertyResolver.resolveEmbeddedValue("${payment.callback.web}") + SLASH + SessionContextHolder.getId() + SLASH + BeanLocatorFactory.getBean(PaymentMethodCachingService.class).get(getPaymentDetails().getPaymentId()).getPaymentCode().getCode();
    }


    private String buildUrlFrom(String url, IAppDetails appDetails) {
        final SessionDTO session = SessionContextHolder.getBody();
        return url + SessionContextHolder.getId() + SLASH + appDetails.getOs() + QUESTION_MARK + SERVICE + EQUAL + appDetails.getService() + AND + APP_ID + EQUAL + appDetails.getAppId() + AND + BUILD_NO + EQUAL + appDetails.getBuildNo() + ((StringUtils.isNotBlank(session.get(THEME)) ? AND + THEME + EQUAL + session.get(THEME) : "") + (StringUtils.isNotBlank(session.get(VERSION))? AND + VERSION + EQUAL + session.get(VERSION): ""));
    }

}