<template>
  <header class="pagehead">
    <button class="backbtn" @click="$router.back()" aria-label="返回">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M15 5l-7 7 7 7" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>
    <div class="stack" style="flex: 1; min-width: 0">
      <span class="kicker" v-if="kicker">{{ kicker }}</span>
      <strong class="display" style="font-size: 1.25rem">{{ title }}</strong>
    </div>
    <slot />
  </header>
</template>

<script setup>
defineProps({ title: String, kicker: String })
</script>
