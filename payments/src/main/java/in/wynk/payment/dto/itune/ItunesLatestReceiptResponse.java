package in.wynk.payment.dto.itune;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import lombok.Getter;
import lombok.Setter;
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
    @Setter
    @Analysed
    private List<LatestReceiptInfo> latestReceiptInfo;
    @Analysed
    private final List<PendingRenewalInfo> pendingRenewalInfo;
    @Analysed
    private final String service;

    public List<PendingRenewalInfo> getPendingRenewalInfo() {
        return CollectionUtils.isNotEmpty(pendingRenewalInfo) ?  pendingRenewalInfo: Collections.EMPTY_LIST;
    }

}
