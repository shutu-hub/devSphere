<template>
  <div class="flex items-center justify-center gap-1 h-12">
    <div 
      v-for="n in 20" 
      :key="n"
      class="w-1 bg-gradient-to-t from-blue-500 to-cyan-400 rounded-full transition-all duration-75 ease-in-out"
      :style="{ 
        height: getBarHeight(n) + '%',
        opacity: active ? 1 : 0.3
      }"
    ></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';

const props = defineProps<{
  active: boolean;
}>();

const heights = ref<number[]>(new Array(20).fill(20));
let animationFrame: number;

const getBarHeight = (index: number) => {
  return heights.value[index];
};

const animate = () => {
  if (props.active) {
    heights.value = heights.value.map(() => Math.random() * 80 + 20); // Random height 20-100%
  } else {
    heights.value = new Array(20).fill(20); // Idle state
  }
  
  // Slow down animation slightly
  setTimeout(() => {
    animationFrame = requestAnimationFrame(animate);
  }, 50);
};

onMounted(() => {
  animate();
});

onUnmounted(() => {
  cancelAnimationFrame(animationFrame);
});
</script>
