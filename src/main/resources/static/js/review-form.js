// Mỗi học viên chỉ có 1 đánh giá: dùng chung cho trang chủ và trang Về TOPIK.
// - Nạp đánh giá hiện có qua GET /api/feedback/mine (401 = khách, giữ nguyên form "Viết đánh giá").
// - Đã có đánh giá: nút mở form đổi thành "Sửa đánh giá của bạn", form điền sẵn số sao + nội dung,
//   nút gửi thành "Cập nhật đánh giá"; POST /api/feedback/submit sẽ cập nhật thay vì tạo mới.
// Trang cần: nút mở form có class js-open-feedback chứa <span class="js-open-feedback-label">,
// và các id feedbackModalTitle, feedbackSubmitBtn, feedbackComment, feedbackHiddenNote, hàm setRating(n).
(function () {
    let mine = { exists: false };

    function labelFeedbackButtons() {
        document.querySelectorAll('.js-open-feedback .js-open-feedback-label').forEach(el => {
            el.textContent = mine.exists ? 'Sửa đánh giá của bạn' : 'Viết đánh giá của bạn';
        });
    }

    function loadMyReview() {
        return fetch('/api/feedback/mine', { cache: 'no-store' })
            .then(res => res.ok ? res.json() : { exists: false })
            .catch(() => ({ exists: false }))
            .then(data => { mine = data || { exists: false }; labelFeedbackButtons(); return mine; });
    }

    function fillFeedbackModal() {
        const title = document.getElementById('feedbackModalTitle');
        const submit = document.getElementById('feedbackSubmitBtn');
        const comment = document.getElementById('feedbackComment');
        const note = document.getElementById('feedbackHiddenNote');
        if (mine.exists) {
            if (title) title.textContent = 'Sửa đánh giá của bạn';
            if (submit) submit.textContent = 'Cập nhật đánh giá';
            if (comment) comment.value = mine.comment || '';
            if (typeof window.setRating === 'function') window.setRating(mine.rating || 5);
            if (note) note.style.display = mine.visible ? 'none' : 'block';
        } else {
            if (title) title.textContent = 'Đánh giá trải nghiệm';
            if (submit) submit.textContent = 'Gửi đánh giá';
            if (comment) comment.value = '';
            if (typeof window.setRating === 'function') window.setRating(5);
            if (note) note.style.display = 'none';
        }
    }

    window.labelFeedbackButtons = labelFeedbackButtons;
    // Mở form: điền theo dữ liệu đã nạp lúc tải trang (trang tự reload sau mỗi lần gửi nên luôn khớp DB).
    window.fillFeedbackModal = fillFeedbackModal;

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', loadMyReview);
    else loadMyReview();
})();
