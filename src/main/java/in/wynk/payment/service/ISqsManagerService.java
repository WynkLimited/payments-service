package in.wynk.payment.service;

public interface ISqsManagerService {

     <T> void publishSQSMessage(T message, String messageDeDuplicationId);
}
