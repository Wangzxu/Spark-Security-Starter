<template>
  <el-container class="index-container">
    <el-header class="header">
      <div class="logo">Spark Security Starter</div>
      <div class="user-info">
        <span class="welcome-text">Welcome, {{ username }}</span>
        <el-button type="warning" size="small" @click="changePwdDialogVisible = true" style="margin-right: 10px;">Change Password</el-button>
        <el-button type="danger" size="small" @click="handleLogout">Logout</el-button>
      </div>
    </el-header>
    <el-main class="main">
      <el-card class="box-card">
        <template #header>
          <div class="card-header">
            <span>Dashboard</span>
          </div>
        </template>
        <div class="content">
          <p>You have successfully logged in!</p>
          <p>This is the protected index page.</p>
          <div style="margin-top: 20px;">
            <el-button type="primary" @click="testPublic">Test Public API</el-button>
            <el-button type="success" @click="testProtected">Test Protected API</el-button>
          </div>
        </div>
      </el-card>
    </el-main>

    <!-- Change Password Dialog -->
    <el-dialog
      title="Change Password"
      v-model="changePwdDialogVisible"
      width="400px"
      @close="resetChangePwdForm"
    >
      <el-form :model="changePwdForm" :rules="changePwdRules" ref="changePwdFormRef" label-width="120px">
        <el-form-item label="Old Password" prop="oldPassword">
          <el-input v-model="changePwdForm.oldPassword" type="password" show-password></el-input>
        </el-form-item>
        <el-form-item label="New Password" prop="newPassword">
          <el-input v-model="changePwdForm.newPassword" type="password" show-password></el-input>
        </el-form-item>
        <el-form-item label="Confirm New" prop="confirmPassword">
          <el-input v-model="changePwdForm.confirmPassword" type="password" show-password></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="changePwdDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="changePwdLoading" @click="handleChangePassword">Confirm</el-button>
        </span>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import Cookies from 'js-cookie'

const router = useRouter()
const username = ref('User')

// Change Password State
const changePwdDialogVisible = ref(false)
const changePwdLoading = ref(false)
const changePwdFormRef = ref(null)
const changePwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== changePwdForm.newPassword) {
    callback(new Error('Two inputs don\'t match!'))
  } else {
    callback()
  }
}

const changePwdRules = reactive({
  oldPassword: [
    { required: true, message: 'Please input old password', trigger: 'blur' },
    { min: 6, max: 20, message: 'Length should be 6 to 20', trigger: 'blur' },
  ],
  newPassword: [
    { required: true, message: 'Please input new password', trigger: 'blur' },
    { min: 6, max: 20, message: 'Length should be 6 to 20', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: 'Please confirm new password', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
})

onMounted(() => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    try {
      const user = JSON.parse(userStr)
      username.value = user.nickname || user.username || 'User'
    } catch (e) {
      console.error(e)
    }
  }
})

const testPublic = async () => {
  try {
    const res = await request.get('/test/public')
    if (res.code === 200) {
      ElMessage.success(res.data)
    } else {
      ElMessage.error(res.message || 'Error calling public API')
    }
  } catch (error) {
    console.error(error)
  }
}

const testProtected = async () => {
  try {
    const res = await request.get('/test/protected')
    if (res.code === 200) {
      ElMessage.success(res.data)
    } else {
      ElMessage.error(res.message || 'Error calling protected API')
    }
  } catch (error) {
    console.error(error)
  }
}

const handleLogout = async () => {
  try {
    const refreshToken = Cookies.get('refreshToken')
    if (refreshToken) {
      await request.post('/auth/logout', { refreshToken })
    }
  } catch (error) {
    console.error('Logout error', error)
  } finally {
    clearAuthAndRedirect()
  }
}

const clearAuthAndRedirect = () => {
  localStorage.removeItem('accessToken')
  Cookies.remove('refreshToken')
  localStorage.removeItem('user')
  ElMessage.success('Logged out successfully')
  router.push('/login')
}

const resetChangePwdForm = () => {
  if (changePwdFormRef.value) {
    changePwdFormRef.value.resetFields()
  }
}

const handleChangePassword = async () => {
  if (!changePwdFormRef.value) return
  await changePwdFormRef.value.validate(async (valid) => {
    if (valid) {
      changePwdLoading.value = true
      try {
        const res = await request.post('/user/change-password', {
          oldPassword: changePwdForm.oldPassword,
          newPassword: changePwdForm.newPassword
        })
        if (res.code === 200) {
          ElMessage.success('Password changed successfully, please login again')
          changePwdDialogVisible.value = false
          // Send logout request to backend as well to be safe, then clear local
          const refreshToken = Cookies.get('refreshToken')
          if (refreshToken) {
            await request.post('/auth/logout', { refreshToken }).catch(() => {})
          }
          clearAuthAndRedirect()
        } else {
          ElMessage.error(res.message || 'Failed to change password')
        }
      } catch (error) {
        console.error('Change password error', error)
      } finally {
        changePwdLoading.value = false
      }
    }
  })
}
</script>

<style scoped>
.index-container {
  height: 100vh;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #304156;
  color: white;
  padding: 0 20px;
}

.logo {
  font-size: 24px;
  font-weight: bold;
}

.user-info {
  display: flex;
  align-items: center;
}

.welcome-text {
  margin-right: 20px;
  font-size: 16px;
}

.main {
  background-color: #f0f2f5;
  padding: 20px;
}

.box-card {
  width: 100%;
  max-width: 800px;
  margin: 0 auto;
}

.content {
  font-size: 16px;
  line-height: 1.5;
}
</style>
