// DeepSeek Balance Widget - Service Worker
const CACHE_NAME = 'deepseek-widget-v1';
const ASSETS = [
    './index.html',
    './manifest.json'
];

// 安装：缓存静态资源
self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME).then(cache => cache.addAll(ASSETS))
    );
    self.skipWaiting();
});

// 激活：清理旧缓存
self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys().then(keys =>
            Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
        )
    );
    self.clients.claim();
});

// 请求：缓存优先，网络回退
self.addEventListener('fetch', event => {
    // 不缓存 API 请求
    if (event.request.url.includes('api.deepseek.com')) return;

    event.respondWith(
        caches.match(event.request).then(cached =>
            cached || fetch(event.request)
        )
    );
});
