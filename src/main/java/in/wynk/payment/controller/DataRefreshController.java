package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.common.dto.EmptyResponse;
import in.wynk.payment.service.DataRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Nishesh Pandey
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("wynk/s2s/v1/refresh")
public class DataRefreshController {
    private final DataRefreshService dataRefreshService;

    @GetMapping("/merchant/data/{txnId}/{partner}")
    @AnalyseTransaction(name = "merchantDataRefresh")
    public EmptyResponse startPaymentRenew (@PathVariable String txnId, @PathVariable String partner) {
        dataRefreshService.refreshMerchantTableData(txnId, partner);
        return EmptyResponse.response();
    }
}
