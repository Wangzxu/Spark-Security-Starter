<template>
  <el-container class="index-container">
    <el-header class="header">
      <div class="logo">Spark Security Starter</div>
      <div class="user-info">
        <span class="welcome-text">Welcome, {{ username }}</span>
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
        </div>
      </el-card>
    </el-main>
  </el-container>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()
const username = ref('User')

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

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  ElMessage.success('Logged out successfully')
  router.push('/login')
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
