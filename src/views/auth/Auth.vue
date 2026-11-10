<template>
  <div class="auth-wrap">
    <div class="auth-left">
      <div class="auth-brand">
        <img class="auth-brand-mark" src="../../assets/brand-mark.svg" alt="" />
        <div>
          <h1 class="display auth-brand-name">朝花惜食</h1>
        </div>
      </div>
      <div class="auth-slogan">
        <p>今天的食物，</p>
        <p>今天吃完。</p>
      </div>
      <div style="margin-top: auto; padding-top: 40px">
        <p class="kicker" style="color: #493522">顾客 · 商家 · 平台管理员</p>
      </div>
    </div>

    <div class="auth-right">
      <div class="auth-card">
        <div class="row" style="align-items: flex-start">
          <div class="stack">
            <span class="kicker">{{ mode === 'login' ? 'SIGN IN' : 'CREATE ACCOUNT' }}</span>
            <h2 class="display" style="font-size: 1.75rem; margin-top: 4px">
              {{ mode === 'login' ? '登录你的账号' : '注册新账号' }}
            </h2>
          </div>
          <button class="auth-switch" @click="switchMode">
            {{ mode === 'login' ? '去注册' : '去登录' }}
          </button>
        </div>

        <!-- 身份端选择：决定登录后在哪个端工作 -->
        <div style="margin-top: 22px">
          <label class="auth-label">选择身份端</label>
          <div class="role-grid">
            <button
              v-for="r in roleOptions"
              :key="r.k"
              class="role-btn"
              :class="{ on: role === r.k }"
              :disabled="mode === 'register' && r.k === 'admin'"
              @click="role = r.k"
            >
              <span class="role-name">{{ r.label }}</span>
              <span class="role-desc">{{ mode === 'register' && r.k === 'admin' ? '不开放注册' : r.desc }}</span>
            </button>
          </div>
        </div>

        <div class="field" style="margin-top: 20px" v-if="mode === 'register'">
          <label class="auth-label">姓名 / 负责人</label>
          <input class="input" v-model="form.name" placeholder="请输入真实姓名" />
        </div>

        <div class="field" :style="mode === 'login' ? 'margin-top:20px' : ''">
          <label class="auth-label">账号</label>
          <input
            class="input"
            v-model="form.account"
            maxlength="20"
            :placeholder="mode === 'login' ? '账号或注册手机号' : '4–20 位字母/数字，或手机号'"
          />
        </div>

        <div class="field">
          <label class="auth-label">密码</label>
          <input class="input" v-model="form.password" type="password" placeholder="至少 6 位" @keyup.enter="submit" />
        </div>

        <!-- 商家注册额外信息 -->
        <template v-if="mode === 'register' && role === 'merchant'">
          <div class="field">
            <label class="auth-label">门店名称</label>
            <input class="input" v-model="form.storeName" placeholder="例如：麦浪面包房" />
          </div>
          <div class="row" style="gap: 12px">
            <div class="field" style="flex: 1">
              <label class="auth-label">食品类别</label>
              <select class="select" v-model="form.category">
                <option v-for="c in store.categories.slice(1)" :key="c">{{ c }}</option>
              </select>
            </div>
            <div class="field" style="flex: 1.4">
              <label class="auth-label">门店地址</label>
              <input class="input" v-model="form.address" placeholder="街道门牌" />
            </div>
          </div>
          <div class="notice" style="margin-bottom: 14px">
            商家注册后需平台审核通过，才会开放盲盒发布与售卖权限。
          </div>
        </template>

        <div v-if="msg" class="notice notice-warn" style="margin-bottom: 14px">{{ msg }}</div>

        <button class="btn btn-accent btn-block" style="padding: 14px" :disabled="submitting" @click="submit">
          {{ submitting ? '处理中…' : mode === 'login' ? '登录' : '注册并登录' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from '../../store'

const route = useRoute()
const router = useRouter()
const store = useStore()

const mode = ref(route.name === 'register' ? 'register' : 'login')
const role = ref('customer')
const msg = ref('')
const submitting = ref(false)

const roles = [
  { k: 'customer', label: '顾客', desc: '发现附近门店 · 购买盲盒' },
  { k: 'merchant', label: '商家', desc: '发布盲盒 · 核销订单' },
  { k: 'admin', label: '平台管理员', desc: '审核入驻 · 处理介入' }
]
// 注册不允许创建管理员，避免越权获得平台权限
const roleOptions = computed(() =>
  mode.value === 'register' ? roles.filter((r) => r.k !== 'admin') : roles
)

watch(mode, () => {
  msg.value = ''
  if (mode.value === 'register' && role.value === 'admin') role.value = 'customer'
})

const form = reactive({ name: '', account: '', password: '', storeName: '', category: '面包', address: '' })

function switchMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  router.replace(mode.value === 'login' ? '/login' : '/register')
}

async function submit() {
  msg.value = ''
  if (submitting.value) return
  submitting.value = true
  try {
    const r =
      mode.value === 'login'
        ? await store.login({ account: form.account, password: form.password, role: role.value })
        : await store.register({
            name: form.name,
            account: form.account,
            password: form.password,
            role: role.value,
            storeName: form.storeName,
            category: form.category,
            address: form.address
          })

    if (!r.ok) {
      msg.value = r.msg
      return
    }
    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' && redirect.startsWith('/') ? redirect : store.homePath)
  } finally {
    submitting.value = false
  }
}
</script>
