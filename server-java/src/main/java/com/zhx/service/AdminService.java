package com.zhx.service;

import com.zhx.dto.Row;
import com.zhx.exception.HttpError;
import com.zhx.repository.DbRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 平台管理侧：入驻审核、门店经营状态、批次下架、账号启停、演示顺延。
 * 复刻 server/services/admin.service.js。
 */
@Service
public class AdminService {
    private final DbRepository db;
    private final ServiceSupport support;

    public AdminService(DbRepository db, ServiceSupport support) {
        this.db = db;
        this.support = support;
    }

    // ---------------------------------------------------------------- 入驻审核

    @Transactional
    public void auditApplication(String adminId, String appId, Map<String, Object> req) {
        support.assertRole(db.accountById(adminId), "admin");
        Map<String, Object> app = db.applicationById(appId);
        if (app == null || !"pending".equals(Row.str(app, "status"))) {
            throw HttpError.bad("该申请已被处理");
        }
        boolean pass = Boolean.TRUE.equals(req == null ? null : req.get("pass"))
                || "true".equals(str(req == null ? null : req.get("pass")));
        String note = strOrDefault(req == null ? null : req.get("note"), "");
        LocalDateTime now = support.nowUtc();
        db.auditApplication(appId, pass ? "approved" : "rejected", note, now);
        if (pass) {
            if (!db.storeExistsByOwner(Row.str(app, "account_id"))) {
                db.insertStore(support.uid("S"),
                        Row.str(app, "store_name"), Row.str(app, "category"), Row.str(app, "address"),
                        toDecimal(app.get("lat")), toDecimal(app.get("lng")), Row.str(app, "phone"),
                        "新店入驻，欢迎光临", Row.str(app, "account_id"), "approved");
            }
            db.updateAccountRole(Row.str(app, "account_id"));
        }
        support.log("平台管理员", "审核商家入驻",
                "「" + Row.str(app, "store_name") + "」" + (pass ? "审核通过" : "退回补充") + "：" + note);
        support.notify(Row.str(app, "account_id"),
                pass ? "入驻审核已通过" : "入驻申请被退回",
                "「" + Row.str(app, "store_name") + "」"
                        + (pass ? "审核通过，可发布盲盒批次" : "需补充材料：" + note),
                pass ? "/merchant" : "/merchant/profile", "audit");
    }

    // ---------------------------------------------------------------- 门店经营状态

    @Transactional
    public void setStoreOperation(String adminId, String storeId, Map<String, Object> req) {
        support.assertRole(db.accountById(adminId), "admin");
        Map<String, Object> store = db.storeById(storeId);
        if (store == null || !"approved".equals(Row.str(store, "audit_status"))) {
            throw HttpError.bad("门店不存在或尚未通过审核");
        }
        String reason = support.moderationReason(req == null ? null : req);
        String status = str(req == null ? null : req.get("status"));
        if (!"active".equals(status) && !"suspended".equals(status)) {
            throw HttpError.bad("经营状态无效");
        }
        if (status.equals(Row.str(store, "operation_status"))) {
            throw HttpError.bad("门店状态已变化，请刷新后重试");
        }
        LocalDateTime now = support.nowUtc();
        db.updateStoreOperation(storeId, status, reason, now);
        String action = "suspended".equals(status) ? "暂停门店经营" : "恢复门店经营";
        support.log(support.roleName("admin"), action,
                "门店 " + Row.str(store, "name") + "（" + storeId + "）：" + reason
                        + "；已有订单继续履约，已停售批次不自动恢复");
        support.notify(Row.str(store, "owner_id"), action,
                Row.str(store, "name") + "：" + reason + "。已有订单请继续履约或按售后流程处理。",
                "/merchant", "audit");
    }

    // ---------------------------------------------------------------- 平台下架批次

    @Transactional
    public void adminStopBatch(String adminId, String batchId, String reason) {
        support.assertRole(db.accountById(adminId), "admin");
        Map<String, Object> b = db.batchById(batchId);
        if (b == null || !"on_sale".equals(Row.str(b, "status"))) {
            throw HttpError.bad("批次已停售或已结束");
        }
        reason = support.moderationReason(Map.of("reason", reason));
        LocalDateTime now = support.nowUtc();
        db.stopBatchIfOnSale(batchId, "平台下架：" + reason, now);
        Map<String, Object> store = db.storeById(Row.str(b, "store_id"));
        String storeName = store == null ? "" : Row.str(store, "name");
        support.log(support.roleName("admin"), "平台下架批次",
                "门店 " + storeName + "，批次 " + Row.str(b, "no") + "（" + batchId + "）：" + reason
                        + "；已有订单继续履约");
        if (store != null) {
            support.notify(Row.str(store, "owner_id"), "盲盒批次已被平台下架",
                    Row.str(b, "no") + "：" + reason + "。已停止新增交易，已有订单请继续履约或处理售后。",
                    "/merchant/batches", "audit");
        }
    }

    // ---------------------------------------------------------------- 账号启停

    @Transactional
    public void toggleAccount(String adminId, String accountId) {
        support.assertRole(db.accountById(adminId), "admin");
        var acc = db.accountById(accountId);
        if (acc == null) throw HttpError.bad("账号不存在");
        if ("admin".equals(acc.role())) throw HttpError.bad("平台管理员账号不可停用");
        String status = "active".equals(acc.status()) ? "disabled" : "active";
        db.updateAccountStatus(accountId, status);
        support.log("平台管理员", "管理账号状态",
                "账号 " + acc.name() + " 已" + ("active".equals(status) ? "启用" : "停用") + "（历史交易保留）");
    }

    // ---------------------------------------------------------------- 演示顺延

    /**
     * 遍历所有 on_sale 的批次，pickup_start/end 都顺延到 now 之后。
     */
    @Transactional
    public int refreshDemoWindow(String adminId) {
        support.assertRole(db.accountById(adminId), "admin");
        int changed = 0;
        LocalDateTime now = support.nowUtc();
        List<Map<String, Object>> batches = db.onSaleBatches();
        for (Map<String, Object> b : batches) {
            LocalDateTime end = Row.dt(b, "pickup_end");
            if (end == null || end.isAfter(now)) continue;
            LocalDateTime start = Row.dt(b, "pickup_start");
            long durMinutes = start != null
                    ? Math.max(java.time.Duration.between(start, end).toMinutes(), 60)
                    : 60;
            LocalDateTime newStart = now.minusMinutes(10);
            LocalDateTime newEnd = newStart.plusMinutes(durMinutes);
            db.updateBatchWindow(Row.str(b, "id"), newStart, newEnd);
            changed++;
        }
        if (changed > 0) {
            support.log("平台管理员", "重开今日售卖时段", "顺延 " + changed + " 个已过期的在售批次");
        }
        return changed;
    }

    // ---------------------------------------------------------------- helpers

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String strOrDefault(Object v, String def) {
        String s = str(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static BigDecimal toDecimal(Object v) {
        if (v instanceof BigDecimal d) return d;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (v == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
