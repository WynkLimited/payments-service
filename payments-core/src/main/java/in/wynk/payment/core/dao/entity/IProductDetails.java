package in.wynk.payment.core.dao.entity;

import com.github.annotation.analytic.core.annotations.Analysed;

import java.io.Serializable;

public interface IProductDetails extends Serializable {

    String getId();

    @Analysed(name = "productType")
    String getType();

}