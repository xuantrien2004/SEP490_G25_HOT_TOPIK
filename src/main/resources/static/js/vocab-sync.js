/**
 * HOT TOPIK - Vocabulary Real-time Sync & Unlearned Words Engine
 * - Hỗ trợ BroadcastChannel cho đồng bộ tức thời 0ms giữa các tab cùng trình duyệt
 * - Hỗ trợ Server-Sent Events (SSE) để đồng bộ thời gian thực giữa Content Manager và Học viên
 * - Quản lý Sổ từ chưa thuộc (Unlearned words) cho học viên ôn tập riêng
 */
(function() {
    'use strict';

    const SYNC_CHANNEL_NAME = 'hot_topik_vocab_sync';
    const UNLEARNED_STORAGE_KEY = 'hottopik_unlearned_words';
    let broadcastChannel = null;
    let eventSource = null;

    // 1. Khởi tạo BroadcastChannel (Cross-tab sync)
    if (typeof BroadcastChannel !== 'undefined') {
        try {
            broadcastChannel = new BroadcastChannel(SYNC_CHANNEL_NAME);
            broadcastChannel.onmessage = function(event) {
                if (event && event.data) {
                    dispatchSyncEvent(event.data);
                }
            };
        } catch (e) {
            console.warn('[HotTopikSync] BroadcastChannel unsupported:', e);
        }
    }

    // 2. Khởi tạo SSE (Server-Sent Events) kết nối với Backend
    let reconnectDelay = 5000;
    let sseTimeoutId = null;

    function shouldConnectSSE() {
        const path = window.location.pathname.toLowerCase();
        return path.includes('/admin/vocabulary') || 
               path.includes('/content/vocabulary') || 
               path.includes('/ly-thuyet') || 
               path.includes('/theory') ||
               path.includes('/tu-chua-thuoc');
    }

    function connectSSE() {
        if (typeof EventSource === 'undefined') return;
        if (!shouldConnectSSE()) return;

        try {
            if (eventSource) {
                eventSource.close();
                eventSource = null;
            }

            // Endpoint stream đồng bộ
            const streamUrl = window.location.pathname.startsWith('/admin') || window.location.pathname.startsWith('/content')
                ? '/admin/vocabulary/sync/stream'
                : '/api/vocab-sync/stream';

            eventSource = new EventSource(streamUrl);

            eventSource.onopen = function() {
                reconnectDelay = 5000;
            };

            eventSource.addEventListener('vocab_sync', function(e) {
                try {
                    const data = JSON.parse(e.data);
                    dispatchSyncEvent(data);
                } catch (err) {
                    console.error('[HotTopikSync] Error parsing SSE payload:', err);
                }
            });

            eventSource.onerror = function() {
                if (eventSource && eventSource.readyState === EventSource.CLOSED) {
                    if (sseTimeoutId) clearTimeout(sseTimeoutId);
                    sseTimeoutId = setTimeout(() => {
                        reconnectDelay = Math.min(reconnectDelay * 1.5, 30000);
                        connectSSE();
                    }, reconnectDelay);
                }
            };
        } catch (e) {
            console.warn('[HotTopikSync] SSE init failed:', e);
        }
    }

    // Đóng kết nối sạch khi rời trang để không ngốn connection pool của trình duyệt
    window.addEventListener('beforeunload', function() {
        if (eventSource) {
            eventSource.close();
            eventSource = null;
        }
    });
    window.addEventListener('pagehide', function() {
        if (eventSource) {
            eventSource.close();
            eventSource = null;
        }
    });

    function dispatchSyncEvent(data) {
        window.dispatchEvent(new CustomEvent('hottopik:vocab-sync', { detail: data }));
    }

    // 3. Quản lý Sổ từ chưa thuộc (Unlearned Words)
    const UnlearnedManager = {
        getAll: function() {
            try {
                return JSON.parse(localStorage.getItem(UNLEARNED_STORAGE_KEY) || '[]');
            } catch (e) {
                return [];
            }
        },
        has: function(id) {
            const list = this.getAll();
            return list.some(item => String(item.id) === String(id));
        },
        add: function(vocab, source) {
            if (!vocab || !vocab.id) return false;
            let list = this.getAll();
            if (list.some(item => String(item.id) === String(vocab.id))) {
                return false; // Đã có rồi
            }
            list.unshift({
                id: vocab.id,
                word: vocab.word || '',
                romanization: vocab.romanization || '',
                meaning: vocab.meaning || '',
                wordType: vocab.wordType || 'Danh từ',
                topikLevel: vocab.topikLevel || 1,
                topic: vocab.topic || '',
                exampleKr: vocab.exampleKr || '',
                exampleVi: vocab.exampleVi || '',
                audioUrl: vocab.audioUrl || '',
                source: source || 'Thủ công',
                addedAt: new Date().toLocaleDateString('vi-VN') + ' ' + new Date().toLocaleTimeString('vi-VN', {hour: '2-digit', minute:'2-digit'})
            });
            localStorage.setItem(UNLEARNED_STORAGE_KEY, JSON.stringify(list));
            window.dispatchEvent(new CustomEvent('hottopik:unlearned-changed', { detail: { count: list.length } }));
            return true;
        },
        remove: function(id) {
            let list = this.getAll();
            const initialLen = list.length;
            list = list.filter(item => String(item.id) !== String(id));
            if (list.length !== initialLen) {
                localStorage.setItem(UNLEARNED_STORAGE_KEY, JSON.stringify(list));
                window.dispatchEvent(new CustomEvent('hottopik:unlearned-changed', { detail: { count: list.length } }));
                return true;
            }
            return false;
        },
        clear: function() {
            localStorage.removeItem(UNLEARNED_STORAGE_KEY);
            window.dispatchEvent(new CustomEvent('hottopik:unlearned-changed', { detail: { count: 0 } }));
        },
        count: function() {
            return this.getAll().length;
        }
    };

    // Public API
    window.HotTopikSync = {
        broadcast: function(action, payload) {
            const data = Object.assign({ action: action, timestamp: Date.now() }, payload || {});
            if (broadcastChannel) {
                try {
                    broadcastChannel.postMessage(data);
                } catch (e) {
                    // Ignore error
                }
            }
            dispatchSyncEvent(data);
        },
        unlearned: UnlearnedManager
    };

    // Kết nối SSE sau khi trang đã render xong để không làm quay vòng tab
    window.addEventListener('load', function() {
        setTimeout(connectSSE, 600);
    });
})();
