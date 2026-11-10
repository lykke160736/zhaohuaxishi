package com.zhx.service;

import com.zhx.domain.AuthUser;
import com.zhx.domain.Constants;
import com.zhx.dto.Money;
import com.zhx.dto.Row;
import com.zhx.dto.StateMapper;
import com.zhx.exception.HttpError;
import com.zhx.repository.DbRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OrderService {
    private final DbRepository db;
    private final ServiceSupport support;
    private final RefundService refunds;

    public OrderService(DbRepository db, ServiceSupport support, RefundService refunds) {
        this.db = db;
        this.support = support;
        this.refunds = refunds;
    }

    @Transactional
    public Map<String, Object> place(AuthUser me, Map<String, Object> body) {
        support.assertRole(me, "customer");
        String batchId = String.valueOf(body.getOrDefault("batchId", ""));
        Map<String, Object> batch = db.batchById(batchId);
        if (batch == null) throw HttpError.bad("批次不存在");
        Map<String, Object> store = support.storeOf(Row.str(batch, "store_id"));
        if (store == null || !"approved".equals(Row.str(store, "audit_status"))
                || !"active".equals(Row.str(store, "operation_status"))) {
            throw HttpError.bad("门店暂停经营，暂不可下单");
        }
        if (!"on_sale".equals(support.batchStatus(batch))) throw HttpError.bad("该批次已停售或已截止");
        int changed = db.reserveBatch(Row.str(batch, "id"));
        if (changed == 0) throw HttpError.bad("库存不足，请刷新后重试");

        LocalDateTime pickupEnd = Row.dt(batch, "pickup_end");
        LocalDateTime payDeadline = support.nowUtc().plusMinutes(Constants.PAY_MINUTES);
        if (pickupEnd != null && pickupEnd.isBefore(payDeadline)) payDeadline = pickupEnd;
        String id = support.uid("O");
        String no = support.newOrderNo();
        int price = Money.cents(batch.get("price"));
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("storeName", Row.str(store, "name"));
        snap.put("category", Row.str(batch, "category"));
        snap.put("price", price);
        snap.put("address", Row.str(store, "address"));
        snap.put("pickupStart", Row.iso(batch, "pickup_start"));
        snap.put("pickupEnd", Row.iso(batch, "pickup_end"));
        db.insertOrder(id, no, me.id(), batchId, Row.str(batch, "store_id"), price, "pending_payment",
                support.nowUtc(), payDeadline, support.uid("K"), com.zhx.dto.Json.write(snap));
        support.log("顾客", "提交盲盒订单", "订单 " + no + "，预留库存 1 份");
        support.notify(Row.str(store, "owner_id"), "收到新订单",
                "订单 " + no + " · " + Row.str(batch, "category") + "盲盒 ¥" + moneyText(price) + "，等待顾客支付",
                "/merchant/orders", "order");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("order", StateMapper.order(db.orderById(id)));
        return out;
    }

    @Transactional
    public Map<String, Object> cancel(AuthUser me, String id, Map<String, Object> body) {
        support.assertRole(me, "customer");
        Map<String, Object> order = db.lockOrderForUser(id, me.id());
        if (order == null) throw HttpError.bad("订单不存在或无权访问");
        if (!"pending_payment".equals(Row.str(order, "status"))) throw HttpError.bad("订单状态已变化");
        db.updateOrderStatus(id, "closed");
        db.failPendingIntentsByOrder(id, "顾客取消", support.nowUtc());
        Map<String, Object> batch = db.batchById(Row.str(order, "batch_id"));
        if (batch != null && Row.i(batch, "reserved") > 0) {
            db.releaseReserved(Row.str(batch, "id"));
            support.log("系统", "释放到期库存", "订单 " + Row.str(order, "no") + " 顾客主动取消，预留回补 1 份");
        }
        return new LinkedHashMap<>();
    }

    @Transactional
    public Map<String, Object> pay(AuthUser me, String id, Map<String, Object> body) {
        support.assertRole(me, "customer");
        Map<String, Object> order = db.lockOrderForUser(id, me.id());
        if (order == null) throw HttpError.bad("订单不存在或无权访问");
        if (!"pending_payment".equals(Row.str(order, "status"))) throw HttpError.bad("订单已关闭或已支付");
        LocalDateTime deadline = Row.dt(order, "pay_deadline");
        if (deadline != null && support.nowUtc().isAfter(deadline)) throw HttpError.bad("已超支付截止时间");

        String couponId = body.get("couponId") == null ? "" : String.valueOf(body.get("couponId"));
        Map<String, Object> coupon = null;
        if (!couponId.isEmpty()) {
            coupon = db.usableCoupon(couponId, me.id());
            if (coupon == null) throw HttpError.bad("优惠券不可用");
            int minSpend = Money.cents(coupon.get("min_spend"));
            int orderAmount = Money.cents(order.get("amount"));
            if (minSpend > 0 && orderAmount < minSpend) throw HttpError.bad("未达优惠券使用门槛");
            if (orderAmount <= Money.cents(coupon.get("amount"))) throw HttpError.bad("优惠券金额需小于订单金额");
        }
        Map<String, Object> existing = db.pendingIntentByOrder(id);
        Map<String, Object> intent = existing;
        if (existing == null) {
            int amount = Money.cents(order.get("amount"));
            if (coupon != null) amount -= Money.cents(coupon.get("amount"));
            String intentId = support.uid("P");
            db.insertIntent(intentId, id, me.id(), amount, coupon == null ? null : couponId,
                    "alipay".equals(body.get("channel")) ? "alipay" : "wechat", "pending", support.nowUtc());
            support.log("顾客", "发起支付", "订单 " + Row.str(order, "no") + " 待支付 " + moneyText(amount) + " 元，等待渠道回调");
            intent = db.intentById(intentId);
        }
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("id", Row.str(intent, "id"));
        payment.put("amount", Money.cents(intent.get("amount")));
        payment.put("status", Row.str(intent, "status"));
        payment.put("channel", Row.str(intent, "channel"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("payment", payment);
        return out;
    }

    public Map<String, Object> paymentStatus(AuthUser me, String id) {
        if (me == null) throw HttpError.unauthorized("请先登录");
        Map<String, Object> intent = db.intentById(id);
        if (intent == null || (!me.id().equals(Row.str(intent, "user_id")) && !me.isAdmin())) {
            throw HttpError.bad("支付单不存在");
        }
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("id", Row.str(intent, "id"));
        payment.put("orderId", Row.str(intent, "order_id"));
        payment.put("amount", Money.cents(intent.get("amount")));
        payment.put("status", Row.str(intent, "status"));
        payment.put("channel", Row.str(intent, "channel"));
        payment.put("failureReason", Row.str(intent, "failure_reason"));
        payment.put("confirmedAt", Row.iso(intent, "confirmed_at"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("payment", payment);
        return out;
    }

    @Transactional
    public Map<String, Object> applyRefund(AuthUser me, String id) {
        support.assertRole(me, "customer");
        Map<String, Object> order = db.lockOrderForUser(id, me.id());
        if (order == null) throw HttpError.bad("订单不存在或无权访问");
        if (!"to_pickup".equals(Row.str(order, "status"))) throw HttpError.bad("当前状态不可申请普通退款");
        LocalDateTime paidAt = Row.dt(order, "paid_at");
        if (paidAt != null) {
            long paidMillis = support.now() - paidAt.toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
            if (paidMillis > 15 * 60000L) throw HttpError.bad("付款已超过 15 分钟，无法普通退款");
        }
        Map<String, Object> snap = com.zhx.dto.Json.parseObject(Row.str(order, "snap"));
        LocalDateTime pickupEnd = support.parseTime(snap.get("pickupEnd"));
        if (pickupEnd != null && !support.nowUtc().isBefore(pickupEnd.minusHours(2))) {
            throw HttpError.bad("已临近取货截止时间（2 小时内），无法普通退款");
        }
        if (db.refundExistsByOrder(id)) throw HttpError.bad("退款已在处理中，请勿重复提交");
        Map<String, Object> batch = db.batchById(Row.str(order, "batch_id"));
        boolean sellable = batch != null && "on_sale".equals(Row.str(batch, "status"))
                && support.nowUtc().isBefore(Row.dt(batch, "pickup_end"));
        refunds.createRefund(Row.str(order, "id"), "normal", sellable, "顾客普通退款");
        support.log("顾客", "申请普通退款", "订单 " + Row.str(order, "no") + " 退款受理，等待渠道确认");
        Map<String, Object> store = support.storeOf(Row.str(order, "store_id"));
        support.notify(store == null ? null : Row.str(store, "owner_id"), "顾客申请退款",
                "订单 " + Row.str(order, "no") + " · ¥" + moneyText(Money.cents(order.get("amount")))
                        + "，等待渠道确认到账", "/merchant/orders", "refund");
        return new LinkedHashMap<>();
    }

    @Transactional
    public Map<String, Object> review(AuthUser me, String id, Map<String, Object> body) {
        support.assertRole(me, "customer");
        Object ratingValue = body.get("rating");
        int rating = ratingValue instanceof Number n ? n.intValue() : -1;
        if (rating < 1 || rating > 5) throw HttpError.bad("请选择星级");
        Map<String, Object> order = db.lockOrderForUser(id, me.id());
        if (order == null) throw HttpError.bad("订单不存在");
        if (!"picked".equals(Row.str(order, "status"))) throw HttpError.bad("领取后才能评价");
        if (db.reviewExistsByOrder(id)) throw HttpError.bad("该订单已评价");
        String text = body.get("text") == null ? "" : String.valueOf(body.get("text")).trim();
        db.insertReview(support.uid("RV"), id, Row.str(order, "store_id"), me.id(), me.name(), rating, text, support.nowUtc());
        support.log("顾客", "评价订单", "订单 " + Row.str(order, "no") + "：" + rating + " 星");
        return new LinkedHashMap<>();
    }

    static String moneyText(int cents) {
        return java.math.BigDecimal.valueOf(cents, 2).toPlainString();
    }
}
