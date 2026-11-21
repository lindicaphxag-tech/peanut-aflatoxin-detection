const CONFIG = require('./config.js')

App({
  globalData: {
    apiUrl: CONFIG.API_BASE,
    timeout: CONFIG.REQUEST_TIMEOUT
  }
})
