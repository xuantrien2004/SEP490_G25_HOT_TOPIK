// Nút ⭐ "Sổ ngữ pháp" dùng chung cho trang chi tiết ngữ pháp, danh sách theo nhóm và trang Ngữ pháp đã lưu.
// Mỗi nút có class save-grammar-btn, data-id (id ngữ pháp) và data-saved ("true"/"false").
(function () {
    function paint(btn) {
        const saved = btn.getAttribute('data-saved') === 'true';
        const compact = btn.classList.contains('save-grammar-compact');
        btn.textContent = saved ? '★ Đã lưu' : (compact ? '☆ Lưu' : '⭐ Lưu ngữ pháp');
        btn.title = saved ? 'Bỏ lưu khỏi Sổ ngữ pháp' : 'Lưu vào Sổ ngữ pháp để ôn lại';
        btn.setAttribute('aria-pressed', String(saved));
        btn.style.border = saved ? '1.5px solid #F4C45A' : '1.5px solid #D0D0C8';
        btn.style.background = saved ? '#FBF0D3' : '#FFFFFF';
        btn.style.color = saved ? '#9A6B00' : '#24363D';
    }

    function toast(message) {
        let el = document.getElementById('savedGrammarToast');
        if (!el) {
            el = document.createElement('div');
            el.id = 'savedGrammarToast';
            el.setAttribute('role', 'status');
            el.style.cssText = 'position:fixed;right:24px;bottom:24px;z-index:3000;padding:12px 18px;border-radius:12px;'
                + 'background:#24363D;color:#fff;font-weight:700;font-size:14px;box-shadow:0 8px 24px rgba(0,0,0,.18);display:none;';
            document.body.appendChild(el);
        }
        el.textContent = message;
        el.style.display = 'block';
        clearTimeout(el._timer);
        el._timer = setTimeout(() => el.style.display = 'none', 2400);
    }

    window.toggleSaveGrammar = function (btn, event) {
        if (event) event.stopPropagation();
        const id = btn.getAttribute('data-id');
        if (!id || btn.disabled) return;
        btn.disabled = true;
        fetch('/api/theory/grammar/' + encodeURIComponent(id) + '/save', { method: 'POST' })
            .then(res => {
                if (res.status === 401) {
                    toast('Vui lòng đăng nhập để lưu ngữ pháp.');
                    setTimeout(() => window.location.href = '/login', 1200);
                    return null;
                }
                return res.json();
            })
            .then(data => {
                if (!data) return;
                if (!data.success) { toast(data.message || 'Không thể lưu ngữ pháp.'); return; }
                // Cập nhật mọi nút cùng id trên trang (vd. thẻ trong danh sách và popup)
                document.querySelectorAll('.save-grammar-btn[data-id="' + id + '"]').forEach(b => {
                    b.setAttribute('data-saved', String(data.saved));
                    paint(b);
                });
                toast(data.message);
                window.dispatchEvent(new CustomEvent('hottopik:saved-grammar-changed', { detail: { id: id, saved: data.saved } }));
            })
            .catch(() => toast('Không thể kết nối máy chủ.'))
            .finally(() => { btn.disabled = false; });
    };

    function init() {
        document.querySelectorAll('.save-grammar-btn').forEach(paint);
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
    else init();
})();
