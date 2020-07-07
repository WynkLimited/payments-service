package in.wynk.payment.controller;

import in.wynk.commons.constants.SessionKeys;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.session.constant.BeanConstant;
import in.wynk.session.service.ISessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/session")
public class SessionController {

    @Autowired
    @Qualifier(BeanConstant.SESSION_MANAGER_BEAN)
    private ISessionManager sessionManager;

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody List<PlanDTO> eligiblePlans) {
        SessionDTO sessionDTO = new SessionDTO();
        sessionDTO.setPayload(new HashMap<>());
        sessionDTO.getPayload().put(SessionKeys.ELIGIBLE_PLANS, eligiblePlans);
        return new ResponseEntity<>(sessionManager.init(sessionDTO, 5, TimeUnit.DAYS), HttpStatus.OK);
    }

    @GetMapping("/get/{sid}")
    public ResponseEntity<?> create(@PathVariable String sid) {
        return new ResponseEntity<>(sessionManager.get(UUID.fromString(sid)), HttpStatus.OK);
    }

}
