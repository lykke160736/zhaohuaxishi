package com.zhx.repository;

import com.zhx.domain.AuthUser;
import com.zhx.dto.Money;
import com.zhx.dto.Row;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class DbRepository {
    private final JdbcTemplate jdbc;

    public DbRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private List<Map<String, Object>> list(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = list(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private AuthUser toUser(Map<String, Object> row) {
        if (row == null) return null;
        return new AuthUser(
                Row.str(row, "id"), Row.str(row, "name"), Row.str(row, "account"),
                Row.str(row, "mobile", ""), Row.str(row, "phone", ""),
                Row.str(row, "role"), Row.str(row, "status"), Row.dt(row, "created_at"));
    }

    // ---------------------------------------------------------------- accounts
    public AuthUser accountById(String id) {
        return toUser(one("SELECT * FROM `accounts` WHERE `id` = ?", id));
    }

    public AuthUser accountByAccountOrMobile(String key) {
        return toUser(one("SELECT * FROM `accounts` WHERE `account` = ? OR `mobile` = ?", key, key));
    }

    public String passwordHashById(String id) {
        Map<String, Object> row = one("SELECT `password_hash` FROM `accounts` WHERE `id` = ?", id);
        return row == null ? null : Row.str(row, "password_hash");
    }

    public boolean accountExists(String account) {
        return !list("SELECT 1 FROM `accounts` WHERE `account` = ? OR (`mobile` <> '' AND `mobile` = ?)",
                account, account).isEmpty();
    }

    public void insertAccount(String id, String name, String account, String mobile, String phone,
                              String passwordHash, String role, String status, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO `accounts` (`id`,`name`,`account`,`mobile`,`phone`,`password_hash`,`role`,`status`,`created_at`) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                id, name, account, mobile, phone, passwordHash, role, status, createdAt);
    }

    public List<Map<String, Object>> allAccounts() {
        return list("SELECT * FROM `accounts`");
    }

    public void updateAccountRole(String id) {
        jdbc.update("UPDATE `accounts` SET `role` = 'merchant' WHERE `id` = ?", id);
    }

    public void updateAccountStatus(String id, String status) {
        jdbc.update("UPDATE `accounts` SET `status` = ? WHERE `id` = ?", status, id);
    }

    public void insertApplication(String id, String storeName, String category, String applicant, String accountId,
                                  String address, BigDecimal lat, BigDecimal lng, String phone, String materials,
                                  LocalDateTime submittedAt, String status) {
        jdbc.update("INSERT INTO `applications` (`id`,`store_name`,`category`,`applicant`,`account_id`,`address`,`lat`,`lng`,`phone`,`materials`,`submitted_at`,`status`) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id, storeName, category, applicant, accountId, address, lat, lng, phone, materials, submittedAt, status);
    }

    // ----------------------------------------------------------------- stores
    public List<Map<String, Object>> allStores() {
        return list("SELECT * FROM `stores`");
    }

    public Map<String, Object> storeById(String id) {
        return one("SELECT * FROM `stores` WHERE `id` = ?", id);
    }

    public Map<String, Object> storeByOwner(String ownerId) {
        return one("SELECT * FROM `stores` WHERE `owner_id` = ?", ownerId);
    }

    public List<Map<String, Object>> dailyStores() {
        return list("SELECT s.* FROM `stores` s JOIN `accounts` a ON a.`id` = s.`owner_id` "
                + "WHERE s.`audit_status` = 'approved' AND s.`operation_status` = 'active' AND a.`status` = 'active'");
    }

    public void insertStore(String id, String name, String category, String address, BigDecimal lat, BigDecimal lng,
                            String phone, String notice, String ownerId, String auditStatus) {
        jdbc.update("INSERT INTO `stores` (`id`,`name`,`category`,`address`,`lat`,`lng`,`phone`,`notice`,`owner_id`,`audit_status`) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                id, name, category, address, lat, lng, phone, notice, ownerId, auditStatus);
    }

    public int updateStoreOperation(String id, String status, String reason, LocalDateTime at) {
        return jdbc.update("UPDATE `stores` SET `operation_status`=?, `operation_reason`=?, `operation_at`=? WHERE `id`=?",
                status, reason, at, id);
    }

    public void updateStoreProfile(String id, String name, String category, String address, String phone, String notice) {
        jdbc.update("UPDATE `stores` SET `name`=?, `category`=?, `address`=?, `phone`=?, `notice`=? WHERE `id`=?",
                name, category, address, phone, notice, id);
    }

    public long storeCount() {
        Map<String, Object> row = one("SELECT COUNT(*) AS n FROM `stores`");
        return row == null ? 0 : Row.l(row, "n");
    }

    // ---------------------------------------------------------------- batches
    public List<Map<String, Object>> allBatches() {
        return list("SELECT * FROM `batches` WHERE `status` <> 'archived'");
    }

    public Map<String, Object> batchById(String id) {
        return one("SELECT * FROM `batches` WHERE `id` = ?", id);
    }

    public Map<String, Object> batchByIdForStore(String id, String storeId) {
        return one("SELECT * FROM `batches` WHERE `id` = ? AND `store_id` = ? AND `status` <> 'archived'", id, storeId);
    }

    public List<Map<String, Object>> batchesByStore(String storeId) {
        return list("SELECT * FROM `batches` WHERE `store_id` = ? AND `status` <> 'archived'", storeId);
    }

    public List<Map<String, Object>> onSaleBatches() {
        return list("SELECT * FROM `batches` WHERE `status` = 'on_sale'");
    }

    public void insertBatch(String id, String no, String storeId, String category, int priceCents,
                            LocalDateTime pickupStart, LocalDateTime pickupEnd, int total, String status,
                            LocalDateTime publishedAt, String stockLogs) {
        jdbc.update("INSERT INTO `batches` (`id`,`no`,`store_id`,`category`,`price`,`pickup_start`,`pickup_end`,`total`,`sold`,`reserved`,`status`,`published_at`,`stock_logs`) "
                        + "VALUES (?,?,?,?,?,?,?,?,0,0,?,?,?)",
                id, no, storeId, category, Money.yuan(priceCents), pickupStart, pickupEnd, total, status,
                publishedAt, stockLogs);
    }

    public int reserveBatch(String id) {
        return jdbc.update("UPDATE `batches` SET `reserved` = `reserved` + 1 "
                + "WHERE `id` = ? AND `total` - `sold` - `reserved` >= 1", id);
    }

    public int releaseReserved(String id) {
        return jdbc.update("UPDATE `batches` SET `reserved` = `reserved` - 1 WHERE `id` = ? AND `reserved` > 0", id);
    }

    public int reservedToSold(String id) {
        return jdbc.update("UPDATE `batches` SET `reserved` = `reserved` - 1, `sold` = `sold` + 1 "
                + "WHERE `id` = ? AND `reserved` > 0", id);
    }

    public int reduceSold(String id) {
        return jdbc.update("UPDATE `batches` SET `sold` = CASE WHEN `sold` > 0 THEN `sold` - 1 ELSE 0 END WHERE `id` = ?", id);
    }

    public void updateBatchTotalAndLogs(String id, int total, String stockLogs) {
        jdbc.update("UPDATE `batches` SET `total` = ?, `stock_logs` = ? WHERE `id` = ?", total, stockLogs, id);
    }

    public int stopBatch(String id, String reason, LocalDateTime at) {
        return jdbc.update("UPDATE `batches` SET `status` = 'stopped', `stop_reason` = ?, `stopped_at` = ? WHERE `id` = ?",
                reason, at, id);
    }

    public int stopBatchIfOnSale(String id, String reason, LocalDateTime at) {
        return jdbc.update("UPDATE `batches` SET `status` = 'stopped', `stop_reason` = ?, `stopped_at` = ? "
                + "WHERE `id` = ? AND `status` = 'on_sale'", reason, at, id);
    }

    public void updateBatchWindow(String id, LocalDateTime start, LocalDateTime end) {
        jdbc.update("UPDATE `batches` SET `pickup_start` = ?, `pickup_end` = ? WHERE `id` = ?", start, end, id);
    }

    public int markBatchPublished(String draftId) {
        return jdbc.update("UPDATE `drafts` SET `published` = 1 WHERE `id` = ? AND `published` = 0", draftId);
    }

    public Map<String, Object> lockIntent(String id) {
        return one("SELECT * FROM `payment_intents` WHERE `id` = ? FOR UPDATE", id);
    }

    public Map<String, Object> lockRefund(String id) {
        return one("SELECT * FROM `refunds` WHERE `id` = ? FOR UPDATE", id);
    }

    public Map<String, Object> lockAftersale(String id) {
        return one("SELECT * FROM `aftersales` WHERE `id` = ? FOR UPDATE", id);
    }

    public Map<String, Object> lockOrder(String id) {
        return one("SELECT * FROM `orders` WHERE `id` = ? FOR UPDATE", id);
    }

    public Map<String, Object> lockOrderForUser(String id, String userId) {
        return one("SELECT * FROM `orders` WHERE `id` = ? AND `user_id` = ? FOR UPDATE", id, userId);
    }

    public Map<String, Object> lockOrderForStore(String id, String storeId) {
        return one("SELECT * FROM `orders` WHERE `id` = ? AND `store_id` = ? FOR UPDATE", id, storeId);
    }

    public Map<String, Object> lockOrderByCode(String code) {
        return one("SELECT * FROM `orders` WHERE `pickup_code` = ? FOR UPDATE", code);
    }

    public Map<String, Object> lockCoupon(String id, String userId) {
        return one("SELECT * FROM `coupons` WHERE `id` = ? AND `user_id` = ? AND `used` = 0 FOR UPDATE", id, userId);
    }

    // ------------------------------------------------------------------ orders
    public List<Map<String, Object>> allOrders() {
        return list("SELECT * FROM `orders`");
    }

    public List<Map<String, Object>> ordersByStore(String storeId) {
        return list("SELECT * FROM `orders` WHERE `store_id` = ?", storeId);
    }

    public List<Map<String, Object>> ordersByUser(String userId) {
        return list("SELECT * FROM `orders` WHERE `user_id` = ?", userId);
    }

    public Map<String, Object> orderById(String id) {
        return one("SELECT * FROM `orders` WHERE `id` = ?", id);
    }

    public Map<String, Object> orderByIdAndUser(String id, String userId) {
        return one("SELECT * FROM `orders` WHERE `id` = ? AND `user_id` = ?", id, userId);
    }

    public Map<String, Object> orderByIdForStore(String id, String storeId) {
        return one("SELECT * FROM `orders` WHERE `id` = ? AND `store_id` = ?", id, storeId);
    }

    public Map<String, Object> orderByPickupCode(String code) {
        return one("SELECT * FROM `orders` WHERE `pickup_code` = ?", code);
    }

    public boolean pickupCodeExists(String code) {
        return !list("SELECT 1 FROM `orders` WHERE `pickup_code` = ?", code).isEmpty();
    }

    public long orderCount() {
        Map<String, Object> row = one("SELECT COUNT(*) AS n FROM `orders`");
        return row == null ? 0 : Row.l(row, "n");
    }

    public void insertOrder(String id, String no, String userId, String batchId, String storeId, int amountCents,
                            String status, LocalDateTime createdAt, LocalDateTime payDeadline, String requestKey, String snap) {
        jdbc.update("INSERT INTO `orders` (`id`,`no`,`user_id`,`batch_id`,`store_id`,`amount`,`status`,`created_at`,`pay_deadline`,`request_key`,`snap`) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id, no, userId, batchId, storeId, Money.yuan(amountCents), status, createdAt, payDeadline, requestKey, snap);
    }

    public int updateOrderStatus(String id, String status) {
        return jdbc.update("UPDATE `orders` SET `status` = ? WHERE `id` = ?", status, id);
    }

    public int updateOrderStatusIf(String id, String from, String to) {
        return jdbc.update("UPDATE `orders` SET `status` = ? WHERE `id` = ? AND `status` = ?", to, id, from);
    }

    public void updateOrderPaid(String id, LocalDateTime paidAt, String pickupCode, String qrToken, String channelTx,
                                int amountCents, Integer originalCents, Integer discountCents, String couponId) {
        jdbc.update("UPDATE `orders` SET `status`='to_pickup', `paid_at`=?, `pickup_code`=?, `qr_token`=?, `channel_tx`=?, "
                        + "`amount`=?, `original_amount`=?, `discount`=?, `coupon_id`=? WHERE `id`=?",
                paidAt, pickupCode, qrToken, channelTx, Money.yuan(amountCents),
                originalCents == null ? null : Money.yuan(originalCents),
                discountCents == null ? null : Money.yuan(discountCents), couponId, id);
    }

    public void updateOrderPoints(String id, int points) {
        jdbc.update("UPDATE `orders` SET `points_gained` = ? WHERE `id` = ?", points, id);
    }

    public void updateOrderPicked(String id, LocalDateTime pickedAt, String operator) {
        jdbc.update("UPDATE `orders` SET `status`='picked', `picked_at`=?, `operator`=? WHERE `id`=?", pickedAt, operator, id);
    }

    public List<Map<String, Object>> pendingPaymentOrders() {
        return list("SELECT * FROM `orders` WHERE `status` = 'pending_payment'");
    }

    public List<Map<String, Object>> toPickupOrders() {
        return list("SELECT * FROM `orders` WHERE `status` = 'to_pickup'");
    }

    // --------------------------------------------------------- payment intents
    public Map<String, Object> intentById(String id) {
        return one("SELECT * FROM `payment_intents` WHERE `id` = ?", id);
    }

    public Map<String, Object> pendingIntentByOrder(String orderId) {
        return one("SELECT * FROM `payment_intents` WHERE `order_id` = ? AND `status` = 'pending'", orderId);
    }

    public void insertIntent(String id, String orderId, String userId, int amountCents, String couponId,
                             String channel, String status, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO `payment_intents` (`id`,`order_id`,`user_id`,`amount`,`coupon_id`,`channel`,`status`,`created_at`) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                id, orderId, userId, Money.yuan(amountCents), couponId, channel, status, createdAt);
    }

    public void updateIntentFailed(String id, String reason, LocalDateTime at) {
        jdbc.update("UPDATE `payment_intents` SET `status`='failed', `confirmed_at`=?, `failure_reason`=? WHERE `id`=? AND `status`='pending'",
                at, reason, id);
    }

    public void updateIntentPaid(String id, int amountCents, LocalDateTime at) {
        jdbc.update("UPDATE `payment_intents` SET `status`='paid', `confirmed_at`=?, `amount`=? WHERE `id`=?",
                at, Money.yuan(amountCents), id);
    }

    public void failPendingIntentsByOrder(String orderId, String reason, LocalDateTime at) {
        jdbc.update("UPDATE `payment_intents` SET `status`='failed', `confirmed_at`=?, `failure_reason`=? "
                + "WHERE `order_id`=? AND `status`='pending'", at, reason, orderId);
    }

    public List<Map<String, Object>> duePendingIntents(LocalDateTime cutoff) {
        return list("SELECT `id` FROM `payment_intents` WHERE `status`='pending' AND `created_at` <= ?", cutoff);
    }

    // ----------------------------------------------------------------- refunds
    public List<Map<String, Object>> allRefunds() {
        return list("SELECT * FROM `refunds`");
    }

    public Map<String, Object> refundById(String id) {
        return one("SELECT * FROM `refunds` WHERE `id` = ?", id);
    }

    public boolean refundExistsByOrder(String orderId) {
        return !list("SELECT 1 FROM `refunds` WHERE `order_id` = ?", orderId).isEmpty();
    }

    public void insertRefund(String id, String orderId, String type, int amountCents, String status,
                             boolean restock, String reason, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO `refunds` (`id`,`order_id`,`type`,`amount`,`status`,`restock`,`reason`,`created_at`) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                id, orderId, type, Money.yuan(amountCents), status, restock ? 1 : 0, reason, createdAt);
    }

    public void updateRefundSuccess(String id, LocalDateTime at) {
        jdbc.update("UPDATE `refunds` SET `status`='success', `confirmed_at`=? WHERE `id`=?", at, id);
    }

    public List<Map<String, Object>> dueProcessingRefunds(LocalDateTime cutoff) {
        return list("SELECT `id` FROM `refunds` WHERE `status`='processing' AND `created_at` <= ?", cutoff);
    }

    // -------------------------------------------------------------- aftersales
    public List<Map<String, Object>> allAftersales() {
        return list("SELECT * FROM `aftersales`");
    }

    public List<Map<String, Object>> aftersalesByStore(String storeId) {
        return list("SELECT a.id, a.status, a.reason, a.deadline FROM `aftersales` a "
                + "JOIN `orders` o ON a.`order_id`=o.`id` WHERE o.`store_id`=? ORDER BY a.`created_at` DESC LIMIT 50", storeId);
    }

    public Map<String, Object> aftersaleById(String id) {
        return one("SELECT * FROM `aftersales` WHERE `id` = ?", id);
    }

    public boolean activeAftersaleExists(String orderId) {
        return !list("SELECT 1 FROM `aftersales` WHERE `order_id` = ? AND `status` IN ('merchant_review','platform_review','agreed')",
                orderId).isEmpty();
    }

    public void insertAftersale(String id, String orderId, String reason, String evidence, String status,
                                LocalDateTime createdAt, LocalDateTime deadline) {
        jdbc.update("INSERT INTO `aftersales` (`id`,`order_id`,`reason`,`evidence`,`status`,`created_at`,`deadline`) "
                        + "VALUES (?,?,?,?,?,?,?)", id, orderId, reason, evidence, status, createdAt, deadline);
    }

    public void updateAftersaleStatus(String id, String status) {
        jdbc.update("UPDATE `aftersales` SET `status` = ? WHERE `id` = ?", status, id);
    }

    public void updateAftersaleHandled(String id, String reply, LocalDateTime handledAt, String status) {
        jdbc.update("UPDATE `aftersales` SET `reply`=?, `handled_at`=?, `status`=? WHERE `id`=?",
                reply, handledAt, status, id);
    }

    public void updateAftersaleResolved(String id, String note, LocalDateTime resolvedAt) {
        jdbc.update("UPDATE `aftersales` SET `platform_note`=?, `status`='resolved', `resolved_at`=? WHERE `id`=?",
                note, resolvedAt, id);
    }

    // ----------------------------------------------------------------- reviews
    public List<Map<String, Object>> allReviews() {
        return list("SELECT * FROM `reviews`");
    }

    public List<Map<String, Object>> reviewsByStore(String storeId) {
        return list("SELECT * FROM `reviews` WHERE `store_id` = ?", storeId);
    }

    public boolean reviewExistsByOrder(String orderId) {
        return !list("SELECT 1 FROM `reviews` WHERE `order_id` = ?", orderId).isEmpty();
    }

    public void insertReview(String id, String orderId, String storeId, String userId, String userName,
                             int rating, String text, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO `reviews` (`id`,`order_id`,`store_id`,`user_id`,`user_name`,`rating`,`text`,`created_at`) "
                        + "VALUES (?,?,?,?,?,?,?,?)", id, orderId, storeId, userId, userName, rating, text, createdAt);
    }

    // ---------------------------------------------------------------- favorites
    public List<Map<String, Object>> favoritesByUser(String userId) {
        return list("SELECT `store_id` FROM `favorites` WHERE `user_id` = ?", userId);
    }

    public boolean favoriteExists(String userId, String storeId) {
        return !list("SELECT 1 FROM `favorites` WHERE `user_id`=? AND `store_id`=?", userId, storeId).isEmpty();
    }

    public void deleteFavorite(String userId, String storeId) {
        jdbc.update("DELETE FROM `favorites` WHERE `user_id`=? AND `store_id`=?", userId, storeId);
    }

    public void insertFavorite(String userId, String storeId, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO `favorites` (`user_id`,`store_id`,`created_at`) VALUES (?,?,?)",
                userId, storeId, createdAt);
    }

    // ----------------------------------------------------------------- wallets
    public Map<String, Object> walletByUser(String userId) {
        return one("SELECT * FROM `wallets` WHERE `user_id` = ?", userId);
    }

    public boolean walletExists(String userId) {
        return !list("SELECT 1 FROM `wallets` WHERE `user_id` = ?", userId).isEmpty();
    }

    public void insertWallet(String userId, int points) {
        jdbc.update("INSERT INTO `wallets` (`user_id`,`points`) VALUES (?,?)", userId, points);
    }

    public void addPoints(String userId, int delta) {
        jdbc.update("UPDATE `wallets` SET `points` = `points` + ? WHERE `user_id` = ?", delta, userId);
    }

    public void subtractPoints(String userId, int delta) {
        jdbc.update("UPDATE `wallets` SET `points` = `points` - ? WHERE `user_id` = ?", delta, userId);
    }

    public int redeemPoints(String userId, int points) {
        return jdbc.update("UPDATE `wallets` SET `points` = `points` - ? WHERE `user_id` = ? AND `points` >= ?",
                points, userId, points);
    }

    // ----------------------------------------------------------------- coupons
    public List<Map<String, Object>> couponsByUser(String userId) {
        return list("SELECT * FROM `coupons` WHERE `user_id` = ? ORDER BY `created_at` DESC", userId);
    }

    public Map<String, Object> usableCoupon(String couponId, String userId) {
        return one("SELECT * FROM `coupons` WHERE `id`=? AND `user_id`=? AND `used`=0", couponId, userId);
    }

    public Map<String, Object> couponById(String id) {
        return one("SELECT * FROM `coupons` WHERE `id` = ?", id);
    }

    public void insertCoupon(String id, String userId, int amountCents, int minSpendCents, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO `coupons` (`id`,`user_id`,`amount`,`min_spend`,`used`,`created_at`) VALUES (?,?,?,?,0,?)",
                id, userId, Money.yuan(amountCents), Money.yuan(minSpendCents), createdAt);
    }

    public void useCoupon(String id, LocalDateTime usedAt, String orderId) {
        jdbc.update("UPDATE `coupons` SET `used`=1, `used_at`=?, `order_id`=? WHERE `id`=?", usedAt, orderId, id);
    }

    // ----------------------------------------------------------------- notices
    public List<Map<String, Object>> noticesByUser(String userId) {
        return list("SELECT * FROM `notices` WHERE `to_user` = ? ORDER BY `created_at` DESC LIMIT 120", userId);
    }

    public void insertNotice(String id, String toUser, String title, String body, String link, String type,
                             LocalDateTime createdAt) {
        jdbc.update("INSERT INTO `notices` (`id`,`to_user`,`title`,`body`,`link`,`type`,`read`,`created_at`) "
                + "VALUES (?,?,?,?,?,?,0,?)", id, toUser, title, body, link, type, createdAt);
    }

    public void trimNotices(String userId) {
        jdbc.update("DELETE FROM `notices` WHERE `to_user` = ? AND `id` NOT IN ("
                + "SELECT `id` FROM (SELECT `id` FROM `notices` WHERE `to_user` = ? ORDER BY `created_at` DESC LIMIT 120) t)",
                userId, userId);
    }

    public void markNoticeRead(String id, String userId) {
        jdbc.update("UPDATE `notices` SET `read`=1 WHERE `id`=? AND `to_user`=?", id, userId);
    }

    public void markAllNoticesRead(String userId) {
        jdbc.update("UPDATE `notices` SET `read`=1 WHERE `to_user`=? AND `read`=0", userId);
    }

    // -------------------------------------------------------------------- logs
    public void insertLog(String id, LocalDateTime time, String actor, String action, String detail) {
        jdbc.update("INSERT INTO `logs` (`id`,`time`,`actor`,`action`,`detail`) VALUES (?,?,?,?,?)",
                id, time, actor, action, detail);
    }

    public List<Map<String, Object>> latestLogs() {
        return list("SELECT * FROM `logs` ORDER BY `time` DESC LIMIT 200");
    }

    // ------------------------------------------------------------------ drafts
    public List<Map<String, Object>> draftsByStore(String storeId) {
        return list("SELECT * FROM `drafts` WHERE `store_id` = ?", storeId);
    }

    public Map<String, Object> draftByIdForStore(String id, String storeId) {
        return one("SELECT * FROM `drafts` WHERE `id`=? AND `store_id`=?", id, storeId);
    }

    public void insertDraft(String id, String storeId, int priceCents, int qty, String category,
                            LocalDateTime start, LocalDateTime end, LocalDateTime updatedAt) {
        jdbc.update("INSERT INTO `drafts` (`id`,`store_id`,`price`,`qty`,`category`,`pickup_start`,`pickup_end`,`published`,`updated_at`) "
                + "VALUES (?,?,?,?,?,?,?,0,?)",
                id, storeId, Money.yuan(priceCents), qty, category, start, end, updatedAt);
    }

    public void updateDraft(String id, int priceCents, int qty, String category, LocalDateTime start,
                            LocalDateTime end, LocalDateTime updatedAt) {
        jdbc.update("UPDATE `drafts` SET `price`=?, `qty`=?, `category`=?, `pickup_start`=?, `pickup_end`=?, `updated_at`=? WHERE `id`=?",
                Money.yuan(priceCents), qty, category, start, end, updatedAt, id);
    }

    // ------------------------------------------------------------ applications
    public List<Map<String, Object>> allApplications() {
        return list("SELECT * FROM `applications`");
    }

    public List<Map<String, Object>> applicationsByAccount(String accountId) {
        return list("SELECT * FROM `applications` WHERE `account_id` = ?", accountId);
    }

    public Map<String, Object> applicationById(String id) {
        return one("SELECT * FROM `applications` WHERE `id` = ?", id);
    }

    public void auditApplication(String id, String status, String note, LocalDateTime auditedAt) {
        jdbc.update("UPDATE `applications` SET `status`=?, `note`=?, `audited_at`=? WHERE `id`=?",
                status, note, auditedAt, id);
    }

    public boolean storeExistsByOwner(String accountId) {
        return !list("SELECT 1 FROM `stores` WHERE `owner_id` = ?", accountId).isEmpty();
    }

    // ------------------------------------------------------------------- ai
    public List<Map<String, Object>> aiBatches(BigDecimal maxPriceYuan) {
        return list("SELECT b.*, s.`name` AS store_name, s.`address`, s.`lat`, s.`lng` FROM `batches` b "
                + "JOIN `stores` s ON s.`id`=b.`store_id` "
                + "WHERE s.`audit_status`='approved' AND s.`operation_status`='active' "
                + "AND b.`status`='on_sale' AND b.`total`-b.`sold`-b.`reserved`>0 AND b.`price`<=?", maxPriceYuan);
    }

    public List<Map<String, Object>> ordersByUserDesc(String userId) {
        return list("SELECT * FROM `orders` WHERE `user_id`=? ORDER BY `created_at` DESC", userId);
    }

    public long pendingApplicationsCount() {
        Map<String, Object> row = one("SELECT COUNT(*) AS n FROM `applications` WHERE `status`='pending'");
        return row == null ? 0 : Row.l(row, "n");
    }

    public List<Map<String, Object>> pendingApplications() {
        return list("SELECT `store_name`, `category`, `submitted_at` FROM `applications` "
                + "WHERE `status`='pending' ORDER BY `submitted_at` LIMIT 50");
    }

    public long platformReviewCount() {
        Map<String, Object> row = one("SELECT COUNT(*) AS n FROM `aftersales` WHERE `status`='platform_review'");
        return row == null ? 0 : Row.l(row, "n");
    }

    public List<Map<String, Object>> platformReviewAftersales() {
        return list("SELECT `id`, `reason`, `status`, `created_at` FROM `aftersales` "
                + "WHERE `status`='platform_review' ORDER BY `created_at` LIMIT 50");
    }

    public List<Map<String, Object>> accountGroupStats() {
        return list("SELECT `role`, `status`, COUNT(*) AS `count` FROM `accounts` GROUP BY `role`, `status`");
    }

    public List<Map<String, Object>> orderGroupStats() {
        return list("SELECT `status`, COUNT(*) AS `count`, SUM(`amount`) AS `amount` FROM `orders` GROUP BY `status`");
    }
}
