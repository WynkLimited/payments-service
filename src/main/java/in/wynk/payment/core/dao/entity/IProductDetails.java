package in.wynk.payment.core.dao.entity;


import com.github.annotation.analytic.core.annotations.Analysed;

public interface IProductDetails {
    String getId();
    @Analysed(name = "productType")
    String getType();
}