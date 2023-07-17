package in.wynk.payment.dto;

import in.wynk.payment.core.dao.entity.IProductDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailsDto implements IProductDetails {
    private String planId;
    private String itemId;
    private String type;

    @Override
    public String getId() {
        return getPlanId();
    }
}
