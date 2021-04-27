package in.wynk.payment.dto.itune;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;

@Getter
@SuperBuilder
@AnalysedEntity
public class ItunesLatestReceiptResponse extends LatestReceiptResponse {

    @Analysed
    private final String decodedReceipt;
    @Analysed
    private final ItunesReceiptType itunesReceiptType;
    @Analysed
    private final List<LatestReceiptInfo> latestReceiptInfo;
    @Analysed
    private final List<PendingRenewalInfo> pendingRenewalInfo;

    public List<PendingRenewalInfo> getPendingRenewalInfo() {
        return CollectionUtils.isNotEmpty(pendingRenewalInfo) ?  pendingRenewalInfo: Collections.EMPTY_LIST;
    }

    public boolean isAutoRenewalOff() {
        LatestReceiptInfo receiptInfo = latestReceiptInfo.get(0);
        return this.pendingRenewalInfo.stream().filter(pendingRenew -> pendingRenew.getAutoRenewProductId().equals(receiptInfo.getProductId())).anyMatch(pendingInfo -> pendingInfo.getAutoRenewStatus().equalsIgnoreCase("0"));
    }

}
