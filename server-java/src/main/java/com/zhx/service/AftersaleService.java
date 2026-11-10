package com.zhx.service;

import com.zhx.domain.AuthUser;
import com.zhx.dto.Money;
import com.zhx.dto.Row;
import com.zhx.exception.HttpError;
import com.zhx.repository.DbRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AftersaleService {
    private final DbRepository db;
    private final ServiceSupport support;
    private final RefundService refunds;

    public AftersaleService(DbRepository db, ServiceSupport support, RefundService refunds) {
        this.db = db;
        this.support = support;
        this.refunds = refunds;
    }

    @Transactional
    public Map<String, Object> submit(AuthUser me, String orderId, Map<String, Object> body) {
        support.assertRole(me, "customer");
        String reason = String.valueOf(body.getOrDefault("reason", "")).trim();
        if (reason.isEmpty()) throw HttpError.bad("请填写售后原因");
        Map<String, Object> order = db.lockOrderForUser(orderId, me.id());
        if (order == null) throw HttpError.bad("订单不存在或无权访问");
        String status = Row.str(order, "status");
        if (!"to_pickup".equals(status) && !"picked".equals(status) && !"no_show".equals(status)) {
            throw HttpError.bad("当前订单状态不可发起售后");
        }
        if (db.activeAftersaleExists(orderId)) throw HttpError.bad("该订单已有处理中的售后申请，请等待处理结果");
        if (db.refundExistsByOrder(orderId)) throw HttpError.bad("该订单已有退款记录，无需重复申请售后");
        String evidence = body.get("evidence") == null ? "未上传" : String.valueOf(body.get("evidence"));
        db.insertAftersale(support.uid("A"), orderId, reason, evidence, "merchant_review",
                support.nowUtc(), support.nowUtc().plusMinutes(24 * 60));
        support.log("顾客", "提交特殊售后", "订单 " + Row.str(order, "no") + "：" + reason);
        Map<String, Object> store = support.storeOf(Row.str(order, "store_id"));
        support.notify(store == null ? null : Row.str(store, "owner_id"), "收到特殊售后申请",
                "订单 " + Row.str(order, "no") + "：" + reason + "（需在 24 小时内处理）",
                "/merchant/aftersale", "aftersale");
        return new LinkedHashMap<>();
    }

    @Transactional
    public Map<String, Object> escalate(AuthUser me, String id) {
        support.assertRole(me, "customer");
        Map<String, Object> aftersale = db.lockAftersale(id);
        if (aftersale == null || !"rejected".equals(Row.str(aftersale, "status"))) {
            throw HttpError.bad("当前状态不可申请平台介入");
        }
        Map<String, Object> order = db.lockOrderForUser(Row.str(aftersale, "order_id"), me.id());
        if (order == null) throw HttpError.bad("订单不存在或无权访问");
        db.updateAftersaleStatus(id, "platform_review");
        support.log("顾客", "申请平台介入", "售后 " + id + " 转入平台处理");
        for (Map<String, Object> admin : db.allAccounts()) {
            if ("admin".equals(Row.str(admin, "role"))) {
                support.notify(Row.str(admin, "id"), "有新售后需平台介入",
                        "售后 " + id + " 顾客已申请平台介入，请及时裁决", "/admin/intervention", "aftersale");
            }
        }
        return new LinkedHashMap<>();
    }

    @Transactional
    public Map<String, Object> handle(AuthUser me, String id, Map<String, Object> body) {
        support.assertRole(me, "merchant");
        Map<String, Object> store = support.merchantStoreOf(me);
        Map<String, Object> aftersale = db.lockAftersale(id);
        if (aftersale == null || !"merchant_review".equals(Row.str(aftersale, "status"))) {
            throw HttpError.bad("该申请已处理");
        }
        Map<String, Object> order = db.lockOrder(Row.str(aftersale, "order_id"));
        if (order == null || !Row.str(store, "id").equals(Row.str(order, "store_id"))) {
            throw HttpError.bad("该售后不属于本店订单");
        }
        boolean agree = support.flag(body.get("agree"));
        String reply = body.get("reply") == null ? "" : String.valueOf(body.get("reply"));
        if (agree && db.refundExistsByOrder(Row.str(order, "id"))) {
            throw HttpError.bad("该订单已有退款记录，请勿重复处理");
        }
        db.updateAftersaleHandled(id, reply, support.nowUtc(), agree ? "agreed" : "rejected");
        if (agree) {
            boolean wasPicked = "picked".equals(Row.str(order, "status")) || "no_show".equals(Row.str(order, "status"));
            Map<String, Object> batch = db.batchById(Row.str(order, "batch_id"));
            boolean restock = !wasPicked && "on_sale".equals(support.batchStatus(batch));
            refunds.createRefund(Row.str(order, "id"), "aftersale", restock, "特殊售后（商家同意）：" + Row.str(aftersale, "reason"));
            support.log("商家", "处理特殊售后", "售后 " + id + " 同意退款");
        } else {
            support.log("商家", "处理特殊售后", "售后 " + id + " 已拒绝：" + reply);
        }
        support.notify(Row.str(order, "user_id"), agree ? "售后已同意" : "售后被驳回",
                "订单 " + Row.str(order, "no") + "：" + (reply.isEmpty()
                        ? (agree ? "商家已同意退款" : "商家未同意") : reply),
                "/order/" + Row.str(order, "id"), "aftersale");
        return new LinkedHashMap<>();
    }

    @Transactional
    public Map<String, Object> resolve(AuthUser me, String id, Map<String, Object> body) {
        support.assertRole(me, "admin");
        Map<String, Object> aftersale = db.lockAftersale(id);
        if (aftersale == null || !"platform_review".equals(Row.str(aftersale, "status"))) {
            throw HttpError.bad("该申请不在平台处理队列");
        }
        String note = body.get("note") == null ? "" : String.valueOf(body.get("note"));
        db.updateAftersaleResolved(id, note, support.nowUtc());
        Map<String, Object> order = db.lockOrder(Row.str(aftersale, "order_id"));
        boolean refund = support.flag(body.get("refund"));
        if (refund && order != null && !db.refundExistsByOrder(Row.str(order, "id"))) {
            refunds.createRefund(Row.str(order, "id"), "platform", false, "平台介入裁决退款：" + note);
        }
        support.log("平台管理员", "平台介入处理", "售后 " + id + " 裁决：" + (refund ? "退款" : "维持商家结论"));
        if (order != null) {
            support.notify(Row.str(order, "user_id"), "平台介入结果已出",
                    "订单 " + Row.str(order, "no") + "：" + (refund ? "平台裁定退款" : "维持商家结论")
                            + (note.isEmpty() ? "" : "（" + note + "）"),
                    "/order/" + Row.str(order, "id"), "aftersale");
        }
        return new LinkedHashMap<>();
    }
}
