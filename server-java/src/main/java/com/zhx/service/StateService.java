package com.zhx.service;

import com.zhx.domain.AuthUser;
import com.zhx.domain.Constants;
import com.zhx.dto.Row;
import com.zhx.dto.StateMapper;
import com.zhx.repository.DbRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StateService {
    private final DbRepository db;
    private final ServiceSupport support;

    public StateService(DbRepository db, ServiceSupport support) {
        this.db = db;
        this.support = support;
    }

    public Map<String, Object> build(AuthUser me) {
        List<Map<String, Object>> stores = db.allStores();
        List<Map<String, Object>> batches = db.allBatches();
        Set<String> approvedStoreIds = new LinkedHashSet<>();
        for (Map<String, Object> store : stores) {
            if ("approved".equals(Row.str(store, "audit_status"))) approvedStoreIds.add(Row.str(store, "id"));
        }
        List<Map<String, Object>> myStores = new ArrayList<>();
        if (me != null) {
            for (Map<String, Object> store : stores) {
                if (me.id().equals(Row.str(store, "owner_id"))) myStores.add(store);
            }
        }
        String ownStoreId = myStores.isEmpty() ? null : Row.str(myStores.get(0), "id");

        List<Map<String, Object>> storeRows = stores;
        List<Map<String, Object>> batchRows = batches;
        List<Map<String, Object>> orderRows = new ArrayList<>();
        List<Map<String, Object>> refundRows = new ArrayList<>();
        List<Map<String, Object>> aftersaleRows = new ArrayList<>();
        List<Map<String, Object>> reviewRows = new ArrayList<>();
        List<Map<String, Object>> draftRows = new ArrayList<>();
        List<Map<String, Object>> applicationRows = new ArrayList<>();
        List<Map<String, Object>> accountRows = new ArrayList<>();
        List<Map<String, Object>> logRows = new ArrayList<>();
        List<Map<String, Object>> noticeRows = new ArrayList<>();

        if (me == null) {
            storeRows = filterStores(stores, true);
            batchRows = filterBatches(batches, approvedStoreIds);
            reviewRows = db.allReviews();
        } else if (me.isAdmin()) {
            orderRows = db.allOrders();
            refundRows = db.allRefunds();
            aftersaleRows = db.allAftersales();
            reviewRows = db.allReviews();
            draftRows = allDrafts();
            applicationRows = db.allApplications();
            accountRows = db.allAccounts();
            logRows = db.latestLogs();
        } else if (me.isMerchant()) {
            storeRows = myStores;
            batchRows = ownStoreId == null ? new ArrayList<>() : db.batchesByStore(ownStoreId);
            orderRows = ownStoreId == null ? new ArrayList<>() : db.ordersByStore(ownStoreId);
            Set<String> orderIds = orderIds(orderRows);
            refundRows = filterByOrder(db.allRefunds(), orderIds);
            aftersaleRows = filterByOrder(db.allAftersales(), orderIds);
            reviewRows = ownStoreId == null ? new ArrayList<>() : db.reviewsByStore(ownStoreId);
            draftRows = ownStoreId == null ? new ArrayList<>() : db.draftsByStore(ownStoreId);
            applicationRows = db.applicationsByAccount(me.id());
        } else {
            storeRows = filterStores(stores, true);
            batchRows = filterBatches(batches, approvedStoreIds);
            orderRows = db.ordersByUser(me.id());
            Set<String> orderIds = orderIds(orderRows);
            refundRows = filterByOrder(db.allRefunds(), orderIds);
            aftersaleRows = filterByOrder(db.allAftersales(), orderIds);
            reviewRows = db.allReviews();
        }

        Map<String, Object> favorites = new LinkedHashMap<>();
        Map<String, Object> wallets = new LinkedHashMap<>();
        if (me != null) {
            List<Map<String, Object>> favs = db.favoritesByUser(me.id());
            List<String> storeIds = new ArrayList<>();
            for (Map<String, Object> fav : favs) storeIds.add(Row.str(fav, "store_id"));
            favorites.put(me.id(), storeIds);

            Map<String, Object> wallet = db.walletByUser(me.id());
            List<Map<String, Object>> coupons = db.couponsByUser(me.id());
            List<Map<String, Object>> couponOut = new ArrayList<>();
            for (Map<String, Object> coupon : coupons) couponOut.add(StateMapper.coupon(coupon));
            Map<String, Object> walletOut = new LinkedHashMap<>();
            walletOut.put("points", wallet == null ? 0 : Row.i(wallet, "points"));
            walletOut.put("coupons", couponOut);
            wallets.put(me.id(), walletOut);
            noticeRows = db.noticesByUser(me.id());
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("clock", support.isoNow());
        Map<String, Object> session = new LinkedHashMap<>();
        if (me != null) {
            session.put("loggedIn", true);
            session.put("role", me.role());
            session.put("userId", me.id());
            session.put("name", me.name());
            session.put("account", me.account());
            session.put("phone", me.phone());
        } else {
            session.put("loggedIn", false);
            session.put("role", null);
            session.put("userId", null);
            session.put("name", "");
            session.put("phone", "");
        }
        state.put("session", session);
        Map<String, Object> merchant = new LinkedHashMap<>();
        merchant.put("accountId", me != null && me.isMerchant() ? me.id() : "m1");
        state.put("merchant", merchant);
        state.put("browseCenter", me == null
                ? Constants.DEFAULT_CENTER
                : support.getSetting("center:" + me.id(), Constants.DEFAULT_CENTER));
        state.put("locationDenied", false);
        state.put("categories", Constants.CATEGORIES);
        state.put("stores", mapList(storeRows, StateMapper::store));
        state.put("batches", mapList(batchRows, StateMapper::batch));
        state.put("orders", mapList(orderRows, StateMapper::order));
        state.put("reviews", mapList(reviewRows, StateMapper::review));
        state.put("favorites", favorites);
        state.put("notices", mapList(noticeRows, StateMapper::notice));
        state.put("wallet", wallets);
        state.put("refunds", mapList(refundRows, StateMapper::refund));
        state.put("aftersales", mapList(aftersaleRows, StateMapper::aftersale));
        state.put("drafts", mapList(draftRows, StateMapper::draft));
        state.put("applications", mapList(applicationRows, StateMapper::application));
        state.put("accounts", me != null && !me.isAdmin()
                ? List.of(StateMapper.account(me))
                : mapList(accountRows, StateMapper::account));
        state.put("logs", mapList(logRows, StateMapper::log));
        state.put("storageError", null);
        state.put("cacheCorrupted", false);
        return state;
    }

    private List<Map<String, Object>> allDrafts() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> store : db.allStores()) {
            out.addAll(db.draftsByStore(Row.str(store, "id")));
        }
        return out;
    }

    private static List<Map<String, Object>> filterStores(List<Map<String, Object>> stores, boolean approvedOnly) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> store : stores) {
            if (!approvedOnly || "approved".equals(Row.str(store, "audit_status"))) out.add(store);
        }
        return out;
    }

    private static List<Map<String, Object>> filterBatches(List<Map<String, Object>> batches, Set<String> storeIds) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> batch : batches) {
            if (storeIds.contains(Row.str(batch, "store_id"))) out.add(batch);
        }
        return out;
    }

    private static Set<String> orderIds(List<Map<String, Object>> orders) {
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> order : orders) ids.add(Row.str(order, "id"));
        return ids;
    }

    private static List<Map<String, Object>> filterByOrder(List<Map<String, Object>> rows, Set<String> orderIds) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (orderIds.contains(Row.str(row, "order_id"))) out.add(row);
        }
        return out;
    }

    private static List<Map<String, Object>> mapList(List<Map<String, Object>> rows,
                                                     java.util.function.Function<Map<String, Object>, Map<String, Object>> mapper) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) out.add(mapper.apply(row));
        return out;
    }
}
