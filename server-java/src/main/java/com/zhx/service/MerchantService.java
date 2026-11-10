package com.zhx.service;

import com.zhx.domain.AuthUser;
import com.zhx.dto.Json;
import com.zhx.dto.Money;
import com.zhx.dto.Row;
import com.zhx.dto.StateMapper;
import com.zhx.exception.HttpError;
import com.zhx.repository.DbRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MerchantService {
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final DbRepository db;
    private final ServiceSupport support;
    private final RefundService refunds;

    public MerchantService(DbRepository db, ServiceSupport support, RefundService refunds) {
        this.db = db;
        this.support = support;
        this.refunds = refunds;
    }

    @Transactional
    public Map<String, Object> saveDraft(AuthUser me, Map<String, Object> body) {
        support.assertRole(me, "merchant");
        double priceNumber = support.numOrNaN(body.get("price"));
        int price = (int) Math.round(priceNumber * 100);
        if (price <= 0) throw HttpError.bad("价格必须大于 0");
        double qtyNumber = support.numOrNaN(body.get("qty"));
        int qty = (int) qtyNumber;
        if (qty <= 0 || qtyNumber != qty) throw HttpError.bad("数量必须为正整数");
        support.checkPickupWindow(body.get("pickupStart"), body.get("pickupEnd"));
        Map<String, Object> store = support.merchantStoreOf(me);
        LocalDateTime start = support.parseTime(body.get("pickupStart"));
        LocalDateTime end = support.parseTime(body.get("pickupEnd"));
        String category = body.get("category") == null ? "" : String.valueOf(body.get("category"));
        String draftId = body.get("id") == null ? "" : String.valueOf(body.get("id"));
        if (!draftId.isEmpty()) {
            Map<String, Object> draft = db.draftByIdForStore(draftId, Row.str(store, "id"));
            if (draft == null || Row.bool(draft, "published")) throw HttpError.bad("已发布内容不可再编辑");
            db.updateDraft(draftId, price, qty, category, start, end, support.nowUtc());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", draftId);
            return out;
        }
        String id = support.uid("D");
        db.insertDraft(id, Row.str(store, "id"), price, qty, category, start, end, support.nowUtc());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        return out;
    }

    @Transactional
    public Map<String, Object> publishBatch(AuthUser me, Map<String, Object> body) {
        Map<String, Object> draft = saveDraft(me, body);
        return publishDraft(me, String.valueOf(draft.get("id")));
    }

    @Transactional
    public Map<String, Object> publishDraft(AuthUser me, String id) {
        support.assertRole(me, "merchant");
        Map<String, Object> store = support.merchantStoreOf(me);
        if (!"active".equals(Row.str(store, "operation_status"))) {
            throw HttpError.bad("门店暂停经营，暂不可发布或追加库存");
        }
        Map<String, Object> draft = db.draftByIdForStore(id, Row.str(store, "id"));
        if (draft == null) throw HttpError.bad("草稿不存在");
        if (Row.bool(draft, "published")) throw HttpError.bad("该草稿已发布，请勿重复操作");
        support.checkPickupWindow(Row.iso(draft, "pickup_start"), Row.iso(draft, "pickup_end"));
        if (db.markBatchPublished(id) != 1) throw HttpError.bad("该草稿已发布，请勿重复操作");
        String batchId = support.uid("B");
        int total = Row.i(draft, "qty");
        String batchNo = "B-" + tail(String.valueOf(support.seq() + 2060), 4);
        List<Map<String, Object>> logs = new java.util.ArrayList<>();
        Map<String, Object> logItem = new LinkedHashMap<>();
        logItem.put("at", support.isoNow());
        logItem.put("delta", total);
        logItem.put("by", "初始发布");
        logItem.put("after", total);
        logs.add(logItem);
        db.insertBatch(batchId, batchNo, Row.str(store, "id"), Row.str(draft, "category"),
                Money.cents(draft.get("price")), Row.dt(draft, "pickup_start"), Row.dt(draft, "pickup_end"),
                total, "on_sale", support.nowUtc(), Json.write(logs));
        support.log("商家", "发布盲盒批次", "批次发布，" + OrderService.moneyText(Money.cents(draft.get("price")))
                + " 元 × " + total + " 份");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("batch", StateMapper.batch(db.batchById(batchId)));
        return out;
    }

    @Transactional
    public Map<String, Object> addStock(AuthUser me, String id, Map<String, Object> body) {
        support.assertRole(me, "merchant");
        double number = support.numOrNaN(body.get("n"));
        int add = (int) number;
        if (add <= 0 || number != add) throw HttpError.bad("追加数量须为正整数");
        Map<String, Object> store = support.merchantStoreOf(me);
        if (!"active".equals(Row.str(store, "operation_status"))) {
            throw HttpError.bad("门店暂停经营，暂不可发布或追加库存");
        }
        Map<String, Object> batch = db.batchByIdForStore(id, Row.str(store, "id"));
        if (batch == null) throw HttpError.bad("批次不存在");
        if (!"on_sale".equals(Row.str(batch, "status")) || !support.nowUtc().isBefore(Row.dt(batch, "pickup_end"))) {
            throw HttpError.bad("已停售或已截止，无法追加");
        }
        int total = Row.i(batch, "total") + add;
        List<Map<String, Object>> logs = Json.parseList(Row.str(batch, "stock_logs"));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("at", support.isoNow());
        item.put("delta", add);
        item.put("by", "商家追加");
        item.put("after", total);
        logs.add(item);
        db.updateBatchTotalAndLogs(id, total, Json.write(logs));
        support.log("商家", "追加批次库存", "批次 " + Row.str(batch, "no") + " +" + add + "，当前总量 " + total);
        return new LinkedHashMap<>();
    }

    @Transactional
    public Map<String, Object> stopBatch(AuthUser me, String id, Map<String, Object> body) {
        support.assertRole(me, "merchant");
        Map<String, Object> store = support.merchantStoreOf(me);
        Map<String, Object> batch = db.batchByIdForStore(id, Row.str(store, "id"));
        if (batch == null || !"on_sale".equals(Row.str(batch, "status"))) throw HttpError.bad("批次已停售或已结束");
        String reason = body.get("reason") == null || String.valueOf(body.get("reason")).isEmpty()
                ? "商家停止售卖" : String.valueOf(body.get("reason"));
        db.stopBatch(id, reason, support.nowUtc());
        support.log("商家", "停止批次售卖", "批次 " + Row.str(batch, "no") + "：" + reason + "（已有订单继续履约）");
        return new LinkedHashMap<>();
    }

    @Transactional
    public Map<String, Object> verify(AuthUser me, Map<String, Object> body) {
        support.assertRole(me, "merchant");
        Map<String, Object> store = support.merchantStoreOf(me);
        String code = String.valueOf(body.getOrDefault("code", "")).trim();
        Map<String, Object> order = db.lockOrderByCode(code);
        if (order == null) throw HttpError.bad("取货码无效，请核对后重试");
        if (!Row.str(store, "id").equals(Row.str(order, "store_id"))) throw HttpError.bad("非本店订单，无法核销");
        if ("picked".equals(Row.str(order, "status"))) {
            throw HttpError.bad("重复核销：该单已于 " + hhmm(Row.dt(order, "picked_at")) + " 领取");
        }
        if (!"to_pickup".equals(Row.str(order, "status"))) {
            throw HttpError.bad("该订单凭证已失效（退款/取消/未支付）");
        }
        Map<String, Object> snap = Json.parseObject(Row.str(order, "snap"));
        LocalDateTime pickupStart = support.parseTime(snap.get("pickupStart"));
        LocalDateTime pickupEnd = support.parseTime(snap.get("pickupEnd"));
        if (pickupStart != null && support.nowUtc().isBefore(pickupStart)) throw HttpError.bad("未到取货开始时间");
        if (pickupEnd != null && !support.nowUtc().isBefore(pickupEnd)) throw HttpError.bad("已超过取货截止时间");
        db.updateOrderPicked(Row.str(order, "id"), support.nowUtc(), Row.str(store, "name"));
        support.log("商家", "到店核销订单", "订单 " + Row.str(order, "no") + " 核销完成");
        support.notify(Row.str(order, "user_id"), "取货成功",
                Row.str(snap, "storeName") + " · 订单 " + Row.str(order, "no") + " 已核销，感谢你的珍惜",
                "/order/" + Row.str(order, "id"), "success");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("order", StateMapper.order(db.orderById(Row.str(order, "id"))));
        return out;
    }

    @Transactional
    public Map<String, Object> merchantCancel(AuthUser me, String id, Map<String, Object> body) {
        support.assertRole(me, "merchant");
        Map<String, Object> store = support.merchantStoreOf(me);
        Map<String, Object> order = db.lockOrderForStore(id, Row.str(store, "id"));
        if (order == null || !"to_pickup".equals(Row.str(order, "status"))) {
            throw HttpError.bad("仅可取消已付款未领取订单");
        }
        Map<String, Object> batch = db.batchById(Row.str(order, "batch_id"));
        if (batch != null && "on_sale".equals(Row.str(batch, "status"))) {
            db.stopBatchIfOnSale(Row.str(batch, "id"), "商家无法履约，暂停售卖", support.nowUtc());
        }
        String reason = body.get("reason") == null || String.valueOf(body.get("reason")).isEmpty()
                ? "未说明" : String.valueOf(body.get("reason"));
        refunds.createRefund(Row.str(order, "id"), "merchant_cancel", false, "商家无法履约：" + reason);
        support.log("商家", "取消无法履约订单", "订单 " + Row.str(order, "no") + "，全额退款跟进中");
        return new LinkedHashMap<>();
    }

    @Transactional
    public Map<String, Object> updateProfile(AuthUser me, Map<String, Object> body) {
        support.assertRole(me, "merchant");
        Map<String, Object> store = support.merchantStoreOf(me);
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isEmpty()) throw HttpError.bad("门店名称不能为空");
        String category = body.get("category") == null ? Row.str(store, "category") : String.valueOf(body.get("category"));
        String address = body.get("address") == null ? "" : String.valueOf(body.get("address")).trim();
        String phone = body.get("phone") == null ? "" : String.valueOf(body.get("phone")).trim();
        String notice = body.get("notice") == null ? "" : String.valueOf(body.get("notice")).trim();
        db.updateStoreProfile(Row.str(store, "id"), name, category, address, phone, notice);
        support.log("商家", "维护门店资料", "「" + name + "」资料已更新");
        return new LinkedHashMap<>();
    }

    private String hhmm(LocalDateTime utc) {
        if (utc == null) return "";
        return HHMM.format(utc.atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault()));
    }

    private static String tail(String text, int length) {
        return text.length() <= length ? text : text.substring(text.length() - length);
    }
}
