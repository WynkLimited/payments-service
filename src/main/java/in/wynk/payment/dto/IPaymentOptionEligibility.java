package in.wynk.payment.dto;

public interface IPaymentOptionEligibility {
    boolean isEligible(String msisdn, String payGroup, String payId);

}
