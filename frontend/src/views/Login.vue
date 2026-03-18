<template>
  <div class="login-container">
    <div class="login-content">
      <div class="login-left">
        <div class="brand-info">
          <h1>Spark Security</h1>
          <p>Welcome back! Secure access to your dashboard and manage your operations efficiently.</p>
        </div>
      </div>
      <div class="login-right">
        <el-card class="login-card" shadow="never">
          <div class="card-header">
            <h2>Sign In</h2>
            <p>Enter your details to proceed.</p>
          </div>
          <el-form :model="loginForm" :rules="rules" ref="loginFormRef" label-position="top" size="large">
            <el-form-item label="Username" prop="username">
              <el-input v-model="loginForm.username" placeholder="johndoe"></el-input>
            </el-form-item>
            <el-form-item label="Password" prop="password">
              <el-input v-model="loginForm.password" type="password" placeholder="••••••••" show-password @keyup.enter="handleLogin"></el-input>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" @click="handleLogin" class="submit-btn">Sign In</el-button>
            </el-form-item>
            <div class="register-link">
              <span>Don't have an account? </span>
              <el-link type="primary" :underline="false" @click="$router.push('/register')">Create one</el-link>
            </div>
          </el-form>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import Cookies from 'js-cookie'

const router = useRouter()
const loginFormRef = ref(null)
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const rules = reactive({
  username: [
    { required: true, message: 'Please input username', trigger: 'blur' },
    { min: 3, max: 20, message: 'Length should be 3 to 20', trigger: 'blur' },
  ],
  password: [
    { required: true, message: 'Please input password', trigger: 'blur' },
    { min: 6, max: 20, message: 'Length should be 6 to 20', trigger: 'blur' },
  ]
})

const handleLogin = async () => {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const res = await request.post('/auth/login', loginForm)
        if (res.code === 200) {
          ElMessage.success('Login successful')
          localStorage.setItem('accessToken', res.data.accessToken)
          Cookies.set('refreshToken', res.data.refreshToken, { expires: 7 })
          if (res.data.user) {
             localStorage.setItem('user', JSON.stringify(res.data.user))
          }
          router.push('/index')
        } else {
          ElMessage.error(res.message || 'Login failed')
        }
      } catch (error) {
        console.error(error)
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #f6f8fb 0%, #e5e9f2 100%);
  padding: 2rem;
  box-sizing: border-box;
}

.login-content {
  display: flex;
  width: 100%;
  max-width: 1000px;
  background: #ffffff;
  border-radius: 20px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.login-left {
  flex: 1;
  background: linear-gradient(135deg, var(--primary-color) 0%, #312e81 100%);
  color: #ffffff;
  padding: 4rem;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.brand-info h1 {
  font-size: 2.5rem;
  font-weight: 700;
  margin-bottom: 1rem;
  line-height: 1.2;
}

.brand-info p {
  font-size: 1.1rem;
  line-height: 1.6;
  opacity: 0.9;
}

.login-right {
  flex: 1;
  padding: 4rem;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
}

.login-card {
  width: 100%;
  max-width: 360px;
  background: transparent;
  box-shadow: none !important;
}

.card-header {
  margin-bottom: 2rem;
}

.card-header h2 {
  font-size: 1.8rem;
  color: var(--text-primary);
  margin: 0 0 0.5rem 0;
  font-weight: 700;
}

.card-header p {
  color: var(--text-secondary);
  margin: 0;
  font-size: 0.95rem;
}

.submit-btn {
  width: 100%;
  border-radius: 8px;
  font-weight: 600;
  letter-spacing: 0.5px;
  margin-top: 1rem;
}

.register-link {
  text-align: center;
  margin-top: 1.5rem;
  font-size: 0.95rem;
  color: var(--text-secondary);
}

.register-link .el-link {
  font-weight: 600;
  font-size: 0.95rem;
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .login-content {
    flex-direction: column;
  }
  .login-left {
    padding: 3rem 2rem;
    text-align: center;
  }
  .login-right {
    padding: 3rem 2rem;
  }
}
</style>
