package in.wynk.payment.service.impl;

import com.datastax.driver.core.utils.UUIDs;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.auth.dao.entity.Client;
import in.wynk.auth.utils.EncryptUtils;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.dto.IPresentation;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.SessionResponse;
import in.wynk.common.dto.SessionResponse.SessionData;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.country.core.service.CountryCurrencyDetailsCachingService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.identity.client.utils.IdentityUtils;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.BestValuePlanPurchaseRequest;
import in.wynk.payment.dto.BestValuePlanResponse;
import in.wynk.payment.dto.PointDetails;
import in.wynk.payment.dto.PurchaseRequest;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.session.constant.SessionConstant;
import in.wynk.session.service.ISessionManager;
import in.wynk.subscription.common.adapter.SessionDTOAdapter;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.request.SessionRequest;
import in.wynk.wynkservice.api.utils.WynkServiceUtils;
import in.wynk.wynkservice.core.dao.entity.App;
import in.wynk.wynkservice.core.dao.entity.Os;
import in.wynk.wynkservice.core.dao.entity.WynkService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static in.wynk.common.constant.BaseConstants.AIRTEL_TV;
import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.SKU_ID;
import static in.wynk.payment.core.constant.PaymentConstants.*;

@Component
@RequiredArgsConstructor
public class RedirectToPaymentPagePresentation implements
        IPresentation<WynkResponseEntity<SessionResponse.SessionData>, Pair<String, BestValuePlanResponse>> {

    private final ISessionManager<String, SessionDTO> sessionManager;
    private final ClientDetailsCachingService clientDetailsCachingService;
    private final CountryCurrencyDetailsCachingService countryCurrencyDetailsCachingService;

    @Value("${service.subscription.webView.root}")
    private String webViewDomain;

    @Value("${service.subscription.api.manage.url}")
    private String manageUrl;

    @Value("${service.subscription.api.purchase.url}")
    private String purchaseUrl;

    @Value("${session.duration:15}")
    private Integer duration;

    @Override
    public WynkResponseEntity<SessionData> transform(final Pair<String, BestValuePlanResponse> pair) throws URISyntaxException {
        final String id = pair.getFirst();
        final BestValuePlanResponse bestValuePlanResponse = pair.getSecond();
        final String bestValuePlanId = bestValuePlanResponse.getPlanId();
        final BestValuePlanPurchaseRequest request = bestValuePlanResponse.getBestValuePlanPurchaseRequest();
        final String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        final Client client = BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(clientId);
        final String PAYMENT_OPTION_URL = EmbeddedPropertyResolver.resolveEmbeddedValue(PaymentConstants.PAYMENT_PAGE_PLACE_HOLDER.replace("%c",
                                                                                                                                           client.getAlias())
                                                                                                                                  .replace("%p",
                                                                                                                                           "payOption"),
                                                                                        "${payment.payOption.page}");
        ;
        try {
            if (StringUtils.isEmpty(bestValuePlanId)) {
                String failurePageUrl = request.getPageUrlDetails().getFailurePageUrl();
                if (StringUtils.isEmpty(failurePageUrl)) {
                    final SessionResponse planPageUrl = getPlanPageUrl(request.toSession());
                    failurePageUrl = planPageUrl.getData() != null ? planPageUrl.getData().getRedirectUrl() : "";
                }
                SessionResponse.SessionData response = SessionResponse.SessionData.builder().redirectUrl(failurePageUrl).sid(id).build();
                return WynkResponseEntity.<SessionResponse.SessionData>builder().data(response).build();
            }
            final URIBuilder queryBuilder = new URIBuilder(PAYMENT_OPTION_URL);
            final PaymentCachingService cache = BeanLocatorFactory.getBean(PaymentCachingService.class);
            queryBuilder.addParameter(PLAN_ID, bestValuePlanId);
            queryBuilder.addParameter(UID,
                                      IdentityUtils.getUidFromUserName(request.getUserDetails().getMsisdn(),
                                                                       request.getAppDetails().getService()));
            queryBuilder.addParameter(APP_ID, String.valueOf(request.getAppDetails().getAppId()));
            queryBuilder.addParameter(SERVICE, String.valueOf(request.getAppDetails().getService()));
            queryBuilder.addParameter(BUILD_NO, String.valueOf(request.getAppDetails().getBuildNo()));
            queryBuilder.addParameter(DEVICE_ID_SHORT, String.valueOf(request.getAppDetails().getDeviceId()));
            queryBuilder.addParameter(BUILD_NO, String.valueOf(request.getAppDetails().getBuildNo()));
            if (Objects.nonNull(request.getMiscellaneousDetails())) {
                queryBuilder.addParameter(INGRESS_INTENT, String.valueOf(request.getMiscellaneousDetails().getIngressIntent()));
                if (request.getMiscellaneousDetails().isMandate()) {
                    queryBuilder.addParameter(PAYMENT_FLOW, PAYMENT_FLOW_MANDATE);
                } else if (request.getMiscellaneousDetails().isTrialOpted()) {
                    queryBuilder.addParameter(PAYMENT_FLOW, PAYMENT_FLOW_TRIAL_OPTED);
                } else if (request.getMiscellaneousDetails().isAutoRenew() && !request.getMiscellaneousDetails().isTrialOpted()) {
                    queryBuilder.addParameter(PAYMENT_FLOW, PAYMENT_FLOW_AUTO_RENEW);
                }
            }
            PlanDTO planDto = cache.getPlan(bestValuePlanResponse.getPlanId());
            if (Objects.nonNull(planDto.getSku()) && Objects.nonNull(planDto.getSku().get("google_iap"))) {
                queryBuilder.addParameter(PaymentConstants.SKU_ID, planDto.getSku().get("google_iap"));
            }
            String builder = PAYMENT_OPTION_URL + id + SLASH + request.getOs() + QUESTION_MARK + queryBuilder.build().getQuery();
            SessionResponse.SessionData response = SessionResponse.SessionData.builder().redirectUrl(builder).sid(id).build();
            return WynkResponseEntity.<SessionResponse.SessionData>builder().data(response).build();
        } catch (URISyntaxException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY997, e);
        }
    }

    public SessionResponse getPlanPageUrl(SessionRequest request) {
        if (request.getService().equalsIgnoreCase(MUSIC) && request.getAppId().equalsIgnoreCase(WEB) && (request.getOs().equalsIgnoreCase(WEBOS)
                                                                                                         || request.getOs()
                                                                                                                   .equalsIgnoreCase(M_WEBOS))) {
            return getSession(request, purchaseUrl);
        }
        return getSession(request, manageUrl);
    }

    private SessionResponse getSession(SessionRequest sessionRequest, String url) {
        String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        ClientDetails clientDetails = (ClientDetails) clientDetailsCachingService.getClientById(clientId);
        final String clientSpecificDomainUrl = EmbeddedPropertyResolver.resolveEmbeddedValue(PaymentConstants.SUBSCRIPTION_PURCHASE_OR_MANAGE_CLIENT_PLACE_HOLDER);
        ;
        boolean isV2 = Boolean.FALSE;
        try {
            AnalyticService.update(CLIENT, clientDetails.getAlias());
            final SessionDTO sessionDTO = SessionDTOAdapter.generateSessionDTO(sessionRequest);
            sessionDTO.put(CLIENT, clientDetails.getAlias());
            String countryCode = sessionRequest.getCountryCode();
            if (StringUtils.isEmpty(countryCode)) {
                countryCode = WynkServiceUtils.fromServiceId(sessionRequest.getService()).getDefaultCountryCode();
                sessionDTO.put(COUNTRY_CODE, countryCurrencyDetailsCachingService.get(countryCode).getCountryCode());
            } else {
                sessionDTO.put(COUNTRY_CODE, countryCode);
            }
            final String id = UUIDs.timeBased().toString();
            sessionDTO.put(GEO_LOCATION, sessionRequest.getGeoLocation());
            sessionManager.init(SessionConstant.SESSION_KEY + SessionConstant.COLON_DELIMITER + id, sessionDTO, duration, TimeUnit.MINUTES);
            AnalyticService.update(SESSION_ID, id);
            final WynkService service = WynkServiceUtils.fromServiceId(sessionRequest.getService());
            final App supportedApp = WynkServiceUtils.getServiceSupportedApp(sessionRequest.getAppId(), service);
            final Os supportedOs = WynkServiceUtils.getAppSupportedOs(sessionRequest.getOs(), supportedApp);
            final String uri = new StringBuilder(sessionRequest.getService()).append(url).append(id).append(SLASH)
                                                                             .append(supportedOs.getId().toLowerCase()).toString();
            final String domainUrl = webViewDomain;
            final StringBuilder host = new StringBuilder(domainUrl)
                .append(uri)
                .append(QUESTION_MARK)
                .append(APP_ID)
                .append(EQUAL)
                .append(supportedApp.getId())
                .append(AND)
                .append(UID)
                .append(EQUAL)
                .append(sessionRequest.getUid())
                .append(AND)
                .append(DEVICE_ID_SHORT)
                .append(EQUAL)
                .append(sessionRequest.getDeviceId());
            if (Objects.nonNull(supportedApp) && (supportedOs.getId().equalsIgnoreCase(IOS) || supportedOs.getId().equalsIgnoreCase(ANDROID))) {
                host.append(AND).append(BUILD_NO).append(EQUAL).append(sessionRequest.getBuildNo());
                if (sessionRequest.getService().equals(AIRTEL_TV) && sessionRequest.getBuildNo() <= 117) {
                    long tms = System.currentTimeMillis();
                    StringBuilder data = new StringBuilder(SLASH).append(HASH).append(SLASH).append(uri).append(sessionRequest.getUid()).append(tms);
                    String clientSecret = Objects.nonNull(clientDetails) ? clientDetails.getClientSecret() : "";
                    String signature = EncryptUtils.calculateRFC2104HMAC(data.toString(), clientSecret);
                    host.append(AND).append(HASH_STR).append(EQUAL).append(signature).append(AND).append(TMS).append(EQUAL).append(tms);
                }
            }
            if (Objects.nonNull(sessionRequest.getTheme())) {
                host.append(AND).append(THEME).append(EQUAL).append(sessionRequest.getTheme());
            }
            if (isV2) {
                host.append(AND).append(VERSION).append(EQUAL).append(V2);
            }
            if (StringUtils.isNotEmpty(sessionRequest.getIntent())) {
                host.append(AND).append(INGRESS_INTENT).append(EQUAL).append(sessionRequest.getIntent());
            }
            SessionResponse.SessionData response = SessionResponse.SessionData.builder().redirectUrl(host.toString()).sid(id).build();
            return SessionResponse.builder().data(response).build();
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY800, e);
        }
    }


}
