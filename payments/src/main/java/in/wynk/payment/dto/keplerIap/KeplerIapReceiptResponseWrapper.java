package in.wynk.payment.dto.keplerIap;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AnalysedEntity
public class KeplerIapReceiptResponseWrapper extends KeplerIapReceiptResponse {

    private String receiptID;
    private String appUserId;
    private KeplerIapReceiptResponse decodedResponse;

    @Override
    public boolean isBetaProduct() {
        return decodedResponse.isBetaProduct();
    }

    @Override
    public Long getCancelDate() {
        return decodedResponse.getCancelDate();
    }

    @Override
    public String getProductId() {
        return decodedResponse.getProductId();
    }

    @Override
    public String getProductType() {
        return decodedResponse.getProductType();
    }

    @Override
    public Long getPurchaseDate() {
        return decodedResponse.getPurchaseDate();
    }

    @Override
    public int getQuantity() {
        return decodedResponse.getQuantity();
    }

    @Override
    public Long getRenewalDate() {
        return decodedResponse.getRenewalDate();
    }

    @Override
    public String getTerm() {
        return decodedResponse.getTerm();
    }

    @Override
    public String getTermSku() {
        return decodedResponse.getTermSku();
    }

    @Override
    public boolean isTestTransaction() {
        return decodedResponse.isTestTransaction();
    }
}
