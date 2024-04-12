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
    private double price;

    @Override
    public String getId () {
        if (type.equals("PLAN")) {
            return getPlanId();
        } else if (type.equals("POINT")) {
            return getItemId();
        }
        return null;
    }
}
