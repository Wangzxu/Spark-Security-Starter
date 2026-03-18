<template>
  <div class="register-container">
    <div class="register-content">
      <div class="register-left">
        <div class="brand-info">
          <h1>Join Spark</h1>
          <p>Create an account to access powerful security features and manage your operations.</p>
        </div>
      </div>
      <div class="register-right">
        <el-card class="register-card" shadow="never">
          <div class="card-header">
            <h2>Create Account</h2>
            <p>Get started with your free account.</p>
          </div>
          <el-form :model="registerForm" :rules="rules" ref="registerFormRef" label-position="top" size="large">
            <el-form-item label="Username" prop="username">
              <el-input v-model="registerForm.username" placeholder="johndoe"></el-input>
            </el-form-item>
            <el-form-item label="Nickname" prop="nickname">
              <el-input v-model="registerForm.nickname" placeholder="John Doe"></el-input>
            </el-form-item>
            <el-form-item label="Password" prop="password">
              <el-input v-model="registerForm.password" type="password" placeholder="••••••••" show-password></el-input>
            </el-form-item>
            <el-form-item label="Confirm Password" prop="confirmPassword">
              <el-input v-model="registerForm.confirmPassword" type="password" placeholder="••••••••" show-password @keyup.enter="handleRegister"></el-input>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" @click="handleRegister" class="submit-btn">Create Account</el-button>
            </el-form-item>
            <div class="login-link">
              <span>Already have an account? </span>
              <el-link type="primary" :underline="false" @click="$router.push('/login')">Sign in</el-link>
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

const router = useRouter()
const registerFormRef = ref(null)
const loading = ref(false)

const registerForm = reactive({
  username: '',
  password: '',
  nickname: '',
  confirmPassword: ''
})

const validatePass2 = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('Please input the password again'))
  } else if (value !== registerForm.password) {
    callback(new Error('Two inputs don\'t match!'))
  } else {
    callback()
  }
}

const rules = reactive({
  username: [
    { required: true, message: 'Please input username', trigger: 'blur' },
    { min: 3, max: 20, message: 'Length should be 3 to 20', trigger: 'blur' },
  ],
  nickname: [
    { required: true, message: 'Please input nickname', trigger: 'blur' },
  ],
  password: [
    { required: true, message: 'Please input password', trigger: 'blur' },
    { min: 6, max: 20, message: 'Length should be 6 to 20', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, validator: validatePass2, trigger: 'blur' }
  ]
})

const handleRegister = async () => {
  if (!registerFormRef.value) return
  await registerFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const payload = {
          username: registerForm.username,
          password: registerForm.password,
          nickname: registerForm.nickname
        }
        const res = await request.post('/auth/register', payload)
        if (res.code === 200) {
          ElMessage.success('Registration successful, please login')
          router.push('/login')
        } else {
          ElMessage.error(res.message || 'Registration failed')
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
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #f6f8fb 0%, #e5e9f2 100%);
  padding: 2rem;
  box-sizing: border-box;
}

.register-content {
  display: flex;
  width: 100%;
  max-width: 1000px;
  background: #ffffff;
  border-radius: 20px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.register-left {
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

.register-right {
  flex: 1;
  padding: 3rem 4rem;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
}

.register-card {
  width: 100%;
  max-width: 360px;
  background: transparent;
  box-shadow: none !important;
}

.card-header {
  margin-bottom: 1.5rem;
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
  margin-top: 0.5rem;
}

.login-link {
  text-align: center;
  margin-top: 1rem;
  font-size: 0.95rem;
  color: var(--text-secondary);
}

.login-link .el-link {
  font-weight: 600;
  font-size: 0.95rem;
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .register-content {
    flex-direction: column;
  }
  .register-left {
    padding: 3rem 2rem;
    text-align: center;
  }
  .register-right {
    padding: 2rem;
  }
}
</style>
