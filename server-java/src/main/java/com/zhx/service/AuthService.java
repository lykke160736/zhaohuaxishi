package com.zhx.service;

import com.zhx.domain.AuthUser;
import com.zhx.domain.Constants;
import com.zhx.exception.HttpError;
import com.zhx.repository.DbRepository;
import com.zhx.security.PasswordService;
import com.zhx.security.TokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthService {
    private final DbRepository db;
    private final PasswordService passwords;
    private final TokenService tokens;
    private final ServiceSupport support;
    private final StateService stateService;

    public AuthService(DbRepository db, PasswordService passwords, TokenService tokens,
                       ServiceSupport support, StateService stateService) {
        this.db = db;
        this.passwords = passwords;
        this.tokens = tokens;
        this.support = support;
        this.stateService = stateService;
    }

    @Transactional
    public Map<String, Object> login(Map<String, Object> body) {
        String key = string(body.get("account")).trim();
        if (key.isEmpty()) throw HttpError.bad("请输入账号");
        AuthUser acc = db.accountByAccountOrMobile(key);
        if (acc == null || !passwords.verify(string(body.get("password")), dbPassword(acc.id()))) {
            throw HttpError.bad("账号或密码错误");
        }
        if (!"active".equals(acc.status())) throw HttpError.bad("该账号已被停用，请联系平台");
        Object role = body.get("role");
        if (role == null || !acc.role().equals(String.valueOf(role))) {
            throw HttpError.bad("该账号身份为「" + support.roleName(acc.role()) + "」，请在对应入口登录");
        }
        support.welcomeCustomer(acc);
        support.log(support.roleName(acc.role()), "登录", acc.name() + " 登录成功");
        return result(acc);
    }

    @Transactional
    public Map<String, Object> register(Map<String, Object> body) {
        String name = string(body.get("name")).trim();
        String account = string(body.get("account")).trim();
        String password = string(body.get("password"));
        String role = string(body.get("role"));
        if (name.isEmpty()) throw HttpError.bad("请填写姓名");
        boolean mobile = account.matches("^1[3-9]\\d{9}$");
        if (!mobile && !account.matches("^[a-zA-Z][a-zA-Z0-9_]{3,19}$")) {
            throw HttpError.bad("账号需为 4–20 位字母、数字或下划线（字母开头），也可直接使用 11 位手机号");
        }
        if (password.length() < 6) throw HttpError.bad("密码至少 6 位");
        if ("admin".equals(role)) throw HttpError.bad("平台管理员账号不开放注册");
        if (!"customer".equals(role) && !"merchant".equals(role)) throw HttpError.bad("身份无效");
        if (db.accountExists(account)) throw HttpError.bad("该账号已被注册");
        if ("merchant".equals(role) && string(body.get("storeName")).trim().isEmpty()) {
            throw HttpError.bad("请填写门店名称");
        }

        String id = support.uid("n");
        String mobileValue = mobile ? account : "";
        String phoneValue = mobile ? account.substring(0, 3) + "****" + account.substring(7) : "未绑定手机号";
        db.insertAccount(id, name, account, mobileValue, phoneValue, passwords.hash(password), role,
                "active", support.nowUtc());
        if ("merchant".equals(role)) {
            String category = string(body.get("category"));
            if (category.isEmpty()) category = "面包";
            String address = string(body.get("address")).trim();
            if (address.isEmpty()) address = "待补充";
            db.insertApplication(support.uid("AP"), string(body.get("storeName")).trim(), category, name, id,
                    address,
                    new BigDecimal(String.valueOf(Constants.DEFAULT_CENTER.get("lat"))),
                    new BigDecimal(String.valueOf(Constants.DEFAULT_CENTER.get("lng"))),
                    mobile ? account : "待补充", "注册时提交，待平台核验", support.nowUtc(), "pending");
        }
        support.log("merchant".equals(role) ? "商家" : "顾客", "注册账号",
                name + " 注册成功" + ("merchant".equals(role) ? "，入驻申请已提交待审核" : ""));
        AuthUser acc = db.accountById(id);
        return result(acc);
    }

    public Map<String, Object> logout(AuthUser me) {
        if (me != null) {
            support.log(support.roleName(me.role()), "退出登录", me.name() + " 已退出，当前会话不可继续使用");
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> result(AuthUser acc) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", tokens.sign(acc.id(), 7L * 24 * 3600));
        out.put("state", stateService.build(acc));
        return out;
    }

    private String dbPassword(String id) {
        return db.passwordHashById(id);
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
