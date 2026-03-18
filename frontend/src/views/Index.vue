<template>
  <el-container class="layout-container">
    <!-- Sidebar -->
    <el-aside width="240px" class="sidebar">
      <div class="logo">
        <div class="logo-icon">S</div>
        <span class="logo-text">Spark Security</span>
      </div>
      <el-menu default-active="1" class="sidebar-menu" background-color="transparent" text-color="#cbd5e1" active-text-color="#ffffff">
        <el-menu-item index="1">
          <el-icon><Monitor /></el-icon>
          <span>Dashboard</span>
        </el-menu-item>
        <el-menu-item index="2">
          <el-icon><Setting /></el-icon>
          <span>Settings</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="main-container">
      <!-- Header -->
      <el-header class="top-header">
        <div class="header-left">
          <h2 class="page-title">Dashboard</h2>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click">
            <span class="user-dropdown">
              <el-avatar :size="32" class="avatar">{{ username.charAt(0).toUpperCase() }}</el-avatar>
              <span class="username">{{ username }}</span>
              <el-icon class="el-icon--right"><arrow-down /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="changePwdDialogVisible = true">Change Password</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout" style="color: #ef4444;">Logout</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- Main Content -->
      <el-main class="content-area">
        <div class="welcome-banner">
          <div class="welcome-text">
            <h3>Welcome back, {{ username }}!</h3>
            <p>Here is what's happening with your projects today.</p>
          </div>
        </div>

        <el-row :gutter="24" class="stats-row">
          <el-col :span="8">
            <el-card class="stat-card" shadow="hover">
              <div class="stat-title">Total Users</div>
              <div class="stat-value">1,234</div>
              <div class="stat-change text-green">+12% from last month</div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card class="stat-card" shadow="hover">
              <div class="stat-title">Active Sessions</div>
              <div class="stat-value">423</div>
              <div class="stat-change text-green">+5% from last month</div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card class="stat-card" shadow="hover">
              <div class="stat-title">Security Alerts</div>
              <div class="stat-value">2</div>
              <div class="stat-change text-red">-1 from last month</div>
            </el-card>
          </el-col>
        </el-row>

        <el-card class="action-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>API Testing Hub</span>
            </div>
          </template>
          <div class="action-content">
            <p class="action-desc">Use the buttons below to test the connection to the backend APIs. Verify that public and protected routes are functioning correctly.</p>
            <div class="action-buttons">
              <el-button type="primary" plain @click="testPublic">
                <el-icon><Unlock /></el-icon> Test Public API
              </el-button>
              <el-button type="success" @click="testProtected">
                <el-icon><Lock /></el-icon> Test Protected API
              </el-button>
            </div>
          </div>
        </el-card>
      </el-main>
    </el-container>

    <!-- Change Password Dialog -->
    <el-dialog
      title="Change Password"
      v-model="changePwdDialogVisible"
      width="400px"
      @close="resetChangePwdForm"
      class="modern-dialog"
    >
      <el-form :model="changePwdForm" :rules="changePwdRules" ref="changePwdFormRef" label-position="top">
        <el-form-item label="Old Password" prop="oldPassword">
          <el-input v-model="changePwdForm.oldPassword" type="password" show-password placeholder="Enter current password"></el-input>
        </el-form-item>
        <el-form-item label="New Password" prop="newPassword">
          <el-input v-model="changePwdForm.newPassword" type="password" show-password placeholder="Enter new password"></el-input>
        </el-form-item>
        <el-form-item label="Confirm New Password" prop="confirmPassword">
          <el-input v-model="changePwdForm.confirmPassword" type="password" show-password placeholder="Confirm new password"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="changePwdDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="changePwdLoading" @click="handleChangePassword">Update Password</el-button>
        </span>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Monitor, Setting, Unlock, Lock, ArrowDown } from '@element-plus/icons-vue'
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
    callback(new Error('Passwords do not match!'))
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
.layout-container {
  height: 100vh;
  display: flex;
}

.sidebar {
  background-color: #1e1b4b; /* Indigo 950 */
  color: white;
  display: flex;
  flex-direction: column;
  transition: width 0.3s;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.05);
  z-index: 10;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo-icon {
  width: 32px;
  height: 32px;
  background-color: var(--primary-color);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 18px;
  margin-right: 12px;
  color: white;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.sidebar-menu {
  border-right: none;
  margin-top: 10px;
}

.sidebar-menu :deep(.el-menu-item) {
  margin: 4px 12px;
  border-radius: 8px;
  height: 48px;
  line-height: 48px;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background-color: rgba(255, 255, 255, 0.1) !important;
  font-weight: 600;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background-color: rgba(255, 255, 255, 0.05) !important;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-color);
  overflow: hidden;
}

.top-header {
  height: 64px !important;
  background-color: #ffffff;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
}

.page-title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
}

.user-dropdown {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background-color 0.2s;
}

.user-dropdown:hover {
  background-color: #f3f4f6;
}

.avatar {
  background-color: var(--primary-color);
  color: white;
  font-weight: 600;
  margin-right: 10px;
}

.username {
  font-size: 0.95rem;
  font-weight: 500;
  color: var(--text-primary);
  margin-right: 6px;
}

.content-area {
  padding: 24px;
  overflow-y: auto;
}

.welcome-banner {
  background: linear-gradient(135deg, #ffffff 0%, #f8fafc 100%);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.welcome-text h3 {
  margin: 0 0 8px 0;
  font-size: 1.5rem;
  color: var(--text-primary);
}

.welcome-text p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 1rem;
}

.stats-row {
  margin-bottom: 24px;
}

.stat-card {
  padding: 20px;
}

.stat-title {
  color: var(--text-secondary);
  font-size: 0.9rem;
  font-weight: 500;
  margin-bottom: 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat-value {
  font-size: 2rem;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.stat-change {
  font-size: 0.85rem;
  font-weight: 500;
}

.text-green {
  color: #10b981;
}

.text-red {
  color: #ef4444;
}

.action-card {
  margin-bottom: 24px;
}

.card-header {
  font-weight: 600;
  font-size: 1.1rem;
}

.action-content {
  padding: 10px 0;
}

.action-desc {
  color: var(--text-secondary);
  margin-bottom: 20px;
  line-height: 1.6;
}

.action-buttons .el-button {
  margin-right: 12px;
  padding: 10px 20px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.modern-dialog :deep(.el-dialog) {
  border-radius: 12px;
  overflow: hidden;
}

.modern-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  border-bottom: 1px solid var(--border-color);
  padding: 20px 24px;
}

.modern-dialog :deep(.el-dialog__body) {
  padding: 24px;
}

.modern-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid var(--border-color);
  padding: 16px 24px;
}
</style>
