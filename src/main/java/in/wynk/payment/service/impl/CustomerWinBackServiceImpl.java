package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.SessionDTO;
import in.wynk.subscription.common.request.SessionRequest;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.common.utils.WynkResponseUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.logging.BaseLoggingMarkers;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.CustomerWindBackRequest;
import in.wynk.payment.dto.PlanDetails;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.service.ICustomerWinBackService;
import in.wynk.payment.service.IDummySessionGenerator;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.session.constant.SessionConstant;
import in.wynk.session.dto.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static in.wynk.common.constant.BaseConstants.*;

@Slf4j
@Service
public class CustomerWinBackServiceImpl implements ICustomerWinBackService {

    private final String payUrl;
    private final PaymentCachingService cachingService;
    private final IDummySessionGenerator sessionGenerator;

    public CustomerWinBackServiceImpl(@Value("${payment.payOption.page}") String payUrl, PaymentCachingService cachingService, IDummySessionGenerator sessionGenerator) {
        this.payUrl = payUrl;
        this.cachingService = cachingService;
        this.sessionGenerator = sessionGenerator;
    }

    @Override
    @TransactionAware(txnId = "#request.dropoutTransactionId")
    public WynkResponseEntity<Void> winBack(CustomerWindBackRequest request) {
        final Transaction droppedTransaction = TransactionContext.get();
        final IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().orElseThrow(() -> new WynkRuntimeException("unable to process your request"));
        final IAppDetails appDetails = purchaseDetails.getAppDetails();
        try {
            final IProductDetails productDetails = purchaseDetails.getProductDetails();
            final Session<String, SessionDTO> winBackSession = sessionGenerator.generate(SessionRequest.builder().uid(droppedTransaction.getUid()).msisdn(droppedTransaction.getMsisdn()).service(appDetails.getService()).appId(appDetails.getAppId()).buildNo(appDetails.getBuildNo()).appVersion(appDetails.getAppVersion()).deviceId(appDetails.getDeviceId()).deviceType(appDetails.getDeviceType()).os(appDetails.getOs()).build());
            final URIBuilder queryBuilder = new URIBuilder(payUrl);
            if (productDetails instanceof PlanDetails)
                queryBuilder.addParameter(PLAN_ID, droppedTransaction.getProductId());
            else {
                queryBuilder.addParameter(ITEM_ID, droppedTransaction.getProductId());
                queryBuilder.addParameter(POINT_PURCHASE_FLOW, Boolean.TRUE.toString());
                queryBuilder.addParameter(AMOUNT, String.valueOf(cachingService.getItem(droppedTransaction.getProductId()).getPrice()));
            }
            queryBuilder.addParameter(SERVICE, String.valueOf(appDetails.getService()));
            queryBuilder.addParameter(BUILD_NO, String.valueOf(appDetails.getBuildNo()));
            return WynkResponseUtils.redirectResponse(payUrl + winBackSession.getId().split(SessionConstant.COLON_DELIMITER)[1] + SLASH + appDetails.getOs() + QUESTION_MARK + queryBuilder.build().getQuery());
        } catch (Exception e) {
            log.error(BaseLoggingMarkers.APPLICATION_ERROR, "Unable to execute winback logic due to {}", e.getMessage(), e);
            return WynkResponseUtils.redirectResponse(buildFailureUrl(EmbeddedPropertyResolver.resolveEmbeddedValue("${payment.timeout.page}"), appDetails));
        } finally {
            if (request.getParams().containsKey(OLD_SID)) {
                AnalyticService.update(ORIGINAL_SID, (String) request.getParams().get(OLD_SID));
            }
        }
    }

    private String buildFailureUrl(String url, IAppDetails appDetails) {
        return url + UUID.randomUUID().toString() + SLASH + appDetails.getOs() + QUESTION_MARK + SERVICE + EQUAL + appDetails.getService() + AND + APP_ID + EQUAL + appDetails.getAppId() + AND + BUILD_NO + EQUAL + appDetails.getBuildNo();
    }

}
