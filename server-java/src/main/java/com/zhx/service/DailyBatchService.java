package com.zhx.service;

import com.zhx.domain.DailyPlans;
import com.zhx.dto.Json;
import com.zhx.dto.Row;
import com.zhx.repository.DbRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DailyBatchService {
    private static final ZoneId CN = ZoneId.of("+08:00");

    private final DbRepository db;
    private final ServiceSupport support;

    public DailyBatchService(DbRepository db, ServiceSupport support) {
        this.db = db;
        this.support = support;
    }

    @Transactional
    public int publishDailyBatches(long at) {
        LocalDate day = Instant.ofEpochMilli(at + 28800000L).atZone(ZoneOffset.UTC).toLocalDate();
        int created = 0;
        for (Map<String, Object> store : db.dailyStores()) {
            String storeId = Row.str(store, "id");
            DailyPlans.Plan plan = DailyPlans.PLANS.get(storeId);
            if (plan == null) continue;
            LocalDateTime start = LocalDateTime.of(day, LocalTime.parse(plan.publishTime())).atZone(CN)
                    .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            LocalDateTime end = LocalDateTime.of(day, LocalTime.parse(plan.pickupEnd())).atZone(CN)
                    .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            long startMillis = start.toInstant(ZoneOffset.UTC).toEpochMilli();
            long endMillis = end.toInstant(ZoneOffset.UTC).toEpochMilli();
            if (at < startMillis || at >= endMillis) continue;
            String id = "DAILY-" + day + "-" + storeId;
            if (db.batchById(id) != null) continue;
            String stamp = support.iso(at);
            List<Map<String, Object>> logs = new java.util.ArrayList<>();
            Map<String, Object> logItem = new LinkedHashMap<>();
            logItem.put("at", stamp);
            logItem.put("delta", plan.quantity());
            logItem.put("by", "每日计划发布");
            logItem.put("after", plan.quantity());
            logs.add(logItem);
            db.insertBatch(id, id, storeId, Row.str(store, "category"), plan.price(), start, end,
                    plan.quantity(), "on_sale", support.nowUtc(), Json.write(logs));
            created++;
            support.log("系统", "每日批次发布",
                    Row.str(store, "name") + " · " + plan.quantity() + "份 · 取货" + plan.publishTime()
                            + "—" + plan.pickupEnd());
        }
        return created;
    }
}
