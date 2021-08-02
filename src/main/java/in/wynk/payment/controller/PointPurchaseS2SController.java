package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.SessionRequest;
import in.wynk.common.dto.SessionResponse;
import in.wynk.payment.service.IPointPurchaseSessionService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/s2s/v1/point")
public class PointPurchaseS2SController {

    private final IPointPurchaseSessionService sessionService;

    @ApiOperation("Provides session Id and the webview URL for point purchase")
    @PostMapping("/purchase")
    @AnalyseTransaction(name = "pointPurchase")
    public SessionResponse initPointPurchase(@RequestBody SessionRequest request) {
        AnalyticService.update(request);
        SessionResponse response = sessionService.initSession(request);
        AnalyticService.update(response);
        return response;
    }


}
