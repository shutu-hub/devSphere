<template>
  <div class="min-h-screen bg-gray-900 text-white font-sans flex flex-col relative overflow-y-auto">
    <!-- Background Effects -->
    <div class="absolute top-0 left-0 w-full h-full overflow-hidden z-0 pointer-events-none">
      <div class="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-blue-600/20 rounded-full blur-[120px]"></div>
      <div class="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-purple-600/20 rounded-full blur-[120px]"></div>
    </div>

    <!-- Header -->
    <header class="w-full p-6 flex justify-between items-center bg-gray-900/50 backdrop-blur-md border-b border-gray-800 z-10">
      <div class="flex items-center gap-3">
        <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-xl shadow-lg shadow-blue-500/20">🤖</div>
        <div>
          <h1 class="text-xl font-bold tracking-tight">AI Interviewer</h1>
          <p class="text-xs text-gray-400">Powered by DeepSeek & FunASR</p>
        </div>
      </div>
      <router-link to="/" class="px-4 py-2 rounded-lg text-sm text-gray-400 hover:text-white hover:bg-white/5 transition-all">退出</router-link>
    </header>

    <main class="flex-1 flex flex-col items-center justify-center p-8 pb-20 max-w-6xl mx-auto w-full gap-12 z-10">
      
      <!-- Hero -->
      <div class="text-center space-y-6 animate-fade-in-up">
        <h2 class="text-5xl md:text-6xl font-extrabold bg-clip-text text-transparent bg-gradient-to-r from-blue-400 via-indigo-400 to-purple-400 tracking-tight">
          下一份 Offer，从这里开始
        </h2>
        <p class="text-gray-400 text-lg max-w-2xl mx-auto leading-relaxed">
          上传简历，选择方向，让 AI 面试官为你进行一场
          <span class="text-blue-400 font-medium">全真模拟面试</span>。
          实时反馈，精准提升。
        </p>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-8 w-full max-w-5xl">
        <!-- Left: Resume Upload -->
        <div class="bg-gray-800/40 backdrop-blur-sm rounded-3xl p-8 border border-gray-700/50 hover:border-blue-500/30 transition-all duration-300 group hover:bg-gray-800/60 shadow-xl">
          <h3 class="text-xl font-bold mb-6 flex items-center gap-3">
            <div class="w-8 h-8 rounded-lg bg-blue-500/20 flex items-center justify-center text-blue-400">📄</div>
            上传简历
          </h3>
          
          <div 
            class="border-2 border-dashed border-gray-600 rounded-2xl h-64 flex flex-col items-center justify-center gap-4 cursor-pointer transition-all duration-300 relative overflow-hidden group-hover:border-gray-500"
            :class="{'border-blue-500 bg-blue-500/10': isDragging, 'bg-gray-900/50': !isDragging}"
            @dragover.prevent="isDragging = true"
            @dragleave.prevent="isDragging = false"
            @drop.prevent="handleDrop"
            @click="triggerFileInput"
          >
            <input type="file" ref="fileInput" class="hidden" accept=".pdf,.doc,.docx,.txt" @change="handleFileSelect" />
            
            <div v-if="!file" class="text-center p-6 transition-transform duration-300 group-hover:scale-105">
              <div class="text-5xl mb-4 text-gray-600 group-hover:text-blue-400 transition-colors">☁️</div>
              <p class="font-medium text-gray-300 text-lg">点击或拖拽上传简历</p>
              <p class="text-sm text-gray-500 mt-2">支持 PDF, Word, TXT (Max 10MB)</p>
            </div>

            <div v-else class="flex flex-col items-center z-10 animate-fade-in">
              <div class="w-16 h-16 rounded-xl bg-green-500/20 flex items-center justify-center text-3xl mb-3 text-green-400">✅</div>
              <p class="font-bold text-white text-lg">{{ file.name }}</p>
              <p class="text-sm text-gray-400 mt-1">{{ (file.size / 1024).toFixed(1) }} KB</p>
              <button @click.stop="file = null" class="mt-6 px-4 py-1.5 rounded-full bg-red-500/10 text-red-400 text-sm hover:bg-red-500/20 transition-colors">移除文件</button>
            </div>
            
            <!-- Uploading Overlay -->
            <div v-if="isUploading" class="absolute inset-0 bg-gray-900/80 backdrop-blur-sm flex flex-col items-center justify-center z-20">
               <div class="w-12 h-12 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mb-4"></div>
               <div class="text-blue-400 font-bold">正在解析简历...</div>
            </div>
          </div>
        </div>

        <!-- Right: Domain Selection -->
        <div class="bg-gray-800/40 backdrop-blur-sm rounded-3xl p-8 border border-gray-700/50 hover:border-purple-500/30 transition-all duration-300 hover:bg-gray-800/60 shadow-xl">
          <h3 class="text-xl font-bold mb-6 flex items-center gap-3">
            <div class="w-8 h-8 rounded-lg bg-purple-500/20 flex items-center justify-center text-purple-400">🎯</div>
            选择面试方向
          </h3>
          
          <div class="grid grid-cols-2 gap-4">
            <button 
              v-for="domain in domains" 
              :key="domain.id"
              @click="selectedDomain = domain.id"
              class="p-4 rounded-xl border text-left transition-all duration-300 relative overflow-hidden group"
              :class="selectedDomain === domain.id ? 'bg-gradient-to-br from-blue-600 to-indigo-700 border-transparent shadow-lg shadow-blue-900/50 scale-[1.02]' : 'bg-gray-900/50 border-gray-700 hover:border-gray-500 hover:bg-gray-800'"
            >
              <div class="text-3xl mb-3 transform group-hover:scale-110 transition-transform duration-300">{{ domain.icon }}</div>
              <div class="font-bold text-base" :class="selectedDomain === domain.id ? 'text-white' : 'text-gray-300'">{{ domain.name }}</div>
              <div class="absolute top-3 right-3 w-2 h-2 rounded-full bg-white shadow-[0_0_10px_white]" v-if="selectedDomain === domain.id"></div>
            </button>
          </div>
        </div>
      </div>

      <!-- Action -->
      <div class="w-full flex justify-center pt-8">
        <button 
          @click="handleStart"
          :disabled="!selectedDomain || isUploading || isStarting"
          class="group relative px-16 py-5 rounded-full bg-gradient-to-r from-blue-500 to-purple-600 text-white font-bold text-xl shadow-2xl shadow-blue-900/40 hover:scale-105 hover:shadow-blue-900/60 disabled:opacity-50 disabled:cursor-not-allowed disabled:scale-100 transition-all duration-300 overflow-hidden"
        >
          <div class="absolute inset-0 bg-white/20 translate-y-full group-hover:translate-y-0 transition-transform duration-300"></div>
          <span class="relative flex items-center gap-2">
            <span v-if="isStarting" class="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
            {{ isStarting ? '正在创建面试...' : '开始模拟面试 →' }}
          </span>
        </button>
      </div>

    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { uploadFile, createInterview } from '@/api/interview';

const router = useRouter();
const fileInput = ref<HTMLInputElement | null>(null);
const file = ref<File | null>(null);
const isDragging = ref(false);
const isUploading = ref(false);
const isStarting = ref(false);
const selectedDomain = ref('');

const domains = [
  { id: 'Java', name: 'Java 后端', icon: '☕' },
  { id: 'Frontend', name: '前端开发', icon: '🎨' },
  { id: 'SystemDesign', name: '系统设计', icon: '🏗️' },
  { id: 'DevOps', name: '运维/云原生', icon: '☁️' },
  { id: 'Go', name: 'Go 语言', icon: '🐹' },
  { id: 'Python', name: 'Python / AI', icon: '🐍' },
];

const triggerFileInput = () => {
  fileInput.value?.click();
};

const handleFileSelect = (event: Event) => {
  const target = event.target as HTMLInputElement;
  if (target.files && target.files.length > 0) {
    file.value = target.files[0];
  }
};

const handleDrop = (event: DragEvent) => {
  isDragging.value = false;
  if (event.dataTransfer?.files && event.dataTransfer.files.length > 0) {
    file.value = event.dataTransfer.files[0];
  }
};

const handleStart = async () => {
  if (!selectedDomain.value) return;
  
  isStarting.value = true;
  try {
    let resumeUrl = '';
    
    // 1. Upload Resume if exists
    if (file.value) {
      isUploading.value = true;
      try {
        const res = await uploadFile(file.value);
        resumeUrl = (res as any).url; 
      } catch (e) {
        console.error('Upload failed', e);
        alert('简历上传失败，请重试');
        isUploading.value = false;
        isStarting.value = false;
        return;
      }
      isUploading.value = false;
    }

    // 2. Create Interview
    const mockJobIds: Record<string, string> = {
        'Java': '1',
        'Frontend': '2',
        'SystemDesign': '3',
        'DevOps': '4',
        'Go': '5',
        'Python': '6'
    };

    const res = await createInterview({
        jobId: mockJobIds[selectedDomain.value] || '1',
        resumeUrl: resumeUrl,
        category: selectedDomain.value
    });
    
    const interview = res as any; 
    
    if (interview && (interview.id || interview.data?.id)) {
        // Handle both direct return and wrapped response
        const id = interview.id || interview.data.id;
        router.push({
            name: 'ai-interview-session',
            params: { id: id },
            query: {
              domain: selectedDomain.value,
              resumeUrl: resumeUrl
            }
        });
    } else {
        alert('创建面试失败');
    }

  } catch (e) {
    console.error('Start interview error', e);
    alert('系统繁忙，请稍后重试');
  } finally {
    isStarting.value = false;
  }
};
</script>

<style scoped>
.animate-fade-in-up {
  animation: fadeInUp 0.8s ease-out;
}
.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
</style>
