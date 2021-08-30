package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.common.dto.EmptyResponse;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.dto.request.UserMappingRequest;
import in.wynk.payment.service.IUserMappingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("wynk/s2s/v1")
public class UserMappingController {

    @PostMapping("/mapping")
    @AnalyseTransaction(name = "wynkUidToExtUserIdMapping")
    public EmptyResponse updateUserMapping(@Valid @RequestBody UserMappingRequest request) {
        IUserMappingService mappingService = BeanLocatorFactory.getBean(request.getCode().getCode(), IUserMappingService.class);
        mappingService.addUserMapping(request);
        return EmptyResponse.response();
    }

}