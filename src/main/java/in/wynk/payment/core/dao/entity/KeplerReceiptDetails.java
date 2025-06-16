package in.wynk.payment.core.dao.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@ToString
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class KeplerReceiptDetails extends ReceiptDetails {

    @Field("amazon_user_id")
    private String amazonUserId;
    @Field("renewal_date")
    private long renewalDate;

    @Override
    public Integer getNotificationType () {
        return null;
    }

    @Override
    public boolean isRenew () {
        return false;
    }
}