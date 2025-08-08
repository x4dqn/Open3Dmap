// Service Worker for Open3DMap Web Platform
const CACHE_NAME = 'open3dmap-v1';
const urlsToCache = [
  '/',
  '/index.html',
  '/styles/main.css',
  '/js/config.js',
  '/js/auth.js',
  '/js/main.js',
  '/assets/open3dmap.png',
  'https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap',
  'https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css'
];

// Install event - cache resources
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => {
        console.log('Opened cache');
        return cache.addAll(urlsToCache);
      })
      .catch((error) => {
        console.error('Cache installation failed:', error);
      })
  );
});

// Fetch event - serve from cache when offline
self.addEventListener('fetch', (event) => {
  event.respondWith(
    caches.match(event.request)
      .then((response) => {
        // Return cached version or fetch from network
        if (response) {
          return response;
        }
        
        // Important: Clone the request because it's a stream
        const fetchRequest = event.request.clone();
        
        return fetch(fetchRequest).then((response) => {
          // Check if valid response
          if (!response || response.status !== 200 || response.type !== 'basic') {
            return response;
          }
          
          // Important: Clone the response because it's a stream
          const responseToCache = response.clone();
          
          caches.open(CACHE_NAME)
            .then((cache) => {
              cache.put(event.request, responseToCache);
            });
          
          return response;
        }).catch(() => {
          // Return offline page for navigation requests
          if (event.request.destination === 'document') {
            return caches.match('/index.html');
          }
        });
      })
  );
});

// Activate event - clean up old caches
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames.map((cacheName) => {
          if (cacheName !== CACHE_NAME) {
            console.log('Deleting old cache:', cacheName);
            return caches.delete(cacheName);
          }
        })
      );
    })
  );
});

// Background sync for uploading scans when online
self.addEventListener('sync', (event) => {
  if (event.tag === 'upload-scan') {
    event.waitUntil(uploadPendingScans());
  }
});

async function uploadPendingScans() {
  // TODO: Implement background sync for pending scan uploads
  console.log('Background sync: uploading pending scans');
}

// Push notification support (for future use)
self.addEventListener('push', (event) => {
  const options = {
    body: 'New scan uploaded successfully!',
    icon: '/assets/logo.svg',
    badge: '/assets/logo.svg'
  };
  
  event.waitUntil(
    self.registration.showNotification('Open3DMap', options)
  );
});

// Notification click handler
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  
  event.waitUntil(
    clients.openWindow('/')
  );
}); 