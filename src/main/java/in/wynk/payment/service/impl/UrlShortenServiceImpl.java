package in.wynk.payment.service.impl;

import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.dto.UrlShortenRequest;
import in.wynk.payment.dto.UrlShortenResponse;
import in.wynk.payment.service.IUrlShortenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class UrlShortenServiceImpl implements IUrlShortenService {

    private final String branchUri;
    private final RestTemplate template;

    public UrlShortenServiceImpl(@Value("${branch.onelink.url}") String branchUri, @Qualifier("externalServiceTemplate") RestTemplate template) {
        this.branchUri = branchUri;
        this.template = template;
    }

    @Override
    public UrlShortenResponse generate(UrlShortenRequest request) {
        try {
            return template.postForObject(branchUri, request, UrlShortenResponse.class);
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        }
    }
}
