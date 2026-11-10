package com.zhx.service;

import com.zhx.dto.Money;
import com.zhx.dto.Row;
import com.zhx.repository.DbRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 退款服务：创建退款记录 + 后台任务确认退款。
 * 复刻 server/services/payment.service.js 的 createRefund / settleRefund。
 */
@Service
public class RefundService {
    private final DbRepository db;
    private final ServiceSupport support;

    public RefundService(DbRepository db, ServiceSupport support) {
        this.db = db;
        this.support = support;
    }

    /**
     * 创建退款记录，并将订单状态置为 refunding。
     * 供 OrderService / MerchantService / AftersaleService 调用。
     */
    @Transactional
    public void createRefund(String orderId, String type, boolean restock, String reason) {
        var order = db.lockOrder(orderId);
        if (order == null) return;
        int amount = Money.cents(order.get("amount"));
        db.insertRefund(support.uid("R"), orderId, type, amount, "processing",
                restock, reason, support.nowUtc());
        db.updateOrderStatus(orderId, "refunding");
    }

    /**
     * 后台任务调用：processing → success，回补 sold-1（仅可售），回滚积分。
     */
    @Transactional
    public void confirmRefund(String refundId) {
        var refund = db.refundById(refundId);
        if (refund == null || !"processing".equals(Row.str(refund, "status"))) return;
        String orderId = Row.str(refund, "order_id");
        var order = db.lockOrder(orderId);
        refund = db.lockRefund(refundId);
        if (refund == null || !"processing".equals(Row.str(refund, "status"))) return;
        db.updateRefundSuccess(refundId, support.nowUtc());
        if (order == null) return;
        db.updateOrderStatus(orderId, "refunded");
        boolean restock = Row.bool(refund, "restock");
        if (restock) {
            String batchId = Row.str(order, "batch_id");
            db.reduceSold(batchId);
        }
        int points = Row.i(order, "points_gained");
        if (points > 0) {
            int revoked = support.revokePoints(Row.str(order, "user_id"), points);
            if (revoked > 0) {
                support.log("支付渠道", "退款回滚惜食积分",
                        "订单 " + Row.str(order, "no") + " 退款到账，回滚 " + revoked + " 积分");
            }
        }
        support.log("支付渠道", "跟踪退款结果", "退款 " + refundId + " 已原路到账");
        String userName = Row.str(order, "user_id");
        if (userName != null) {
            support.notify(userName, "退款已到账",
                    "订单 " + Row.str(order, "no") + " 退款 ¥"
                            + Money.yuan(Money.cents(refund.get("amount"))).toPlainString() + " 已原路退回",
                    "/order/" + orderId, "refund");
        }
    }
}
