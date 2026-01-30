package in.wynk.payment.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.GeoLocation;
import in.wynk.common.dto.IGeoLocation;
import in.wynk.data.entity.MongoBaseEntity;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;

@Getter
@Deprecated
@SuperBuilder
@AnalysedEntity
@Document("recurring_payment_details")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RecurringDetails extends MongoBaseEntity<RecurringDetails.PurchaseKey> implements IChargingDetails {

    @Field("callback_url")
    private String callbackUrl;

    @Field("source_transaction_id")
    private String sourceTransactionId;

    @Analysed
    @Field("app_details")
    private IAppDetails appDetails;

    @Analysed
    @Field("payer_details")
    private IUserDetails userDetails;

    @Analysed
    @Field("payment_details")
    private IPaymentDetails paymentDetails;

    @Analysed
    @Field("product_details")
    private IProductDetails productDetails;

    @Field("page_url_details")
    private IPageUrlDetails pageUrlDetails;

    @Analysed
    private IGeoLocation geoLocation;

    @Override
    @JsonIgnore
    public ICallbackDetails getCallbackDetails() {
        return () -> callbackUrl;
    }

    @Override
    public ISessionDetails getSessionDetails () {
        return null;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PurchaseKey implements Serializable {
        @Field("uid")
        private String uid;
        @Field("product_key")
        private String productKey;
    }

}