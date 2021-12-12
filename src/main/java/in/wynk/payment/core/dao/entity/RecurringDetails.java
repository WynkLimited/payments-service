package in.wynk.payment.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import in.wynk.data.entity.MongoBaseEntity;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;

@Getter
@SuperBuilder
@Document("recurring_payment_details")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RecurringDetails extends MongoBaseEntity<RecurringDetails.PurchaseKey> implements IChargingDetails {

    @Field("callback_url")
    private String callbackUrl;

    @Field("source_transaction_id")
    private String sourceTransactionId;

    @Field("app_details")
    private IAppDetails appDetails;

    @Field("payer_details")
    private IUserDetails userDetails;

    @Field("payment_details")
    private IPaymentDetails paymentDetails;

    @Field("product_details")
    private IProductDetails productDetails;

    @Field("page_url_details")
    private IPageUrlDetails pageUrlDetails;

    @Override
    @JsonIgnore
    public ICallbackDetails getCallbackDetails() {
        return () -> callbackUrl;
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