package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.IPresentation;
import in.wynk.common.dto.SessionRequest;
import in.wynk.common.dto.SessionResponse;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.dto.PurchaseRequest;
import in.wynk.payment.service.IPurchaseSessionService;
import in.wynk.payment.utils.LoadClientUtils;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/(wynk|iq)/s2s")
public class PurchaseS2SController {

    private final IPurchaseSessionService sessionService;

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

    @PostMapping("/v2/(point|plan)/purchase")
    @AnalyseTransaction(name = "purchaseRequest")
    @ApiOperation("Provides session Id and the webview URL for plan/point purchase")
    @PreAuthorize(PaymentConstants.PAYMENT_CLIENT_AUTHORIZATION + " && hasAuthority(\"PURCHASE_INIT\")")
    public WynkResponseEntity<SessionResponse.SessionData> init(@Valid @RequestBody PurchaseRequest request) {
        LoadClientUtils.loadClient(true);
        AnalyticService.update(request);
        final String sid = sessionService.init(request);
        final WynkResponseEntity<SessionResponse.SessionData> response = BeanLocatorFactory.getBean(new ParameterizedTypeReference<IPresentation<WynkResponseEntity<SessionResponse.SessionData>, Pair<String, PurchaseRequest>>>() {
        }).transform(Pair.of(sid, request));
        AnalyticService.update(response.getBody());
        return response;
    }

}