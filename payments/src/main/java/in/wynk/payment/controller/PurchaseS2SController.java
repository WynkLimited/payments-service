package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.IPresentation;

import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.BestValuePlanPurchaseRequest;
import in.wynk.payment.dto.BestValuePlanResponse;
import in.wynk.payment.event.PurchaseRequestKafkaMessage;
import in.wynk.payment.presentation.PurchaseSessionPresentation;
import in.wynk.payment.publisher.PurchaseEventPublisher;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.stream.service.IDataPlatformKafkaService;
import in.wynk.subscription.common.request.SessionRequest;
import in.wynk.common.dto.SessionResponse;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.dto.PurchaseRequest;
import in.wynk.payment.service.IPurchaseSessionService;
import in.wynk.payment.utils.LoadClientUtils;
import in.wynk.exception.WynkRuntimeException;
import io.swagger.annotations.ApiOperation;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = {"/wynk/s2s", "/iq/s2s"})
public class PurchaseS2SController {

    private final IPurchaseSessionService sessionService;

    private final ISubscriptionServiceManager iSubscriptionServiceManager;
    private final PurchaseSessionPresentation purchaseSessionPresentation;
    private final PurchaseEventPublisher purchaseEventPublisher;

    @PostMapping("/v1/point/purchase")
    @AnalyseTransaction(name = "pointPurchase")
    @ApiOperation("Provides session Id and the webview URL for point purchase")
    public SessionResponse initPointPurchase(@Valid @RequestBody SessionRequest request) {
        LoadClientUtils.loadClient(true);
        AnalyticService.update(request);
        SessionResponse response = sessionService.initSession(request);
        AnalyticService.update(response);
        return response;
    }

    @SneakyThrows
    @PostMapping( value = {"/v2/plan/purchase", "/v2/point/purchase"})
    @AnalyseTransaction(name = "purchaseRequest")
    @ApiOperation("Provides session Id and the webview URL for plan/point purchase")
    @PreAuthorize(PaymentConstants.PAYMENT_CLIENT_AUTHORIZATION + " && hasAuthority(\"PURCHASE_INIT\")")
    public WynkResponseEntity<SessionResponse.SessionData> init(@Valid @RequestBody PurchaseRequest request) {
        try {
            LoadClientUtils.loadClient(true);
            AnalyticService.update(request);
            final String sid = sessionService.init(request);
            WynkResponseEntity<SessionResponse.SessionData> response = purchaseSessionPresentation.transform(Pair.of(sid, request));
            AnalyticService.update(response.getBody());
            purchaseEventPublisher.publishAsync(request, response);
            return response;
        } catch (WynkRuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new WynkRuntimeException(PaymentErrorType.PAY929, ex);
        }
    }
    @SneakyThrows
    @PostMapping(value = {"/v3/plan/purchase"})
    @AnalyseTransaction(name = "purchaseRequestV3")
    @ApiOperation("Provides session Id and the webview URL for directToPayment page plan purchase")
    @PreAuthorize(PaymentConstants.PAYMENT_CLIENT_AUTHORIZATION + " && hasAuthority(\"PURCHASE_INIT\")")
    public WynkResponseEntity<SessionResponse.SessionData> planPurchase(@Valid @RequestBody BestValuePlanPurchaseRequest request,
                                                                               @RequestParam Map<String, String> additionalParam) {
        LoadClientUtils.loadClient(true);
        final String sid = sessionService.init(request , additionalParam);
        final BestValuePlanResponse bestValuePlan = iSubscriptionServiceManager.getBestValuePlan(request, additionalParam);
        final WynkResponseEntity<SessionResponse.SessionData> response = BeanLocatorFactory.getBean(new ParameterizedTypeReference<IPresentation<WynkResponseEntity<SessionResponse.SessionData>,  Pair<String, BestValuePlanResponse>>>() {
        }).transform( Pair.of(sid, bestValuePlan));
        AnalyticService.update(response.getBody());
        return response;
    }

}