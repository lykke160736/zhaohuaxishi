package com.zhx.service;

import com.zhx.dto.Json;
import com.zhx.dto.Money;
import com.zhx.dto.Row;
import com.zhx.repository.DbRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 支付后台任务：confirmPayment / failPayment。
 * 复刻 server/services/payment.service.js 的 confirmPayment。
 */
@Service
public class PaymentService {
    private final DbRepository db;
    private final ServiceSupport support;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaymentService.class);
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    private final RefundService refundService;

    public PaymentService(DbRepository db, ServiceSupport support, RefundService refundService, org.springframework.transaction.PlatformTransactionManager manager) {
        this.transactions = new org.springframework.transaction.support.TransactionTemplate(manager);
        this.db = db;
        this.support = support;
        this.refundService = refundService;
    }

    /**
     * 后台任务调用：pending → paid，生成 pickup_code / channel_tx / qr_token，
     *  reserved→sold，发放积分。
     */
    @Transactional
    public void confirmPayment(String intentId) {
        var intent = db.intentById(intentId);
        if (intent == null || !"pending".equals(Row.str(intent, "status"))) return;
        var order = db.lockOrder(Row.str(intent, "order_id"));
        intent = db.lockIntent(intentId);
        if (intent == null || !"pending".equals(Row.str(intent, "status"))) return;
        if (order == null || !"pending_payment".equals(Row.str(order, "status"))) {
            db.updateIntentFailed(intentId, "订单状态已变化", support.nowUtc());
            return;
        }
        LocalDateTime now = support.nowUtc();
        LocalDateTime payDeadline = Row.dt(order, "pay_deadline");
        if (payDeadline != null && !payDeadline.isAfter(now)) {
            db.updateIntentFailed(intentId, "已超支付截止时间", now);
            return;
        }
        var batch = db.batchById(Row.str(order, "batch_id"));
        if (batch == null || Row.i(batch, "reserved") <= 0) {
            db.updateIntentFailed(intentId, "预留库存已释放", now);
            return;
        }
        // 优惠券仍然可用（可选校验）
        String couponId = Row.str(intent, "coupon_id");
        if (couponId != null && !couponId.isEmpty()) {
            var coupon = db.lockCoupon(couponId, Row.str(intent, "user_id"));
            if (coupon == null) {
                db.updateIntentFailed(intentId, "优惠券不可用", now);
                return;
            }
        }
        // 预留 → 成交
        if (db.reservedToSold(Row.str(batch, "id")) != 1) {
            throw new IllegalStateException("预留库存已变化");
        }

        String code = support.makePickupCode();
        String qrToken = support.randToken(24);
        String channelTx = "ch_" + support.randToken(14);
        int paidAmount = Money.cents(intent.get("amount"));
        int original = Money.cents(order.get("amount"));
        int discount = original - paidAmount;

        db.updateOrderPaid(Row.str(order, "id"), now, code, qrToken, channelTx,
                paidAmount, discount > 0 ? original : null,
                discount > 0 ? discount : null, couponId);
        if (couponId != null && !couponId.isEmpty()) {
            db.useCoupon(couponId, now, Row.str(order, "id"));
        }
        db.updateIntentPaid(intentId, paidAmount, now);

        support.log("支付渠道", "确认支付结果",
                "订单 " + Row.str(order, "no") + " 到账 ¥" + Money.yuan(paidAmount).toPlainString()
                        + (discount > 0 ? "（已用优惠券）" : ""));
        int gained = support.earnPoints(Row.str(order, "user_id"), paidAmount);
        db.updateOrderPoints(Row.str(order, "id"), gained);

        var snap = Json.parseObject(Row.str(order, "snap"));
        String pickupStart = Row.iso(snap, "pickupStart");
        String pickupEnd = Row.iso(snap, "pickupEnd");
        support.notify(Row.str(order, "user_id"), "支付成功",
                Row.str(snap, "storeName") + " · 取货码 " + code + "，请在 "
                        + pickupStart + "–" + pickupEnd + " 到店自提"
                        + (gained > 0 ? " · 获得 " + gained + " 惜食积分" : ""),
                "/pickup/" + Row.str(order, "id"), "success");
    }

    /**
     * 后台任务调用：pending → failed。
     */
    @Transactional
    public void failPayment(String intentId, String reason) {
        var intent = db.intentById(intentId);
        if (intent == null || !"pending".equals(Row.str(intent, "status"))) return;
        db.updateIntentFailed(intentId, reason, support.nowUtc());
    }

    /** 每秒扫描：到期支付自动确认 + 到期退款自动到账。 */
    public void settleDue(long payDelayMs, long refundDelayMs) {
        var now = support.nowUtc();
        var payCutoff = now.minusNanos(payDelayMs * 1_000_000);
        for (var intent : db.duePendingIntents(payCutoff)) {
            try {
                transactions.executeWithoutResult(status -> confirmPayment(Row.str(intent, "id")));
            } catch (Exception e) {
                log.warn("支付或退款回调失败，将在下次扫描重试", e);
            }
        }
        var refundCutoff = now.minusNanos(refundDelayMs * 1_000_000);
        for (var refund : db.dueProcessingRefunds(refundCutoff)) {
            try {
                transactions.executeWithoutResult(status -> refundService.confirmRefund(Row.str(refund, "id")));
            } catch (Exception e) {
                log.warn("支付或退款回调失败，将在下次扫描重试", e);
            }
        }
    }

    /** 每秒扫描：支付超时订单自动关闭并释放预留。 */
    @Transactional
    public void scanTimeouts() {
        var now = support.nowUtc();
        for (var order : db.pendingPaymentOrders()) {
            var deadline = Row.dt(order, "pay_deadline");
            if (deadline == null || deadline.isAfter(now)) continue;
            String orderId = Row.str(order, "id");
            if (db.updateOrderStatusIf(orderId, "pending_payment", "closed") != 1) continue;
            String batchId = Row.str(order, "batch_id");
            if (batchId != null) db.releaseReserved(batchId);
            db.failPendingIntentsByOrder(orderId, "支付超时", now);
        }
        for (var order : db.toPickupOrders()) {
            var snap = Json.parseObject(Row.str(order, "snap"));
            var end = support.parseTime(snap.get("pickupEnd"));
            if (end != null && !end.isAfter(now)) {
                db.updateOrderStatusIf(Row.str(order, "id"), "to_pickup", "no_show");
            }
        }
    }
}
