(() => {
    'use strict';
    document.querySelectorAll('.qp-player').forEach(root => {
        const cards = [...root.querySelectorAll('.qp-question')];
        if (!cards.length) return;
        const form = root.closest('form');
        const preview = root.dataset.preview === 'true';
        const numbers = [...root.querySelectorAll('.qp-number')];
        // Mỗi lượt có form action/path riêng nên state chuyển câu/đánh dấu không dùng chung giữa các bài.
        // sessionStorage chỉ giữ trong phiên tab; bài thi vẫn lấy câu trả lời từ backend autosave, luyện dạng/preview có phục hồi cục bộ.
        const storageKey = 'hottopik-player:' + (form?.getAttribute('action') || location.pathname);
        let state = {}, current = 0, confirming = false, pendingSubmitter = null;
        try { state = JSON.parse(sessionStorage.getItem(storageKey) || '{}'); } catch (_) { /* Optional local recovery. */ }
        if (!state || typeof state !== 'object') state = {};
        let locked = {};
        try { locked = preview ? (state.checked || {}) : JSON.parse(root.dataset.checkedJson || '{}'); } catch (_) {}
        const groupSticky = root.querySelector('.qp-group-sticky');
        const groupRange = groupSticky?.querySelector('.qp-group-range');
        const groupInstruction = groupSticky?.querySelector('.qp-group-instruction');
        const groupAudio = groupSticky?.querySelector('.qp-group-audio');
        const groupPassage = groupSticky?.querySelector('.qp-group-passage');
        const groupImage = groupSticky?.querySelector('.qp-group-image');
        const groupToggle = groupSticky?.querySelector('.qp-group-toggle');
        let activeGroup = null;
        if (groupToggle && groupSticky) {
            groupToggle.addEventListener('click', () => {
                const collapsed = groupSticky.classList.toggle('is-collapsed');
                const textEl = groupToggle.querySelector('.qp-toggle-text');
                const iconEl = groupToggle.querySelector('.qp-toggle-icon');
                if (textEl) textEl.textContent = collapsed ? 'Mở rộng' : 'Thu gọn';
                if (iconEl) iconEl.textContent = collapsed ? '▼' : '▲';
                groupToggle.setAttribute('aria-expanded', String(!collapsed));
            });
        }
        function lock(card, selected) {
            card.dataset.checked=String(selected);
            card.querySelectorAll('input[type=radio]').forEach(input=>{ input.checked=input.value===String(selected); input.disabled=true; });
            let hidden=card.querySelector('.qp-locked-value');
            if(!hidden){hidden=document.createElement('input');hidden.type='hidden';hidden.className='qp-locked-value';hidden.name=card.dataset.key;card.append(hidden);}
            hidden.value=selected;
        }
        const keys = new Set(cards.map(card => card.dataset.key));
        let savedFlags = [];
        try { savedFlags = JSON.parse(root.dataset.flagsJson || '[]'); } catch (_) {}
        const flagged = new Set(((Array.isArray(state.flagged) && state.flagged.length) ? state.flagged : savedFlags).filter(key => keys.has(key)));
        function persist() {
            const answers = Object.fromEntries(cards.map(card => [card.dataset.key, card.querySelector('input[type=radio]:checked')?.value || '']));
            try { sessionStorage.setItem(storageKey, JSON.stringify({answers, flagged: [...flagged], current, checked: preview ? Object.fromEntries(cards.filter(c=>c.dataset.checked).map(c=>[c.dataset.key,c.dataset.checked])) : {}})); } catch (_) { /* Storage can be unavailable. */ }
        }
        // Đã trả lời tính radio đã chọn và textarea Viết có nội dung (nếu đề hỗn hợp).
        // Chưa chắc là dấu độc lập, có thể chồng với đã trả lời/chưa làm nên ba số không cộng thành tổng câu.
        function counts() {
            const writing = [...(form?.querySelectorAll('.qp-writing textarea') || [])];
            const answered = cards.filter(c => c.querySelector('input:checked')).length + writing.filter(t => t.value.trim()).length;
            return {answered, empty: cards.length + writing.length - answered, marked: flagged.size};
        }
        // Đồng bộ lựa chọn, nút Kiểm tra, trạng thái đánh dấu, ma trận số câu và tiến độ; persist không gọi API chấm/lưu điểm.
        function update() {
            cards.forEach((card, i) => {
                const checked = card.querySelector('input:checked');
                card.querySelectorAll('.qp-option').forEach(option => option.classList.toggle('selected', !!option.querySelector('input:checked')));
                const marked = flagged.has(card.dataset.key);
                card.querySelector('.qp-flag').setAttribute('aria-pressed', String(marked));
                card.querySelector('.qp-flag').textContent = marked ? 'Bỏ “Chưa chắc”' : 'Chưa chắc';
                card.querySelector('.qp-flag-note').hidden = !marked;
                let flagInput = card.querySelector('.qp-flag-input');
                if (marked) {
                    if (!flagInput) {
                        flagInput = document.createElement('input');
                        flagInput.type = 'hidden';
                        flagInput.className = 'qp-flag-input';
                        flagInput.name = 'flag_' + card.dataset.key;
                        flagInput.value = '1';
                        card.append(flagInput);
                    }
                } else if (flagInput) {
                    flagInput.remove();
                }
                let emptyInput = card.querySelector('.qp-empty-input');
                if (!checked) {
                    if (!emptyInput) {
                        emptyInput = document.createElement('input');
                        emptyInput.type = 'hidden';
                        emptyInput.className = 'qp-empty-input';
                        emptyInput.name = card.dataset.key;
                        emptyInput.value = '';
                        card.append(emptyInput);
                    }
                } else if (emptyInput) {
                    emptyInput.remove();
                }
                card.querySelector('.qp-check').hidden = !checked || card.dataset.checked === checked.value;
                numbers[i].classList.toggle('answered', !!checked);
                numbers[i].classList.toggle('flagged', marked);
                numbers[i].classList.toggle('current', i === current);
                const qNo = card.dataset.no || (i + 1);
                numbers[i].setAttribute('aria-label', `Câu ${qNo}: ${checked ? 'Đã trả lời' : 'Chưa làm'}${marked ? ', chưa chắc' : ''}`);
                if (i === current) numbers[i].setAttribute('aria-current', 'step'); else numbers[i].removeAttribute('aria-current');
            });
            const answered = cards.filter(c => c.querySelector('input:checked')).length;
            root.querySelector('.qp-progress').textContent = `Đã làm ${answered}/${cards.length} câu`;
            persist();
        }
        // Hien thi cau chi dinh va tat ca cau cung nhom (chia se audio/passage/image); cac input van trong form.
        // Hien thi day du doan van/audio/hinh anh/huong dan tren tung the cau trong nhom (khong dung khung sticky).
        function show(index, scroll = true) {
            if (index < 0 || index >= cards.length) return;
            const targetGroup = cards[index].dataset.group;
            const matchingCards = (targetGroup && !targetGroup.startsWith('ng-'))
                ? cards.filter(c => c.dataset.group === targetGroup)
                : [];
            // Chi ap dung nhom khi co tu 2 cau tro len dung chung noi dung (2 hay 3 cau)
            const isGrouped = matchingCards.length >= 2;
            const groupCards = isGrouped ? matchingCards : [];
            const lastInGroup = isGrouped ? groupCards[groupCards.length - 1] : null;
            root.classList.toggle('qp-group-mode', isGrouped);

            if (groupSticky) {
                groupSticky.hidden = true;
                if (groupAudio) {
                    groupAudio.querySelectorAll('audio').forEach(a => a.pause());
                    groupAudio.innerHTML = '';
                }
            }

            cards.forEach((card, i) => {
                const inGroup = isGrouped && card.dataset.group === targetGroup;
                card.hidden = !inGroup && i !== index;
                // Hien thi lai doan van, audio, anh, huong dan cho tung the cau trong nhom
                const audioEl = card.querySelector('.qp-audio');
                if (audioEl) audioEl.hidden = false;
                const passageEl = card.querySelector('.qp-passage');
                if (passageEl) passageEl.hidden = false;
                const imgEl = card.querySelector('.qp-question-image');
                if (imgEl) imgEl.hidden = false;
                const instrEl = card.querySelector('.qp-instruction');
                if (instrEl) instrEl.hidden = false;
                // Chi an nav (qp-actions), giu nguyen qp-per-card-actions (flag/report/check)
                const navEl = card.querySelector('.qp-actions');
                if (navEl) navEl.hidden = inGroup ? (card !== lastInGroup) : false;

                if (card.hidden) card.querySelectorAll('audio').forEach(a => a.pause());
            });
            current = index;
            update();
            if (scroll) {
                const scrollTarget = cards[index];
                scrollTarget?.scrollIntoView({behavior: 'smooth', block: 'start'});
            }
        }
        cards.forEach((card, i) => {
            // Exam selections come from its autosave backend; local recovery is only for practice/preview.
            if (!form?.hasAttribute('data-exam-form')) {
                const saved = state.answers?.[card.dataset.key];
                const input = [...card.querySelectorAll('input[type=radio]')].find(radio => radio.value === saved);
                if (input) input.checked = true;
            }
            const checkedValue=locked[card.dataset.key] || locked[card.dataset.key.replace('answer_', '')];
            if(checkedValue) lock(card, checkedValue);
            const options = card.querySelector('.qp-options');
            const images = !!options.querySelector('img');
            options.classList.toggle('qp-images', images);
            const longText = [...options.querySelectorAll('.qp-option-content')].some(o => o.textContent.trim().length > 38);
            options.classList.toggle('qp-stacked', !images && longText);
            card.querySelectorAll('.qp-audio audio').forEach(audio => {
                audio.addEventListener('play', () => {
                    root.querySelectorAll('.qp-audio audio').forEach(other => {
                        if (other !== audio && !other.paused) other.pause();
                    });
                });
            });
            card.querySelector('.qp-prev').addEventListener('click', () => {
                const myGroup = card.dataset.group;
                const myMatching = (myGroup && !myGroup.startsWith('ng-')) ? cards.filter(c => c.dataset.group === myGroup) : [];
                if (myMatching.length >= 2) {
                    // Nut prev o cuoi group -> nhay ve cau truoc dau group
                    let groupStart = i;
                    while (groupStart > 0 && cards[groupStart-1].dataset.group === myGroup) groupStart--;
                    let target = groupStart - 1;
                    if (target < 0) return;
                    const prevGroup = cards[target].dataset.group;
                    const prevMatching = (prevGroup && !prevGroup.startsWith('ng-')) ? cards.filter(c => c.dataset.group === prevGroup) : [];
                    if (prevMatching.length >= 2) {
                        let prevStart = target;
                        while (prevStart > 0 && cards[prevStart-1].dataset.group === prevGroup) prevStart--;
                        target = prevStart;
                    }
                    show(target);
                } else {
                    // Cau don: chi lui 1 cau
                    show(i - 1);
                }
            });
            card.querySelector('.qp-next').addEventListener('click', () => {
                const myGroup = card.dataset.group;
                const myMatching = (myGroup && !myGroup.startsWith('ng-')) ? cards.filter(c => c.dataset.group === myGroup) : [];
                if (myMatching.length >= 2) {
                    // Nhay qua tat ca cau trong cung group
                    let target = i + 1;
                    while (target < cards.length && cards[target].dataset.group === myGroup) target++;
                    show(target);
                } else {
                    // Cau don: chi tien 1 cau
                    show(i + 1);
                }
            });
            numbers[i].addEventListener('click', () => show(i));
            card.querySelector('.qp-flag').addEventListener('click', () => {
                if (flagged.has(card.dataset.key)) flagged.delete(card.dataset.key); else flagged.add(card.dataset.key);
                update();
                form?.dispatchEvent(new Event('change', {bubbles: true}));
            });
            card.addEventListener('change', () => {
                if(card.dataset.checked) {lock(card,card.dataset.checked);update();return;}
                delete card.dataset.checked;
                card.querySelector('.qp-feedback').hidden = true;
                card.querySelector('.qp-error').hidden = true;
                card.querySelectorAll('.qp-option').forEach(o => {
                    o.classList.remove('correct', 'wrong');
                    o.querySelector('.qp-verdict').textContent = '';
                });
                update();
            });
            // Cho phép bỏ chọn: click lại vào option đang được chọn thì deselect — chỉ khi chưa lock (chưa Kiểm tra)
            // Dùng pointerdown để ghi nhận trạng thái TRƯỚC khi browser thay đổi radio,
            // vì sự kiện click fired SAU khi browser đã tick radio => không phân biệt được chọn hay bỏ chọn.
            card.querySelectorAll('.qp-option').forEach(option => {
                let wasCheckedBeforeClick = false;
                option.addEventListener('pointerdown', () => {
                    const radio = option.querySelector('input[type=radio]');
                    wasCheckedBeforeClick = !!(radio && radio.checked);
                });
                option.addEventListener('click', () => {
                    if (card.dataset.checked) return; // đã lock, không cho bỏ
                    if (!wasCheckedBeforeClick) return; // đang chọn mới, không phải bỏ chọn
                    const radio = option.querySelector('input[type=radio]');
                    if (radio) {
                        radio.checked = false;
                        radio.dispatchEvent(new Event('change', {bubbles: true}));
                    }
                });
            });
            // Kiểm tra lấy lựa chọn hiện tại: preview dùng đáp án nhúng, người học POST checkUrl để server xác minh quyền/câu.
            // Phản hồi chỉ tô đáp án, hiện giải thích/phụ đề; không submit bài. Đáp án đổi khi đang fetch thì bỏ phản hồi cũ.
            // textContent dùng cho dữ liệu giải thích/phụ đề; bản dịch null hiển thị thông báo thiếu, không tự dịch hay suy đoán.
            card.querySelector('.qp-check').addEventListener('click', async event => {
                let selected = card.querySelector('input:checked')?.value;
                if (!selected) return;
                const button = event.currentTarget;
                button.disabled = true;
                card.querySelectorAll("input[type=radio]").forEach(input => input.disabled = true);
                const error = card.querySelector('.qp-error');
                error.hidden = true;
                try {
                    let data;
                    if (preview) {
                        if (!card.dataset.answer) throw new Error('Câu này chưa có đáp án đúng trong bản nháp.');
                        data = {answer: Number(card.dataset.answer), explanation: card.querySelector('.qp-source-explanation').textContent,
                            script: card.querySelector('.qp-source-script').textContent};
                    } else {
                        const response = await fetch(card.dataset.checkUrl, {method: 'POST', body: new URLSearchParams({selected})});
                        if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) throw new Error('Chưa kiểm tra được câu trả lời. Vui lòng thử lại.');
                        data = await response.json();
                    }
                    // A late response must never highlight a different answer than the one checked.
                    if (card.querySelector('input:checked')?.value !== selected) return;
                    selected = String(data.selected ?? selected);
                    const correct = Number(selected) === Number(data.answer);
                    lock(card, selected);
                    card.querySelectorAll('.qp-option').forEach(option => {
                        const value = Number(option.querySelector('input').value);
                        option.classList.toggle('correct', value === Number(data.answer));
                        option.classList.toggle('wrong', !correct && value === Number(selected));
                        option.querySelector('.qp-verdict').textContent = value === Number(data.answer) ? 'ĐÁP ÁN ĐÚNG' : value === Number(selected) ? 'BẠN CHỌN' : '';
                    });
                    const feedback = card.querySelector('.qp-feedback');
                    feedback.classList.toggle('incorrect', !correct);
                    feedback.querySelector('.qp-feedback-title').textContent = correct ? 'Chính xác! Bạn làm tốt lắm.' : 'Chưa đúng, không sao đâu.';
                    feedback.querySelector('.qp-explanation').textContent = data.explanation || 'Chưa có giải thích cho câu hỏi này.';
                    if (card.dataset.skill === 'LISTENING') {
                        feedback.querySelector('.qp-script').textContent = data.script || 'Chưa có phụ đề cho bài nghe này.';
                        feedback.querySelector('.qp-translation').textContent = data.scriptTranslation || 'Chưa có bản dịch phụ đề trong dữ liệu của đề.';
                    }
                    feedback.hidden = false;
                    update();
                } catch (ex) { error.textContent = ex.message; error.hidden = false; }
                finally { button.disabled = false; if(!card.dataset.checked) card.querySelectorAll("input[type=radio]").forEach(input=>input.disabled=false); }
            });
            if(checkedValue) card.querySelector(".qp-check").dispatchEvent(new MouseEvent("click",{bubbles:true}));
        });
        const modal = root.querySelector('.qp-submit-modal');
        // Chụp thống kê hiện thời vào modal, giữ nút submit gốc để xác nhận xong vẫn đi đúng URL/luồng hiện tại.
        function confirmSubmit(submitter) {
            pendingSubmitter = submitter || null;
            const totals = counts();
            root.querySelector('.qp-summary-answered').textContent = totals.answered;
            root.querySelector('.qp-summary-empty').textContent = totals.empty;
            root.querySelector('.qp-summary-flagged').textContent = totals.marked;
            bootstrap.Modal.getOrCreateInstance(modal).show();
        }
        root.querySelector('.qp-submit').addEventListener('click', () => {
            if (form?.dataset.writingSubmit === 'true') form.requestSubmit();
            else confirmSubmit(null);
        });
        // Listener capture chặn nộp để hiện tóm tắt trước listener autosave của exam/play.
        // Pause và hết giờ đi tiếp ngay; xác nhận Nộp gọi requestSubmit một lần với cờ confirming để không mở modal lặp.
        form?.addEventListener('submit', event => {
            const action = event.submitter?.getAttribute('formaction') || form.action;
            if (form.dataset.writingSubmit === 'true' || action.endsWith('/pause') || form.dataset.timedOut === 'true' || confirming) { confirming = false; return; }
            event.preventDefault();
            event.stopImmediatePropagation();
            confirmSubmit(event.submitter);
        }, true);
        root.querySelector('.qp-confirm-submit').addEventListener('click', () => {
            bootstrap.Modal.getOrCreateInstance(modal).hide();
            if (preview) {
                const totals = counts();
                const result = root.querySelector('.qp-preview-result');
                result.textContent = `Xem thử: đã trả lời ${totals.answered} câu, chưa làm ${totals.empty} câu, chưa chắc ${totals.marked} câu. Không lưu lịch sử.`;
                result.hidden = false;
                return;
            }
            confirming = true;
            if (pendingSubmitter) form.requestSubmit(pendingSubmitter); else form.requestSubmit();
        });
        form?.addEventListener('input', persist);
        document.addEventListener('keydown', event => {
            if (event.ctrlKey || event.altKey || event.metaKey || document.querySelector('.modal.show') || /INPUT|TEXTAREA|SELECT|AUDIO|BUTTON/.test(event.target.tagName)) return;
            if (event.key === 'ArrowLeft') { event.preventDefault(); show(current - 1); }
            if (event.key === 'ArrowRight') { event.preventDefault(); show(current + 1); }
            if (/^[1-4]$/.test(event.key)) {
                event.preventDefault();
                const input = cards[current].querySelectorAll('input[type=radio]')[Number(event.key) - 1];
                if (input && !input.disabled && !cards[current].dataset.checked) { input.checked = true; input.dispatchEvent(new Event('change', {bubbles: true})); }
            }
            if (event.key === 'Enter') {
                const check = cards[current].querySelector('.qp-check');
                if (!check.hidden) { event.preventDefault(); check.click(); }
            }
        });
        // IntersectionObserver: đánh dấu câu đang scroll đến trong Phiếu câu hỏi
        if (typeof IntersectionObserver !== "undefined") {
            const ioOptions = { root: null, threshold: 0.4 };
            const io = new IntersectionObserver((entries) => {
                // Tìm card có intersection ratio cao nhất
                let best = -1, bestRatio = 0;
                entries.forEach(entry => {
                    const idx = cards.indexOf(entry.target);
                    if (idx >= 0 && entry.isIntersecting && entry.intersectionRatio > bestRatio) {
                        best = idx; bestRatio = entry.intersectionRatio;
                    }
                });
                if (best >= 0 && best !== current) {
                    current = best;
                    update(); // cập nhật đánh dấu số câu bên Phiếu câu hỏi
                }
            }, ioOptions);
            cards.forEach(card => io.observe(card));
        }
        show(Number.isInteger(state.current) && state.current >= 0 && state.current < cards.length ? state.current : 0, false);
    });
})();
