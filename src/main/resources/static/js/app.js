/**
 * HOT TOPIK - Global JavaScript App Helper
 */

// Toggle Notification Dropdown in TopBar
function toggleBellDropdown() {
    const dropdown = document.getElementById('bellDropdown');
    const userDropdown = document.getElementById('userDropdownMenu');
    if (userDropdown) userDropdown.classList.remove('show');
    if (dropdown) {
        dropdown.classList.toggle('show');
        if (dropdown.classList.contains('show')) refreshNotifications();
    }
}

async function refreshNotifications() {
    const list = document.getElementById('bellDropdownList');
    const badge = document.getElementById('bellBadge');
    if (!list || document.hidden) return;
    try {
        const response = await fetch('/api/notifications/summary', {cache: 'no-store'});
        if (!response.ok) return;
        const data = await response.json();
        if (badge) {
            badge.textContent = data.unreadCount;
            badge.style.display = data.unreadCount > 0 ? 'inline-flex' : 'none';
        }
        list.replaceChildren();
        for (const n of data.notifications) {
            const link = document.createElement('a');
            link.href = n.link && n.link.startsWith('/bao-loi/') ? n.link : '/thong-bao';
            link.className = 'ssak-notification-item' + (n.read ? '' : ' unread');
            const content = document.createElement('div');
            const title = document.createElement('strong');
            const message = document.createElement('div');
            title.textContent = n.title;
            message.textContent = n.message;
            message.className = 'small text-muted';
            content.append(title, message);
            link.append(content);
            link.addEventListener('click', () => fetch('/api/notifications/' + n.id + '/read', {method: 'POST', keepalive: true}));
            list.append(link);
        }
        if (!data.notifications.length) list.textContent = 'Chưa có thông báo mới.';
    } catch (_) { /* Retry on the next poll or when the learner opens the bell. */ }
}
document.addEventListener('DOMContentLoaded', () => {
    refreshNotifications();
    if (document.getElementById('bellDropdownList')) setInterval(refreshNotifications, 30000);
});
document.addEventListener('visibilitychange', () => { if (!document.hidden) refreshNotifications(); });

// Toggle User Profile Dropdown
function toggleUserDropdown(event) {
    if (event) event.stopPropagation();
    const bellDropdown = document.getElementById('bellDropdown');
    if (bellDropdown) bellDropdown.classList.remove('show');
    const dropdown = document.getElementById('userDropdownMenu');
    if (dropdown) {
        dropdown.classList.toggle('show');
    }
}

// Close dropdowns on clicking outside
document.addEventListener('click', function(e) {
    const userDropdown = document.getElementById('userDropdownMenu');
    const userCaretBtn = document.getElementById('userCaretBtn');
    if (userDropdown && userDropdown.classList.contains('show')) {
        if (!userDropdown.contains(e.target) && (!userCaretBtn || !userCaretBtn.contains(e.target))) {
            userDropdown.classList.remove('show');
        }
    }

    const bellDropdown = document.getElementById('bellDropdown');
    const bellBtn = document.getElementById('bellToggleBtn');
    if (bellDropdown && bellDropdown.classList.contains('show')) {
        if (!bellDropdown.contains(e.target) && (!bellBtn || !bellBtn.contains(e.target))) {
            bellDropdown.classList.remove('show');
        }
    }
});

// Mark all notifications as read
function markAllNotificationsRead() {
    const badge = document.getElementById('bellBadge');
    if (badge) {
        badge.style.display = 'none';
    }
    const unreadItems = document.querySelectorAll('.ssak-notification-item.unread');
    unreadItems.forEach(item => {
        item.classList.remove('unread');
        const dot = item.querySelector('span');
        if (dot) dot.style.background = 'transparent';
    });
    showSsakToast('Đã đánh dấu tất cả thông báo là đã đọc!');
}

// Global Toast Popup
function showSsakToast(message, duration = 3500) {
    let toast = document.getElementById('ssak-global-toast');
    if (!toast) {
        toast = document.createElement('div');
        toast.id = 'ssak-global-toast';
        toast.style.cssText = `
            position: fixed;
            bottom: 32px;
            left: 50%;
            transform: translateX(-50%);
            background: #24363D;
            color: #FFFFFF;
            padding: 14px 24px;
            border-radius: 16px;
            font-weight: 800;
            font-size: 15px;
            z-index: 9999;
            box-shadow: 0 10px 25px rgba(0,0,0,0.2);
            transition: opacity 0.3s ease, transform 0.3s ease;
            pointer-events: none;
            display: flex;
            align-items: center;
            gap: 10px;
        `;
        document.body.appendChild(toast);
    }
    toast.innerHTML = `<span>🌱</span> <span>${message}</span>`;
    toast.style.opacity = '1';
    toast.style.transform = 'translateX(-50%) translateY(0)';

    clearTimeout(toast.__timeout);
    toast.__timeout = setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(-50%) translateY(10px)';
    }, duration);
}
window.showSsakToast = showSsakToast;

// Close dropdown on click outside
document.addEventListener('click', function(e) {
    const bellContainer = document.querySelector('[data-bell="1"]');
    const dropdown = document.getElementById('bellDropdown');
    if (bellContainer && dropdown && !bellContainer.contains(e.target)) {
        dropdown.classList.remove('show');
    }
});

// Sound effect player simulator (for practice/quiz)
function playSound(type) {
    try {
        const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
        const osc = audioCtx.createOscillator();
        const gain = audioCtx.createGain();
        osc.connect(gain);
        gain.connect(audioCtx.destination);

        if (type === 'correct') {
            osc.frequency.setValueAtTime(587.33, audioCtx.currentTime); // D5
            osc.frequency.setValueAtTime(880, audioCtx.currentTime + 0.1); // A5
            gain.gain.setValueAtTime(0.15, audioCtx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.3);
            osc.start();
            osc.stop(audioCtx.currentTime + 0.35);
        } else if (type === 'click') {
            osc.frequency.setValueAtTime(400, audioCtx.currentTime);
            gain.gain.setValueAtTime(0.08, audioCtx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.08);
            osc.start();
            osc.stop(audioCtx.currentTime + 0.08);
        }
    } catch(e) {
        // AudioContext not allowed or supported
    }
}
window.playSound = playSound;
