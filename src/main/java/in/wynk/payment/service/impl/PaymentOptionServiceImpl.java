package in.wynk.payment.service.impl;

import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.PaymentGroup;
import in.wynk.payment.core.entity.PaymentMethod;
import in.wynk.payment.core.entity.UserPreferredPayment;
import in.wynk.payment.dao.UserPreferredPaymentsDao;
import in.wynk.payment.dto.PaymentOptionsDTO;
import in.wynk.payment.dto.PaymentOptionsDTO.PaymentGroupsDTO;
import in.wynk.payment.dto.PaymentOptionsDTO.PaymentMethodDTO;
import in.wynk.payment.service.IPaymentOptionService;
import in.wynk.payment.service.PaymentCachingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static in.wynk.commons.constants.Constants.UID;

@Service
public class PaymentOptionServiceImpl implements IPaymentOptionService {

    @Autowired
    private PaymentCachingService paymentCachingService;
    @Autowired
    private UserPreferredPaymentsDao userPreferredPaymentsDao;

    @Override
    public PaymentOptionsDTO getPaymentOptions(SessionDTO sessionDTO, String planId) {
        String uid = sessionDTO.get(UID);
        Map<PaymentGroup, List<PaymentMethod>> availableMethods = paymentCachingService.getGroupedPaymentMethods();
        List<UserPreferredPayment> preferredPayments = userPreferredPaymentsDao.findByUid(uid);
        return paymentMethods(availableMethods, preferredPayments);
    }

    private PaymentOptionsDTO paymentMethods(Map<PaymentGroup, List<PaymentMethod>> availableMethods, List<UserPreferredPayment> preferredPayments){
        List<PaymentGroupsDTO> paymentGroupsDTOS = new ArrayList<>();
        for(PaymentGroup group: availableMethods.keySet()){
            List<PaymentMethodDTO> methodDTOS = availableMethods.get(group).stream().map(PaymentMethodDTO::new).collect(Collectors.toList());
            PaymentGroupsDTO groupsDTO = PaymentGroupsDTO.builder().paymentMethods(methodDTOS).paymentGroup(group).build();
            paymentGroupsDTOS.add(groupsDTO);
        }
        return PaymentOptionsDTO.builder().paymentGroups(paymentGroupsDTOS).build();
    }
}
