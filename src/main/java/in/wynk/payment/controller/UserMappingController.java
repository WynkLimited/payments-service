package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.common.dto.EmptyResponse;
import in.wynk.payment.dto.request.UserMappingRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("wynk/s2s/v1")
public class UserMappingController {

    @PostMapping("/mapping")
    @AnalyseTransaction(name="wynkUidToExtUserIdMapping")
    public EmptyResponse updateUserMapping(@RequestBody UserMappingRequest userMappingRequest){

        return EmptyResponse.response();
    }
}
