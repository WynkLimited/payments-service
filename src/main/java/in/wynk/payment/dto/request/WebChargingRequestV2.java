package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.GeoLocation;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.common.utils.MsisdnUtils;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.ISessionDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.AppDetails;
import in.wynk.payment.dto.PageUrlDetails;
import in.wynk.payment.dto.UserDetails;
import in.wynk.payment.dto.request.charge.AbstractPaymentDetails;
import in.wynk.session.context.SessionContextHolder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

import static in.wynk.payment.core.constant.PaymentConstants.*;

@Getter
@AnalysedEntity
@AllArgsConstructor
@SuperBuilder
public class WebChargingRequestV2 extends AbstractPaymentChargingRequest {

    @Override
    public boolean isAutoRenewOpted () {
        return this.getPaymentDetails().isAutoRenew();
    }

    @Override
    @Analysed
    @JsonIgnore
    public IAppDetails getAppDetails () {
        SessionDTO session = SessionContextHolder.getBody();
        return AppDetails.builder().deviceType(session.get(DEVICE_TYPE)).deviceId(session.get(DEVICE_ID)).buildNo(session.get(BUILD_NO)).service(session.get(SERVICE)).appId(session.get(APP_ID))
                .appVersion(session.get(APP_VERSION)).os(session.get(OS)).build();
    }

    @Override
    @Analysed
    @JsonIgnore
    public GeoLocation getGeoLocation () {
        SessionDTO session = SessionContextHolder.getBody();
        GeoLocation geoLocation = session.get(GEO_LOCATION);
        return Objects.isNull(geoLocation) ? GeoLocation.builder().build() :
                GeoLocation.builder().accessCountryCode(geoLocation.getAccessCountryCode()).stateCode(geoLocation.getStateCode()).ip(geoLocation.getIp()).build();
    }

    @Override
    public ISessionDetails getSessionDetails() {
        return WebSessionDetails.builder().sessionId(SessionContextHolder.getId()).build();
    }


    @Override
    @Analysed
    @JsonIgnore
    public IUserDetails getUserDetails () {
        final SessionDTO session = SessionContextHolder.getBody();
        /*if(paymentDetails.getPaymentId().equalsIgnoreCase(ADDTOBILL)){
            return UserBillingDetail.builder().billingSiDetail(billingSiDetail).msisdn(MsisdnUtils.normalizePhoneNumber(session.get(MSISDN))).dslId(session.get(DSL_ID)).subscriberId(session.get
            (SUBSCRIBER_ID)).countryCode(session.get(COUNTRY_CODE)).si(session.get(SI)).build();
        }*/
        return UserDetails.builder().msisdn(MsisdnUtils.normalizePhoneNumber(session.get(MSISDN))).dslId(session.get(DSL_ID)).subscriberId(session.get(SUBSCRIBER_ID))
                .countryCode(session.get(COUNTRY_CODE)).build();
    }

    @Override
    @JsonIgnore
    public IChargingDetails.IPageUrlDetails getPageUrlDetails () {
        final IAppDetails appDetails = getAppDetails();
        final SessionDTO session = SessionContextHolder.getBody();
        final String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(appDetails.getService());
        final String clientPagePlaceHolder = PAYMENT_PAGE_PLACE_HOLDER_V2.replace("%c", clientAlias);
        final String successPage = session.getSessionPayload().containsKey(SUCCESS_WEB_URL) ? getWebUrl(session, SUCCESS_WEB_URL) :
                buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "success"), "${payment.success.v2.page}"), appDetails);
        final String failurePage = session.getSessionPayload().containsKey(FAILURE_WEB_URL) ? getWebUrl(session, FAILURE_WEB_URL) :
                buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "failure"), "${payment.failure.v2.page}"), appDetails);
        final String pendingPage = session.getSessionPayload().containsKey(PENDING_WEB_URL) ? getWebUrl(session, PENDING_WEB_URL) :
                buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "pending"), "${payment.pending.v2.page}"), appDetails);
        final String unknownPage = session.getSessionPayload().containsKey(UNKNOWN_WEB_URL) ? getWebUrl(session, UNKNOWN_WEB_URL) :
                buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "unknown"), "${payment.unknown.v2.page}"), appDetails);
        return PageUrlDetails.builder().successPageUrl(successPage).failurePageUrl(failurePage).pendingPageUrl(pendingPage).unknownPageUrl(unknownPage).build();
    }

    private String getWebUrl (SessionDTO session, String webUrl) {
        String paymentFlow = getPaymentFlow(getPaymentDetails());
        return StringUtils.isNotEmpty(paymentFlow) ? session.get(webUrl) + AND + PAYMENT_FLOW + EQUAL + paymentFlow : session.get(webUrl);
    }

    @Override
    @JsonIgnore
    public IChargingDetails.ICallbackDetails getCallbackDetails () {
        return () -> EmbeddedPropertyResolver.resolveEmbeddedValue("${payment.callback.web2}") + SLASH + SessionContextHolder.getId() + SLASH +
                BeanLocatorFactory.getBean(PaymentMethodCachingService.class).get(getPaymentDetails().getPaymentId()).getPaymentCode().getCode();
    }


    private String buildUrlFrom (String resolver, IAppDetails appDetails) {
        final SessionDTO session = SessionContextHolder.getBody();
        String url =
                resolver + SessionContextHolder.getId() + SLASH + appDetails.getOs() + QUESTION_MARK + SERVICE + EQUAL + appDetails.getService() + AND + APP_ID + EQUAL + appDetails.getAppId() + AND +
                        BUILD_NO + EQUAL + appDetails.getBuildNo() + ((StringUtils.isNotBlank(session.get(THEME)) ? AND + THEME + EQUAL + session.get(THEME) : "") +
                        (StringUtils.isNotBlank(session.get(VERSION)) ? AND + VERSION + EQUAL + session.get(VERSION) : "")) + AND +
                        ((BaseConstants.PLAN.equals(getProductDetails().getType())) ? PLAN_ID : ITEM_ID) + EQUAL + getProductDetails().getId();
        String paymentFlow = getPaymentFlow(getPaymentDetails());
        return StringUtils.isNotEmpty(paymentFlow) ? url + AND + PAYMENT_FLOW + EQUAL + paymentFlow : url;
    }

    @Override
    public ClientDetails getClientDetails() {
        final ClientDetailsCachingService clientCachingService = BeanLocatorFactory.getBean(ClientDetailsCachingService.class);
        return (ClientDetails) clientCachingService.getClientByAlias(SessionContextHolder.<SessionDTO>getBody().get(CLIENT));
    }

    private String getPaymentFlow (AbstractPaymentDetails paymentDetails) {
        String paymentFlow = null;
        if (paymentDetails.isMandate()) {
            paymentFlow = MANDATE;
        } else if (paymentDetails.isTrialOpted()) {
            paymentFlow = TRIAL_OPTED;
        } else if (paymentDetails.isAutoRenew()) {
            paymentFlow = AUTO_RENEW;
        }
        return paymentFlow;
    }
}