<template>
  <div class="empty">
    <div class="glyph">{{ glyph }}</div>
    <p style="margin-top: 10px; font-size: 0.92rem">{{ text }}</p>
    <slot />
  </div>
</template>

<script setup>
defineProps({
  glyph: { type: String, default: '空' },
  text: { type: String, default: '这里还什么都没有' }
})
</script>
