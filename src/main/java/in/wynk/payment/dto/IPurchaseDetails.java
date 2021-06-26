package in.wynk.payment.dto;

public interface IPurchaseDetails {
    IPayerDetails getPayerDetails();
    PaymentDetails getPaymentDetails();
    IProductDetails getProductDetails();
}
