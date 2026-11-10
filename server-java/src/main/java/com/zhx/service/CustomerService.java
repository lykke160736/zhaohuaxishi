package com.zhx.service;

import com.zhx.dto.Row;
import com.zhx.exception.HttpError;
import com.zhx.repository.DbRepository;
import com.zhx.security.TokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 顾客侧业务：浏览中心、收藏、积分兑换、通知已读。
 * 复刻 server/services/customer.service.js。
 */
@Service
public class CustomerService {
    private final DbRepository db;
    private final ServiceSupport support;
    private final TokenService tokens;

    public CustomerService(DbRepository db, ServiceSupport support, TokenService tokens) {
        this.db = db;
        this.support = support;
        this.tokens = tokens;
    }

    /**
     * 覆盖 settings center:<uid>
     */
    @Transactional
    public void setBrowseCenter(String userId, Map<String, Object> req) {
        Map<String, Object> place = req == null ? new LinkedHashMap<>() : safeMap(req.get("place"));
        Object latObj = place.get("lat");
        Object lngObj = place.get("lng");
        if (!(latObj instanceof Number) || !(lngObj instanceof Number)) {
            throw HttpError.bad("位置坐标无效");
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", strOrDefault(place.get("name"), "当前位置"));
        value.put("city", strOrDefault(place.get("city"), ""));
        value.put("lat", latObj);
        value.put("lng", lngObj);
        value.put("source", "gps".equals(str(req.get("source"))) ? "gps" : "manual");
        support.setSetting("center:" + userId, value);
        support.log("顾客", "选择浏览地点", "浏览中心切换至「" + value.get("name") + "」(" + value.get("source") + ")");
    }

    /**
     * 主键 (user_id, store_id) 切换。
     */
    @Transactional
    public void toggleFavorite(String userId, String storeId) {
        if (db.favoriteExists(userId, storeId)) {
            db.deleteFavorite(userId, storeId);
        } else {
            db.insertFavorite(userId, storeId, support.nowUtc());
        }
    }

    /**
     * 积分兑换：100 积分 → 500 分（5 元）券。
     * 先读 points → 扣 100 → 插券，事务内。
     */
    @Transactional
    public Map<String, Object> redeemCoupon(String userId) {
        Map<String, Object> wallet = db.walletByUser(userId);
        if (wallet == null || Row.i(wallet, "points") < 100) {
            throw HttpError.bad("积分不足 100，无法兑换");
        }
        int changed = db.redeemPoints(userId, 100);
        if (changed == 0) throw HttpError.bad("积分不足 100，无法兑换");
        String couponId = support.uid("CP");
        db.insertCoupon(couponId, userId, 500, 0, support.nowUtc());
        support.log("顾客", "兑换优惠券", "消耗 100 积分兑换 5 元无门槛券");
        return db.couponById(couponId);
    }

    @Transactional
    public void markNoticeReadAll(String userId) {
        db.markAllNoticesRead(userId);
    }

    @Transactional
    public void markNoticeRead(String userId, String noticeId) {
        db.markNoticeRead(noticeId, userId);
    }

    // ---------------------------------------------------------------- helpers

    private static Map<String, Object> safeMap(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) out.put(String.valueOf(e.getKey()), e.getValue());
            return out;
        }
        return new LinkedHashMap<>();
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String strOrDefault(Object v, String def) {
        String s = str(v).trim();
        return s.isEmpty() ? def : s;
    }
}
