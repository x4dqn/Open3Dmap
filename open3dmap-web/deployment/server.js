const express = require('express');
const cors = require('cors');
const path = require('path');
const fs = require('fs');
require('dotenv').config();

// Enhanced server with production features
class ProductionServer {
    constructor() {
        this.app = express();
        this.port = process.env.PORT || 3000;
        this.host = process.env.HOST || '0.0.0.0';
        this.isProduction = process.env.NODE_ENV === 'production';
        
        this.setupMiddleware();
        this.setupRoutes();
        this.setupErrorHandling();
        this.setupGracefulShutdown();
    }

    setupMiddleware() {
        // Security middleware
        this.app.use((req, res, next) => {
            res.setHeader('X-Powered-By', 'Open3DMapWeb');
            res.setHeader('X-Content-Type-Options', 'nosniff');
            res.setHeader('X-Frame-Options', 'DENY');
            res.setHeader('X-XSS-Protection', '1; mode=block');
            next();
        });

        // CORS configuration
        const corsOptions = {
            origin: this.isProduction 
                ? (process.env.CORS_ORIGINS || '').split(',').filter(Boolean)
                : true,
            credentials: true,
            optionsSuccessStatus: 200
        };
        this.app.use(cors(corsOptions));

        // Body parsing
        this.app.use(express.json({ limit: process.env.MAX_UPLOAD_SIZE || '50mb' }));
        this.app.use(express.urlencoded({ extended: true, limit: process.env.MAX_UPLOAD_SIZE || '50mb' }));

        // Static file serving with cache headers
        this.app.use(express.static(__dirname, {
            maxAge: this.isProduction ? '1y' : '0',
            etag: true,
            lastModified: true,
            setHeaders: (res, path) => {
                if (path.endsWith('.html')) {
                    res.setHeader('Cache-Control', 'no-cache, must-revalidate');
                } else if (path.match(/\.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot|webp|wasm)$/)) {
                    res.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
                }
            }
        }));

        // Request logging
        this.app.use((req, res, next) => {
            const start = Date.now();
            res.on('finish', () => {
                const duration = Date.now() - start;
                if (!req.url.includes('/health')) {
                    console.log(`${new Date().toISOString()} ${req.method} ${req.url} ${res.statusCode} ${duration}ms`);
                }
            });
            next();
        });
    }

    setupRoutes() {
        // Health check endpoint
        this.app.get('/health', (req, res) => {
            const healthInfo = {
                status: 'ok',
                timestamp: new Date().toISOString(),
                uptime: process.uptime(),
                memory: process.memoryUsage(),
                version: process.env.npm_package_version || '1.0.0',
                environment: process.env.NODE_ENV || 'development'
            };
            res.json(healthInfo);
        });

        // Metrics endpoint (basic)
        this.app.get('/metrics', (req, res) => {
            const metrics = {
                timestamp: new Date().toISOString(),
                process: {
                    uptime: process.uptime(),
                    memory: process.memoryUsage(),
                    cpu: process.cpuUsage(),
                    pid: process.pid,
                    version: process.version
                },
                system: {
                    loadavg: require('os').loadavg(),
                    freemem: require('os').freemem(),
                    totalmem: require('os').totalmem(),
                    cpus: require('os').cpus().length
                }
            };
            res.json(metrics);
        });

        // API routes
        if (fs.existsSync(path.join(__dirname, 'api'))) {
            try {
                const colmapRouter = require('./api/colmap/convert');
                const uploadRouter = require('./api/colmap/upload');
                this.app.use('/api/colmap', colmapRouter);
                this.app.use('/api/colmap', uploadRouter);
                console.log('✅ API routes loaded');
            } catch (error) {
                console.warn('⚠️  Failed to load API routes:', error.message);
            }
        }

        // Serve different pages based on route
        this.app.get('/', (req, res) => {
            res.sendFile(path.join(__dirname, 'index.html'));
        });

        this.app.get('/dashboard', (req, res) => {
            res.sendFile(path.join(__dirname, 'dashboard.html'));
        });

        this.app.get('/brush-trainer', (req, res) => {
            if (fs.existsSync(path.join(__dirname, 'brush-trainer.html'))) {
                res.sendFile(path.join(__dirname, 'brush-trainer.html'));
            } else {
                res.status(404).send('Brush trainer not available');
            }
        });

        // Catch-all for SPA routing
        this.app.get('*', (req, res) => {
            // Don't interfere with API routes
            if (req.url.startsWith('/api/')) {
                res.status(404).json({ error: 'API endpoint not found' });
                return;
            }
            
            // Serve index.html for all other routes (SPA)
            res.sendFile(path.join(__dirname, 'index.html'));
        });
    }

    setupErrorHandling() {
        // 404 handler
        this.app.use((req, res) => {
            res.status(404).json({
                error: 'Not Found',
                message: `The requested resource ${req.url} was not found on this server.`,
                timestamp: new Date().toISOString()
            });
        });

        // Global error handler
        this.app.use((err, req, res, next) => {
            console.error('Error occurred:', err);
            
            // Don't leak error details in production
            const errorResponse = {
                error: 'Internal Server Error',
                timestamp: new Date().toISOString()
            };

            if (!this.isProduction) {
                errorResponse.details = err.message;
                errorResponse.stack = err.stack;
            }

            res.status(err.status || 500).json(errorResponse);
        });

        // Handle uncaught exceptions
        process.on('uncaughtException', (err) => {
            console.error('Uncaught Exception:', err);
            if (this.isProduction) {
                process.exit(1);
            }
        });

        // Handle unhandled promise rejections
        process.on('unhandledRejection', (reason, promise) => {
            console.error('Unhandled Rejection at:', promise, 'reason:', reason);
            if (this.isProduction) {
                process.exit(1);
            }
        });
    }

    setupGracefulShutdown() {
        const gracefulShutdown = (signal) => {
            console.log(`\n${signal} received. Starting graceful shutdown...`);
            
            this.server.close((err) => {
                if (err) {
                    console.error('Error during server shutdown:', err);
                    process.exit(1);
                }
                
                console.log('Server closed successfully.');
                process.exit(0);
            });

            // Force shutdown after 10 seconds
            setTimeout(() => {
                console.error('Force shutdown due to timeout');
                process.exit(1);
            }, 10000);
        };

        process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
        process.on('SIGINT', () => gracefulShutdown('SIGINT'));
    }

    start() {
        this.server = this.app.listen(this.port, this.host, () => {
            console.log(`🚀 Open3DMapWeb server started`);
            console.log(`📍 Environment: ${process.env.NODE_ENV || 'development'}`);
            console.log(`🌐 Server running at http://${this.host}:${this.port}`);
            console.log(`💾 Memory usage: ${Math.round(process.memoryUsage().heapUsed / 1024 / 1024)}MB`);
            console.log(`⏰ Started at: ${new Date().toISOString()}`);
            
            if (this.isProduction) {
                console.log('🔒 Running in production mode');
            } else {
                console.log('🛠️  Running in development mode');
            }
        });

        this.server.on('error', (err) => {
            if (err.code === 'EADDRINUSE') {
                console.error(`❌ Port ${this.port} is already in use`);
            } else {
                console.error('❌ Server error:', err);
            }
            process.exit(1);
        });

        // Handle server timeout
        this.server.timeout = 60000; // 60 seconds
        
        return this.server;
    }
}

// Start the server
if (require.main === module) {
    const server = new ProductionServer();
    server.start();
}

module.exports = ProductionServer; 