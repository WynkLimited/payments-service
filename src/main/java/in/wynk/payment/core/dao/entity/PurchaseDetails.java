package in.wynk.payment.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.GeoLocation;
import in.wynk.data.entity.MongoBaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@SuperBuilder
@AnalysedEntity
@Document("purchase_details")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PurchaseDetails extends MongoBaseEntity<String> implements IChargingDetails {

    @Analysed
    private IGeolocation geoLocation;

    @Field("callback_url")
    private String callbackUrl;

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

    @Override
    @JsonIgnore
    public ICallbackDetails getCallbackDetails() {
        return () -> callbackUrl;
    }

}
