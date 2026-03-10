import axios from 'axios'
import { ElMessage } from 'element-plus'
import Cookies from 'js-cookie'

const request = axios.create({
  baseURL: '/api',
  timeout: 5000
})

// Constants for token keys
const ACCESS_TOKEN_KEY = 'accessToken'
const REFRESH_TOKEN_KEY = 'refreshToken'

// State for silent refresh
let isRefreshing = false
let requestsQueue = []

// Request interceptor
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem(ACCESS_TOKEN_KEY)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// Response interceptor
request.interceptors.response.use(
  response => {
    const res = response.data
    // Assuming backend returns { code: 200, message: '...', data: ... }
    
    // Check if the business logic returns 401
    if (res.code && res.code !== 200) {
      if (res.code === 401 && !response.config._retry) {
        response.config._retry = true
        return handleUnauthorized(response.config)
      }
      
      ElMessage.error(res.message || 'Error')
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res
  },
  error => {
    // Check if the HTTP status is 401
    if (error.response && error.response.status === 401 && !error.config._retry) {
      error.config._retry = true
      return handleUnauthorized(error.config)
    }
    
    ElMessage.error(error.message || 'Request Error')
    return Promise.reject(error)
  }
)

/**
 * Handles 401 Unauthorized responses by attempting to refresh the token.
 * 
 * @param {Object} originalConfig - The original axios request configuration
 * @returns {Promise} - Resolves with the retried request or rejects on failure
 */
function handleUnauthorized(originalConfig) {
  if (!isRefreshing) {
    isRefreshing = true
    const refreshToken = Cookies.get(REFRESH_TOKEN_KEY)
    
    if (!refreshToken) {
      isRefreshing = false
      return redirectToLogin()
    }

    // Call refresh API directly with axios to avoid interceptor loop
    return axios.post('/api/auth/refresh', { refreshToken })
      .then(refreshRes => {
        const refreshData = refreshRes.data
        if (refreshData.code === 200) {
          const newAccessToken = refreshData.data.accessToken
          const newRefreshToken = refreshData.data.refreshToken
          
          localStorage.setItem(ACCESS_TOKEN_KEY, newAccessToken)
          Cookies.set(REFRESH_TOKEN_KEY, newRefreshToken, { expires: 7 })
          
          // Update the original request's authorization header
          originalConfig.headers.Authorization = `Bearer ${newAccessToken}`
          
          isRefreshing = false
          
          // Retry queued requests
          requestsQueue.forEach(cb => cb(newAccessToken, false))
          requestsQueue = []
          
          // Retry the original request
          return request(originalConfig)
        } else {
          throw new Error(refreshData.message || 'Refresh token invalid')
        }
      })
      .catch(err => {
        isRefreshing = false
        return redirectToLogin(err)
      })
  } else {
    // If a refresh is already in progress, queue the request
    return new Promise((resolve, reject) => {
      requestsQueue.push((tokenOrErr, isErr) => {
        if (isErr) {
          reject(tokenOrErr)
        } else {
          originalConfig.headers.Authorization = `Bearer ${tokenOrErr}`
          resolve(request(originalConfig))
        }
      })
    })
  }
}

/**
 * Clears local storage and redirects to the login page.
 * 
 * @param {Error} err - Optional error object to reject the promise with
 * @returns {Promise} - Rejects with an error
 */
function redirectToLogin(err = new Error('Unauthorized')) {
  // Reject all queued requests so they don't hang
  requestsQueue.forEach(cb => cb(err, true))
  requestsQueue = []
  
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  Cookies.remove(REFRESH_TOKEN_KEY)
  localStorage.removeItem('user')
  window.location.href = '/login'
  return Promise.reject(err)
}

export default request