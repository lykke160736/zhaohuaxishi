<template>
  <div ref="el" :style="{ height, width: '100%' }"></div>
</template>

<script setup>
// 通用 ECharts 包装：传入 option 即渲染，自动响应数据变化与窗口缩放
// 按需引入：仅注册项目实际用到的图表与组件，避免全量打包（约 1.1MB）
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { onMounted, onUnmounted, ref, watch } from 'vue'

echarts.use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '260px' }
})

const el = ref(null)
let chart = null

const render = () => {
  if (chart && props.option) chart.setOption(props.option, true)
}
const resize = () => chart?.resize()

onMounted(() => {
  chart = echarts.init(el.value)
  render()
  window.addEventListener('resize', resize)
})
onUnmounted(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})
watch(() => props.option, render, { deep: true })
</script>
