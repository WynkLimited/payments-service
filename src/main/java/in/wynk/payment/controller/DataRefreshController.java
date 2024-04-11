package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.common.dto.EmptyResponse;
import in.wynk.payment.service.IDataRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

/**
 * @author Nishesh Pandey
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("wynk/s2s/v1/refresh")
public class DataRefreshController {
    private final IDataRefreshService dataRefreshService;
    @Value("${spring.application.name}")
    private String applicationAlias;

    @GetMapping("/merchant/data/{txnId}/{partner}")
    @AnalyseTransaction(name = "merchantDataRefresh")
    public EmptyResponse refreshMerchantTableBySyncingStatus (@PathVariable String txnId, @PathVariable String partner) {
        dataRefreshService.refreshMerchantTableData(txnId, partner);
        return EmptyResponse.response();
    }

    @PostMapping("/merchant/data/{partner}")
    @AnalyseTransaction(name = "merchantDataRefreshCallBack")
    public EmptyResponse refreshMerchantTableByCallBack (@RequestHeader HttpHeaders headers, @PathVariable String partner, @RequestBody String payload) {
        dataRefreshService.handleCallback(partner, applicationAlias, headers, payload);
        return EmptyResponse.response();
    }

    @PostMapping("/transaction/{txnId}/recon")
    @AnalyseTransaction(name = "merchantDataRefreshCallBack")
    public EmptyResponse refresh (@RequestHeader HttpHeaders headers, @PathVariable String txnId) {
        dataRefreshService.handleCallback(applicationAlias, headers, txnId);
        return EmptyResponse.response();
    }
}
