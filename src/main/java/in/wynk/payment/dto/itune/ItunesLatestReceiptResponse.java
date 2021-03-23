package in.wynk.payment.dto.itune;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

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

}
