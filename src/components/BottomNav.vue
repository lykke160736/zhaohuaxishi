<template>
  <header class="topnav">
    <router-link to="/" class="topnav-brand">
      朝花惜食<span class="en">ZHAOHUAXISHI</span>
    </router-link>
    <nav class="topnav-links">
      <router-link to="/" :class="{ on: $route.name === 'home' }">首页</router-link>
      <router-link to="/orders" :class="{ on: $route.name === 'orders' || $route.name === 'order' }">
        我的订单
      </router-link>
      <router-link to="/wallet" :class="{ on: $route.name === 'wallet' }">积分</router-link>
      <NoticeBell />
      <router-link to="/me" :class="{ on: $route.name === 'me' }">我的</router-link>
    </nav>
  </header>
</template>

<script setup>
import NoticeBell from './NoticeBell.vue'
</script>
