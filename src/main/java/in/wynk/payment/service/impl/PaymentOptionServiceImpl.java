package in.wynk.payment.service.impl;

import in.wynk.common.dto.SessionDTO;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.core.enums.PaymentGroup;
import in.wynk.payment.dto.response.PaymentOptionsDTO;
import in.wynk.payment.dto.response.PaymentOptionsDTO.PaymentMethodDTO;
import in.wynk.payment.service.IPaymentOptionService;
import in.wynk.payment.service.PaymentCachingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.UID;

@Service
public class PaymentOptionServiceImpl implements IPaymentOptionService {

    private static final int N=2;
    @Autowired
    private PaymentCachingService paymentCachingService;
    @Autowired
    private PayUMerchantPaymentService payUMerchantPaymentService;
    @Autowired
    private PaytmMerchantWalletPaymentService paytmMerchantWalletPaymentService;

    @Override
    public PaymentOptionsDTO getPaymentOptions(SessionDTO sessionDTO, String planId) {
        String uid = sessionDTO.get(UID);
        Map<PaymentGroup, List<PaymentMethod>> availableMethods = paymentCachingService.getGroupedPaymentMethods();
        List<UserPreferredPayment> preferredPayments = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(N);
        Callable<UserPreferredPayment> task1 = () -> paytmMerchantWalletPaymentService.getUserPreferredPayments(uid);
        Callable<UserPreferredPayment> task2 = () -> payUMerchantPaymentService.getUserPreferredPayments(uid);
        Future<UserPreferredPayment> preferredPaymentsCard = executorService.submit(task1);
        Future<UserPreferredPayment> preferredPaymentsWallet = executorService.submit(task2);
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(N, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (Exception e) { }
        finally {
            try {
                preferredPayments.add(preferredPaymentsCard.get());
            } catch (Exception e) { }
            try {
                preferredPayments.add(preferredPaymentsWallet.get());
            } catch (Exception e) { }
        }
        return paymentMethods(availableMethods, preferredPayments);
    }

    private PaymentOptionsDTO paymentMethods(Map<PaymentGroup, List<PaymentMethod>> availableMethods, List<UserPreferredPayment> preferredPayments){
        List<PaymentOptionsDTO.PaymentGroupsDTO> paymentGroupsDTOS = new ArrayList<>();
        for(PaymentGroup group: availableMethods.keySet()){
            List<PaymentMethodDTO> methodDTOS = availableMethods.get(group).stream().map(PaymentMethodDTO::new).collect(Collectors.toList());
            for(PaymentMethodDTO paymentMethodDTO : methodDTOS) {
                String paymentGroup = paymentMethodDTO.getGroup();
                String paymentCode = paymentMethodDTO.getPaymentCode();
                Map<String, Object> meta = paymentMethodDTO.getMeta();
                List<UserPreferredPayment> savedPayments = preferredPayments.parallelStream().filter(x -> x!=null && x.getOption().getGroup().toString().equals(paymentGroup) && x.getOption().getPaymentCode().toString().equals(paymentCode)).collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(savedPayments)) {
                    meta.put("savedPayments", savedPayments);
                }
            }
            PaymentOptionsDTO.PaymentGroupsDTO groupsDTO = PaymentOptionsDTO.PaymentGroupsDTO.builder().paymentMethods(methodDTOS).paymentGroup(group).build();
            paymentGroupsDTOS.add(groupsDTO);
        }
        return PaymentOptionsDTO.builder().paymentGroups(paymentGroupsDTOS).build();
    }

}
