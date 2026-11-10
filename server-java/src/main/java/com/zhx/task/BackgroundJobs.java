package com.zhx.task;

import com.zhx.service.DailyBatchService;
import com.zhx.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 后台任务：支付/退款回调、超时扫描与每日批次发布。
 * 对应 server/jobs/background.jobs.js：与浏览器是否打开无关。
 */
@Component
@Order(2)
public class BackgroundJobs implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BackgroundJobs.class);

    private final PaymentService payments;
    private final DailyBatchService dailyBatches;

    @Value("${app.demo-delay.pay-ms:6000}")
    private long payDelayMs;

    @Value("${app.demo-delay.refund-ms:10000}")
    private long refundDelayMs;

    @Value("${app.auto-settle:true}")
    private boolean autoSettle;

    public BackgroundJobs(PaymentService payments, DailyBatchService dailyBatches) {
        this.payments = payments;
        this.dailyBatches = dailyBatches;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (autoSettle) publishDaily();
    }

    /** 每秒：到期支付确认 + 退款到账 + 支付/取货超时关闭。 */
    @Scheduled(fixedDelay = 1000)
    public void tick() {
        if (autoSettle) {
            try {
                payments.settleDue(payDelayMs, refundDelayMs);
            } catch (Exception e) {
                log.warn("[settle] {}", e.getMessage());
            }
            try {
                payments.scanTimeouts();
            } catch (Exception e) {
                log.warn("[timeout] {}", e.getMessage());
            }
        }
    }

    /** 每分钟补发当日批次。 */
    @Scheduled(fixedDelay = 60000)
    public void publish() {
        if (autoSettle) publishDaily();
    }

    private void publishDaily() {
        try {
            dailyBatches.publishDailyBatches(System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("[daily-publish] {}", e.getMessage());
        }
    }
}
