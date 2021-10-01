package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IProductDetails;
import lombok.*;

import java.util.Optional;

@Getter
@Builder
@ToString
@AnalysedEntity
public class PurchaseInitEvent {
    @Analysed
    private String uid;
    @Analysed
    private String msisdn;
    @Analysed
    private String clientAlias;
    @Analysed
    private String transactionId;
    @Analysed
    private IAppDetails appDetails;
    @Analysed
    private IProductDetails productDetails;

    private Optional<String> sid;

}
