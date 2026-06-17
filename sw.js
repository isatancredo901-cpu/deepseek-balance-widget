// DeepSeek Balance Widget - Service Worker
const CACHE_NAME = 'deepseek-widget-v2';

// 安装：不缓存 index.html，确保用户始终拿到最新版本
self.addEventListener('install', event => {
    self.skipWaiting();
});

// 激活：清理所有旧缓存
self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys().then(keys =>
            Promise.all(keys.map(k => caches.delete(k)))
        )
    );
    self.clients.claim();
});

// 请求：对 HTML 用网络优先，其他用缓存兜底
self.addEventListener('fetch', event => {
    if (event.request.url.includes('api.deepseek.com')) return;

    // HTML 请求：网络优先（确保拿到最新代码）
    if (event.request.destination === 'document' || event.request.url.endsWith('.html')) {
        event.respondWith(
            fetch(event.request).catch(() => caches.match(event.request))
        );
        return;
    }

    // 其他静态资源：缓存优先
    event.respondWith(
        caches.match(event.request).then(cached =>
            cached || fetch(event.request).then(response => {
                const clone = response.clone();
                caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
                return response;
            })
        )
    );
});
