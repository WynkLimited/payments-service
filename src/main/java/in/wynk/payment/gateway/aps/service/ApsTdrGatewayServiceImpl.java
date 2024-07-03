package in.wynk.payment.gateway.aps.service;

import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.BaseTDRResponse;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.response.tdr.ApsTdrResponse;
import in.wynk.payment.service.IMerchantTDRService;
import in.wynk.payment.service.IMerchantTransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_TDR_ERROR;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsTdrGatewayServiceImpl implements IMerchantTDRService {
    private final String TDR_ENDPOINT;
    private final ApsCommonGatewayService common;
    private final IMerchantTransactionService merchantTransactionService;
    private final RestTemplate httpTemplate;

    public ApsTdrGatewayServiceImpl (String tdrEndpoint, @Qualifier("apsHttpTemplate") RestTemplate httpTemplate, ApsCommonGatewayService common,
                                     IMerchantTransactionService merchantTransactionService) {
        this.common = common;
        this.httpTemplate = httpTemplate;
        this.TDR_ENDPOINT = tdrEndpoint;
        this.merchantTransactionService = merchantTransactionService;

    }

    @Override
    public BaseTDRResponse getTDR (String transactionId) {
        try {
            final Transaction transaction = TransactionContext.get();
            final MerchantTransaction merchantTransaction = merchantTransactionService.getMerchantTransaction(transactionId);
            final String pgId = merchantTransaction.getExternalTransactionId();
            final URI uri = httpTemplate.getUriTemplateHandler().expand(TDR_ENDPOINT, pgId);
            ApsTdrResponse apsTdrResponse = common.exchange(transaction.getClientAlias(), uri.toString(), HttpMethod.GET, transaction.getMsisdn(), null, ApsTdrResponse.class);
            return BaseTDRResponse.from(apsTdrResponse.getTdrAmount());
        } catch (Exception e) {
            log.error(APS_TDR_ERROR, e.getMessage());
        }
        return BaseTDRResponse.from(-2);
    }
}
