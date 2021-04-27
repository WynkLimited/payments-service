package in.wynk.payment.controller;

import in.wynk.common.dto.WynkResponse;
import in.wynk.payment.scheduler.PaymentMysqlDumpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wynk/s2s/v1/mysqldump/")
public class PaymentMysqlDbDumpController {
    @Autowired
    private PaymentMysqlDumpService paymentMysqlDumpService;
    @GetMapping("/transaction")
    public WynkResponse<Boolean> allPlans() {
        paymentMysqlDumpService.startCassandraS3Export();
        return WynkResponse.<Boolean>builder().body(true).build();
    }
}
