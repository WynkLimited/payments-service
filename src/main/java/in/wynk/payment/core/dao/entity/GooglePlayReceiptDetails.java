package in.wynk.payment.core.dao.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GooglePlayReceiptDetails extends ReceiptDetails {
    @Field("purchase_token")
    @Indexed(unique = true)
    private String purchaseToken;
    @Field("subscription_id")
    private String subscriptionId;
    @Field("package_name")
    private String packageName;
    @Field("sku_id")
    private String skuId;
    private String service;
}
