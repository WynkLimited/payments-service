package in.wynk.payment.core.dao.entity;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Builder
@AnalysedEntity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SupplierInfo {

    @Field("state_code")
    private String stateCode;

    private String state;
    @Field("gst_no")
    private String GSTNo;
}
