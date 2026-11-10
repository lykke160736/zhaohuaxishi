package com.zhx;

import com.zhx.service.DailyBatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/** 对拍 tests/daily-batches.test.js。 */
class DailyBatchTest extends ApiTestBase {

    @Autowired
    DailyBatchService dailyService;

    @Autowired
    JdbcTemplate jdbc;

    private long at(String text) {
        return java.time.OffsetDateTime.parse("2026-11-02T" + text + ":00+08:00")
                .toInstant().toEpochMilli();
    }

    private long nextDayAt(String text) {
        return java.time.OffsetDateTime.parse("2026-11-03T" + text + ":00+08:00")
                .toInstant().toEpochMilli();
    }

    private String statusOf(String batchId) {
        return jdbc.queryForObject("SELECT status FROM batches WHERE id=?", String.class, batchId);
    }

    @Test
    void dailyPlanPublishesByBeijingTimeAndNeverBackfillsAfterCutoff() {
        int ordersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        assertEquals(0, dailyService.publishDailyBatches(at("09:59")));
        assertEquals(1, dailyService.publishDailyBatches(at("10:00")));

        var breakfast = jdbc.queryForMap(
                "SELECT * FROM batches WHERE id='DAILY-2026-11-02-S16'");
        assertEquals(12, ((Number) breakfast.get("total")).intValue());
        assertEquals(0, ((Number) breakfast.get("sold")).intValue());

        assertEquals(0, dailyService.publishDailyBatches(at("10:01")));
        jdbc.update("UPDATE batches SET status='stopped' WHERE id='DAILY-2026-11-02-S16'");
        assertEquals(0, dailyService.publishDailyBatches(at("10:02")));
        assertEquals("stopped", statusOf("DAILY-2026-11-02-S16"));

        assertEquals(5, dailyService.publishDailyBatches(at("14:10")));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM batches WHERE id='DAILY-2026-11-02-S15'", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM batches WHERE id='DAILY-2026-11-02-S1'", Integer.class));

        assertEquals(0, dailyService.publishDailyBatches(at("14:11")));
        assertEquals(0, dailyService.publishDailyBatches(at("23:00")));
        assertEquals(1, dailyService.publishDailyBatches(nextDayAt("10:00")));
        assertEquals(ordersBefore, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
    }

    @Test
    void suspendedStoreDisabledMerchantAndSoldOutAreNotRepublished() {
        jdbc.update("UPDATE stores SET operation_status='suspended' WHERE id='S7'");
        jdbc.update("UPDATE accounts SET status='disabled' WHERE id=(SELECT owner_id FROM stores WHERE id='S12')");
        assertEquals(0, dailyService.publishDailyBatches(at("13:20")));

        jdbc.update("UPDATE stores SET operation_status='active' WHERE id='S7'");
        assertEquals(1, dailyService.publishDailyBatches(at("13:21")));
        jdbc.update("UPDATE batches SET sold=total WHERE id='DAILY-2026-11-02-S7'");
        assertEquals(0, dailyService.publishDailyBatches(at("13:22")));

        var batch = jdbc.queryForMap("SELECT * FROM batches WHERE id='DAILY-2026-11-02-S7'");
        assertEquals(0, ((Number) batch.get("total")).intValue() - ((Number) batch.get("sold")).intValue());
    }
}
