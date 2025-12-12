<template>
  <div class="min-h-screen bg-gradient-to-br from-[#0f172a] to-[#1e293b] text-white font-sans overflow-hidden flex flex-col relative">
    <!-- Particles Background (Simplified CSS/JS implementation) -->
    <canvas ref="particlesCanvas" class="absolute inset-0 z-0 pointer-events-none"></canvas>

    <!-- Header -->
    <header class="flex justify-between items-center p-6 z-10 relative">
      <div class="flex items-center">
        <div class="w-10 h-10 rounded-full bg-gradient-to-r from-cyan-500 to-cyan-400 flex items-center justify-center mr-3 shadow-lg shadow-cyan-500/20">
          <span class="text-xl">🤖</span>
        </div>
        <h1 class="text-2xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-cyan-400 to-cyan-200">AI Interviewer</h1>
      </div>
      
      <div class="flex items-center gap-6">
        <!-- Countdown -->
        <div class="relative w-20 h-20 flex items-center justify-center">
          <svg class="w-full h-full transform -rotate-90">
            <circle class="text-white/10" stroke-width="6" stroke="currentColor" fill="transparent" r="36" cx="40" cy="40" />
            <circle 
              class="text-cyan-400 transition-all duration-1000 ease-linear" 
              stroke-width="6" 
              stroke-linecap="round"
              stroke="currentColor" 
              fill="transparent" 
              r="36" 
              cx="40" 
              cy="40" 
              :stroke-dasharray="circumference" 
              :stroke-dashoffset="dashOffset" 
            />
          </svg>
          <div class="absolute text-lg font-bold font-mono">{{ formattedTime }}</div>
        </div>
        
        <!-- Progress -->
        <div class="hidden md:flex flex-col items-end">
          <span class="text-sm text-white/70 mb-1">Interview Progress</span>
          <div class="flex items-center gap-3">
            <div class="w-32 h-1.5 bg-white/10 rounded-full overflow-hidden">
              <div class="h-full bg-gradient-to-r from-cyan-400 to-cyan-300 transition-all duration-500" :style="{ width: progressPercentage + '%' }"></div>
            </div>
            <span class="text-sm font-medium font-mono">{{ questionCount }}/{{ totalQuestions }}</span>
          </div>
        </div>
        
        <!-- Settings/Exit -->
        <button @click="handleEndInterview" class="w-10 h-10 rounded-full bg-white/10 backdrop-blur-md border border-white/20 flex items-center justify-center hover:bg-white/20 transition-all text-white/80 hover:text-white">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-grow flex flex-col items-center justify-center relative z-10 px-4">
      
      <!-- Interviewer Avatar -->
      <div class="relative mb-8 group">
        <!-- Glow Effect -->
        <div class="absolute inset-0 bg-cyan-500/30 blur-[60px] rounded-full transform scale-110 group-hover:scale-125 transition-transform duration-700"></div>
        
        <!-- Avatar Circle -->
        <div class="relative w-60 h-60 rounded-full border-4 border-white/20 shadow-[0_0_30px_rgba(6,182,212,0.3)] overflow-hidden bg-gray-900 z-10">
           <Avatar :viseme="currentViseme" :audio-url="currentAudioUrl" @speak-end="onSpeakEnd" class="w-full h-full object-cover" />
        </div>
        
        <!-- Name Tag -->
        <div class="absolute -bottom-4 left-1/2 transform -translate-x-1/2 bg-gray-900/80 backdrop-blur-md border border-white/10 px-4 py-1.5 rounded-full flex flex-col items-center z-20 whitespace-nowrap">
          <h2 class="text-sm font-semibold text-white">AI Interviewer</h2>
          <span class="text-[10px] text-cyan-400 uppercase tracking-wider">Senior Tech Lead</span>
        </div>
      </div>

      <!-- Question Card -->
      <transition name="slide-up" mode="out-in">
        <div v-if="currentQuestion" :key="currentQuestion.id" class="w-full max-w-2xl bg-white/10 backdrop-blur-md border border-white/20 rounded-3xl p-8 shadow-2xl mb-8 hover:transform hover:-translate-y-1 transition-all duration-300">
          <div class="flex items-center mb-4 gap-3">
            <span class="px-3 py-1 rounded-full bg-cyan-500/20 text-cyan-300 text-xs font-bold border border-cyan-500/30">
              Question {{ questionCount }}/{{ totalQuestions }}
            </span>
            <span class="text-xs text-white/50 flex items-center gap-1">
              <svg class="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
              Est. 2 mins
            </span>
          </div>
          
          <h3 class="text-xl md:text-2xl font-medium leading-relaxed text-white/90 mb-4">
            {{ currentQuestion.content }}
          </h3>
          
          <div v-if="status === 'THINKING'" class="flex items-center gap-2 text-cyan-400 text-sm animate-pulse">
            <div class="w-2 h-2 bg-cyan-400 rounded-full"></div>
            AI is analyzing your response...
          </div>
          <div v-else class="text-white/40 text-sm">
            Click the microphone to answer or type below.
          </div>
        </div>
      </transition>

      <!-- Interaction Area -->
      <div class="flex flex-col items-center w-full max-w-xl">
        
        <!-- Audio Visualizer -->
        <div class="h-12 flex items-center justify-center gap-1 mb-6 w-full">
           <div v-for="i in 30" :key="i" 
                class="w-1 bg-white/30 rounded-full transition-all duration-100"
                :class="{ 'bg-cyan-400': isAudioActive }"
                :style="{ height: isAudioActive ? Math.random() * 100 + '%' : '4px' }">
           </div>
        </div>

        <!-- Controls -->
        <div class="flex items-center gap-6">
          <!-- Text Input Toggle (Optional, keeping it simple for now as per design) -->
          
          <!-- Mic Button -->
          <button 
            @click="toggleRecording"
            class="w-16 h-16 rounded-full flex items-center justify-center transition-all duration-300 relative group"
            :class="isRecording ? 'bg-cyan-500/20 border-cyan-400 shadow-[0_0_30px_rgba(6,182,212,0.5)]' : 'bg-white/10 border-white/20 hover:bg-white/20 hover:scale-105'"
            style="border-width: 2px;"
          >
            <!-- Pulse Ring -->
            <div v-if="isRecording" class="absolute inset-0 rounded-full border-2 border-cyan-400 opacity-50 animate-ping"></div>
            
            <svg v-if="!isRecording" xmlns="http://www.w3.org/2000/svg" class="h-6 w-6 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" />
            </svg>
            <div v-else class="w-5 h-5 bg-cyan-400 rounded-sm"></div>
          </button>

          <!-- Next Button -->
          <button 
            @click="submitTextAnswer"
            :disabled="!answerText && !isRecording" 
            class="px-8 py-3 rounded-full bg-gradient-to-r from-cyan-500 to-blue-500 text-white font-medium shadow-lg shadow-cyan-500/30 hover:shadow-cyan-500/50 hover:-translate-y-0.5 disabled:opacity-50 disabled:cursor-not-allowed disabled:transform-none transition-all duration-300 flex items-center gap-2"
          >
            Next Question
            <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 5l7 7m0 0l-7 7m7-7H3" />
            </svg>
          </button>
        </div>

        <!-- Hidden Text Area for Fallback -->
        <textarea 
          v-model="answerText" 
          class="mt-6 w-full bg-black/20 border border-white/10 rounded-xl p-4 text-white/90 placeholder-white/30 focus:outline-none focus:border-cyan-500/50 transition-all resize-none h-24 text-sm"
          placeholder="Or type your answer here..."
        ></textarea>

      </div>
    </main>

    <!-- Footer Stats -->
    <footer class="p-6 flex justify-between items-center text-sm text-white/50 relative z-10">
      <div class="flex gap-4">
        <div class="flex items-center gap-2 bg-white/5 px-3 py-1.5 rounded-full border border-white/10">
          <svg class="w-4 h-4 text-cyan-400" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
          <span>Time: {{ formattedTime }}</span>
        </div>
        <div class="flex items-center gap-2 bg-white/5 px-3 py-1.5 rounded-full border border-white/10">
          <svg class="w-4 h-4 text-cyan-400" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" /></svg>
          <span>Speaking: {{ isRecording ? 'Active' : 'Idle' }}</span>
        </div>
      </div>
      
      <div class="hidden md:flex items-center gap-2 bg-white/5 px-3 py-1.5 rounded-full border border-white/10">
        <div class="w-2 h-2 rounded-full bg-green-400 animate-pulse"></div>
        <span>System Online</span>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, onUnmounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Avatar from './components/Avatar.vue';
import { startInterview, submitAnswer, completeInterview, type Question } from '@/api/interview';

const route = useRoute();
const router = useRouter();
const interviewId = route.params.id as string;

// State
const status = ref<'IDLE' | 'THINKING' | 'SPEAKING' | 'LISTENING'>('IDLE');
const currentQuestion = ref<Question | null>(null);
const answerText = ref('');
const questionCount = ref(0);
const totalQuestions = ref(5);
const isLoading = ref(false);
const isRecording = ref(false);

// Timer
const startTime = ref(Date.now());
const currentTime = ref(Date.now());
let timerInterval: number;

// Avatar
const currentViseme = ref(null);
const currentAudioUrl = ref('');

// Particles
const particlesCanvas = ref<HTMLCanvasElement | null>(null);

// Computed
const statusColorDot = computed(() => {
  switch (status.value) {
    case 'SPEAKING': return 'bg-cyan-400 shadow-[0_0_10px_#22d3ee]';
    case 'LISTENING': return 'bg-green-400 shadow-[0_0_10px_#4ade80]';
    case 'THINKING': return 'bg-purple-400 shadow-[0_0_10px_#c084fc]';
    default: return 'bg-gray-500';
  }
});

const formattedTime = computed(() => {
  const diff = Math.floor((currentTime.value - startTime.value) / 1000);
  const mins = Math.floor(diff / 60).toString().padStart(2, '0');
  const secs = (diff % 60).toString().padStart(2, '0');
  return `${mins}:${secs}`;
});

const progressPercentage = computed(() => {
  return Math.min((questionCount.value / totalQuestions.value) * 100, 100);
});

const circumference = 2 * Math.PI * 36;
const dashOffset = computed(() => {
    // 15 minutes countdown logic from reference, or just progress based
    // Reference had 15:00 countdown. Let's make it a 15 min countdown visual.
    const totalSeconds = 15 * 60;
    const elapsed = Math.floor((currentTime.value - startTime.value) / 1000);
    const remaining = Math.max(totalSeconds - elapsed, 0);
    return circumference - (remaining / totalSeconds) * circumference;
});

const isAudioActive = computed(() => {
    return isRecording.value || status.value === 'SPEAKING';
});

// Methods
const initInterview = async () => {
  if (!interviewId) {
    alert('Invalid Interview ID');
    router.push('/interview');
    return;
  }

  isLoading.value = true;
  status.value = 'THINKING';
  try {
    const question = await startInterview(interviewId);
    if (question) {
      handleNewQuestion(question as unknown as Question);
    } else {
      alert('Failed to start interview');
    }
  } catch (e) {
    console.error('Start error', e);
    alert('Failed to start interview session');
  } finally {
    isLoading.value = false;
  }
};

const handleNewQuestion = (question: Question) => {
  currentQuestion.value = question;
  questionCount.value++;
  status.value = 'SPEAKING';
  
  // Simulate TTS delay
  setTimeout(() => {
    status.value = 'LISTENING';
  }, 1500);
};

const toggleRecording = () => {
    isRecording.value = !isRecording.value;
    if (isRecording.value) {
        // Start recording logic (Mock for now)
    } else {
        // Stop recording logic
    }
};

const submitTextAnswer = async () => {
  if ((!answerText.value.trim() && !isRecording.value) || isLoading.value) return;

  // If recording, we would transcribe here. For now assume text input or mock.
  const answer = answerText.value || "[Voice Answer Placeholder]";
  
  answerText.value = '';
  isRecording.value = false;
  isLoading.value = true;
  status.value = 'THINKING';

  try {
    const nextQuestion = await submitAnswer(interviewId, answer);
    
    if (nextQuestion) {
      handleNewQuestion(nextQuestion as unknown as Question);
    } else {
      finishInterview();
    }
  } catch (e) {
    console.error('Submit error', e);
    alert('Failed to submit answer');
    isLoading.value = false;
    status.value = 'LISTENING';
    answerText.value = answer;
  }
};

const finishInterview = async () => {
  try {
    await completeInterview(interviewId);
    router.push({ name: 'interview-result', params: { id: interviewId } });
  } catch (e) {
    console.error('Complete error', e);
    router.push({ name: 'interview-result', params: { id: interviewId } });
  }
};

const handleEndInterview = async () => {
  if (confirm('Are you sure you want to end the interview?')) {
    await finishInterview();
  }
};

const onSpeakEnd = () => {
  if (status.value === 'SPEAKING') {
    status.value = 'LISTENING';
  }
};

// Particle Effect
const initParticles = () => {
    const canvas = particlesCanvas.value;
    if (!canvas) return;
    
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;

    const particles: any[] = [];
    const particleCount = 50;

    for (let i = 0; i < particleCount; i++) {
        particles.push({
            x: Math.random() * canvas.width,
            y: Math.random() * canvas.height,
            vx: (Math.random() - 0.5) * 0.5,
            vy: (Math.random() - 0.5) * 0.5,
            size: Math.random() * 2 + 1,
            alpha: Math.random() * 0.5 + 0.1
        });
    }

    const animate = () => {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        
        particles.forEach(p => {
            p.x += p.vx;
            p.y += p.vy;

            if (p.x < 0) p.x = canvas.width;
            if (p.x > canvas.width) p.x = 0;
            if (p.y < 0) p.y = canvas.height;
            if (p.y > canvas.height) p.y = 0;

            ctx.beginPath();
            ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
            ctx.fillStyle = `rgba(6, 182, 212, ${p.alpha})`; // Cyan color
            ctx.fill();
        });
        
        // Draw connections
        for (let i = 0; i < particles.length; i++) {
            for (let j = i + 1; j < particles.length; j++) {
                const dx = particles[i].x - particles[j].x;
                const dy = particles[i].y - particles[j].y;
                const dist = Math.sqrt(dx * dx + dy * dy);

                if (dist < 150) {
                    ctx.beginPath();
                    ctx.strokeStyle = `rgba(6, 182, 212, ${0.15 * (1 - dist / 150)})`;
                    ctx.lineWidth = 1;
                    ctx.moveTo(particles[i].x, particles[i].y);
                    ctx.lineTo(particles[j].x, particles[j].y);
                    ctx.stroke();
                }
            }
        }
        
        requestAnimationFrame(animate);
    };
    
    animate();
};

// Lifecycle
onMounted(() => {
  initInterview();
  initParticles();
  timerInterval = setInterval(() => {
    currentTime.value = Date.now();
  }, 1000);
});

onUnmounted(() => {
  clearInterval(timerInterval);
});
</script>

<style scoped>
.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.5s cubic-bezier(0.16, 1, 0.3, 1);
}

.slide-up-enter-from {
  opacity: 0;
  transform: translateY(20px);
}

.slide-up-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}
</style>
