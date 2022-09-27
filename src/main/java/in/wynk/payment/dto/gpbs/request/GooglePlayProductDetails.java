package in.wynk.payment.dto.gpbs.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.payment.dto.ProductDetailsDto;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
public class GooglePlayProductDetails extends ProductDetailsDto {
    @Analysed
    private String skuId;
}
