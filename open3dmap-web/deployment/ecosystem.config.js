module.exports = {
  apps: [
    {
      name: 'open3dmap-web',
      script: 'server.js',
      cwd: '/var/www/open3dmap',
      instances: 'max', // Use all CPU cores
      exec_mode: 'cluster',
      env: {
        NODE_ENV: 'production',
        PORT: 3000
      },
      env_production: {
        NODE_ENV: 'production',
        PORT: 3000
      },
      // Logging
      log_file: '/var/log/open3dmap/combined.log',
      out_file: '/var/log/open3dmap/out.log',
      error_file: '/var/log/open3dmap/error.log',
      log_date_format: 'YYYY-MM-DD HH:mm:ss Z',
      
      // Auto restart configuration
      watch: false, // Don't watch in production
      ignore_watch: ['node_modules', 'logs', 'temp'],
      restart_delay: 5000,
      max_restarts: 10,
      min_uptime: '10s',
      
      // Memory management
      max_memory_restart: '1G',
      
      // Process management
      kill_timeout: 5000,
      wait_ready: true,
      listen_timeout: 10000,
      
      // Advanced PM2 features
      instance_var: 'INSTANCE_ID',
      merge_logs: true,
      
      // Health monitoring
      health_check_grace_period: 3000,
      health_check_fatal: true,
      
      // Autorestart configuration
      autorestart: true,
      
      // Environment variables
      env_file: '.env'
    }
  ],

  deploy: {
    production: {
      user: 'deploy',
      host: ['your-server.com'],
      ref: 'origin/main',
      repo: 'https://github.com/your-repo/open3dmap-web.git',
      path: '/var/www/open3dmap',
      'pre-deploy-local': '',
      'post-deploy': 'npm install && pm2 reload ecosystem.config.js --env production',
      'pre-setup': ''
    }
  }
}; 