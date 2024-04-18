package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.context.ClientContext;
import in.wynk.client.core.constant.ClientErrorType;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.utils.EncodingUtil;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.UrlShortenRequest;
import in.wynk.payment.dto.UrlShortenResponse;
import in.wynk.payment.service.IQuickPayLinkGenerator;
import in.wynk.payment.service.IUrlShortenService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.wynkservice.api.utils.WynkServiceUtils;
import in.wynk.wynkservice.core.dao.entity.WynkService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static in.wynk.common.constant.BaseConstants.*;

@Service
public class QuickPayLinkGeneratorImpl implements IQuickPayLinkGenerator {

    private final String winBackUrl;
    private final IUrlShortenService urlShortenService;
    private final PaymentCachingService cachingService;

    public QuickPayLinkGeneratorImpl(@Value("${service.payment.api.endpoint.winBack}") String winBackUrl, IUrlShortenService urlShortenService, PaymentCachingService cachingService) {
        this.winBackUrl = winBackUrl;
        this.cachingService = cachingService;
        this.urlShortenService = urlShortenService;
    }

    @Override
    @TransactionAware(txnId = "#tid", lock = false)
    public String generate(String tid) {
        final Transaction transaction = TransactionContext.get();
        final IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().orElseThrow(() -> new WynkRuntimeException("Purchase details is not found"));
        return generate(tid, transaction.getClientAlias(), purchaseDetails.getAppDetails(), purchaseDetails.getProductDetails());
    }

    @Override
    public String generate(String tid, String clientAlias, IAppDetails appDetails, IProductDetails productDetails) {
        return generateInternal(tid, clientAlias, Optional.empty(), appDetails, productDetails);
    }

    @Override
    public String generate(String tid, String clientAlias, String oldSid, IAppDetails appDetails, IProductDetails productDetails) {
        return generateInternal(tid, clientAlias, Optional.ofNullable(oldSid), appDetails, productDetails);
    }

    @ClientAware(clientAlias = "#clientAlias")
    private String generateInternal(String tid, String clientAlias, Optional<String> oldSidOption, IAppDetails appDetails, IProductDetails productDetails) {
        try {
            final Client clientDetails = ClientContext.getClient().orElseThrow(() -> new WynkRuntimeException(ClientErrorType.CLIENT001));
            final String service = productDetails.getType().equalsIgnoreCase(PLAN) ? cachingService.getPlan(productDetails.getId()).getService() : cachingService.getItem(productDetails.getId()).getService();
            final WynkService wynkService = WynkServiceUtils.fromServiceId(service);
            final long ttl = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3);
            final String payUrl = buildUrlFromWithOption(winBackUrl + tid + QUESTION_MARK + CLIENT_IDENTITY + EQUAL + Base64.getEncoder().encodeToString(clientDetails.getAlias().getBytes(StandardCharsets.UTF_8)) + AND + TTL + EQUAL + ttl + AND + TOKEN_ID + EQUAL + URLEncoder.encode(Objects.requireNonNull(EncryptionUtils.generateAppToken(tid + COLON + ttl, clientDetails.getClientSecret())), StandardCharsets.UTF_8.toString()), appDetails, oldSidOption);
            String androidDeepLink = wynkService.<String>get(PaymentConstants.ANDROID_DEEP_LINK)
                    .map(link -> link.replace(PaymentConstants.PLAN_ID_PLACEHOLDER, productDetails.getId()))
                    .orElseGet(() -> "");
            String desktopDeepLink = wynkService.<String>get(PaymentConstants.DESKTOP_DEEP_LINK)
                    .map(link -> link.replace(PaymentConstants.PLAN_ID_PLACEHOLDER, productDetails.getId()))
                    .orElseGet(() -> "");
            String fallbackUrl = wynkService.<String>get(PaymentConstants.FALLBACK_URL)
                    .map(link -> link.replace(PaymentConstants.PLAN_ID_PLACEHOLDER, productDetails.getId()))
                    .orElseGet(() -> "");
            final String finalPayUrl = wynkService.<String>get(PaymentConstants.PAY_OPTION_DEEPLINK)
                    .map(link -> link.replace(PaymentConstants.PLAN_ID_PLACEHOLDER, productDetails.getId()))
                    .orElseGet(() -> "");
            AnalyticService.update(PaymentConstants.ANDROID_DEEP_LINK, androidDeepLink);
            AnalyticService.update(PaymentConstants.DESKTOP_DEEP_LINK, desktopDeepLink);
            AnalyticService.update(PaymentConstants.FALLBACK_URL, fallbackUrl);
            AnalyticService.update(PaymentConstants.PAY_OPTION_DEEPLINK, finalPayUrl);
            UrlShortenRequest.UrlShortenData data = new UrlShortenRequest.UrlShortenData(androidDeepLink, desktopDeepLink, fallbackUrl,finalPayUrl);
            final UrlShortenResponse shortenResponse = urlShortenService.generate(UrlShortenRequest.builder().key(wynkService.getBranchKey()).campaign(PaymentConstants.WINBACK_CAMPAIGN).channel(wynkService.getId()).data(data).build());
            return shortenResponse.getTinyUrl();
        } catch (Exception ex) {
            throw new WynkRuntimeException(ex);
        }
    }

    private String buildUrlFromWithOption(String url, IAppDetails appDetails, Optional<String> oldSidOption) {
        return oldSidOption.map(sid -> buildUrlFrom(url, appDetails) + AND + OLD_SID + EQUAL + sid).orElseGet(() -> buildUrlFrom(url, appDetails));
    }

    private String buildUrlFrom(String url, IAppDetails appDetails) {
        return url + BaseConstants.AND + OS + EQUAL + appDetails.getOs() + AND + SERVICE + EQUAL + appDetails.getService() + AND + APP_ID + EQUAL + appDetails.getAppId() + AND + BUILD_NO + EQUAL + appDetails.getBuildNo();
    }

}
