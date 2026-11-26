/**
 * HOT TOPIK - Interactive Walkthrough Tour & Contextual Guidance
 * Zero External Dependencies (Vanilla JavaScript ES6+)
 * 11 Stations: Về TOPIK -> Hôm nay -> Lộ trình -> Luyện tập -> VIP -> Đề thi -> Bài viết GV -> Lý thuyết -> Tiến độ -> Xu -> Đánh giá
 */

(function () {
    'use strict';

    // 11 Trạm xuyên suốt hệ thống theo thứ tự người dùng yêu cầu (giải thích rõ ràng, chi tiết, dễ hiểu cho người mới)
    const TOUR_STATIONS = [
        {
            step: 1,
            path: '/ve-topik',
            name: 'Về TOPIK',
            badge: 'Trạm 1/11 · Về TOPIK',
            title: 'Hiểu rõ kỳ thi TOPIK từ A đến Z',
            desc: 'Nơi cung cấp thông tin chuẩn xác nhất về kỳ thi TOPIK: cấu trúc đề thi TOPIK I & II, bảng điểm chuẩn đỗ của từng cấp độ (từ Cấp 1 đến Cấp 6), cùng chiến thuật và mẹo làm bài thực chiến từ các thủ khoa.',
            tip: 'Người mới nên xem trước bảng quy đổi điểm và thời gian làm bài ở đây để biết chính xác mục tiêu của mình cần bao nhiêu điểm nhé!',
            selectors: ['#topikTabs', '#contentExam', '.container.my-4', 'main']
        },
        {
            step: 2,
            path: '/hom-nay',
            name: 'Hôm nay',
            badge: 'Trạm 2/11 · Hôm nay',
            title: 'Góc học tập hàng ngày cùng Bé Ssak',
            desc: 'Đây là trang chính bạn sẽ mở mỗi ngày. Hệ thống tự động chia nhỏ bài học thành 2–3 nhiệm vụ ngắn gọn (khoảng 15–20 phút/ngày) gồm Nghe, Đọc hoặc Từ vựng để bạn học nhẹ nhàng, không bị quá tải.',
            tip: 'Hoàn thành bài học mỗi ngày sẽ giúp duy trì chuỗi Streak học liên tục, nhận Xu thưởng và giúp mầm cây Bé Ssak lớn dần lên 🌱!',
            selectors: ['main.container .col-lg-8 > div > div:first-child', '.p-4.p-md-5.rounded-5', '.td-hero', 'main']
        },
        {
            step: 3,
            path: '/lo-trinh',
            name: 'Lộ trình',
            badge: 'Trạm 3/11 · Lộ trình',
            title: 'Bản đồ học tập cá nhân hóa theo từng ngày',
            desc: 'Lộ trình ôn thi được thiết kế chi tiết theo từng tuần và từng chặng. Bạn sẽ thấy rõ hôm nay học gì, ngày mai học gì và khi nào cần tổng ôn. Nếu có ngày bận chưa kịp học, bạn hoàn toàn có thể bấm "Làm bù" mà không sợ bị mất bài.',
            tip: 'Bạn có thể bấm "Khôi phục nhịp học" để hệ thống tự động dàn đều bài tập chưa làm sang những ngày tiếp theo.',
            selectors: ['#phaseBar', '.lr-phases', '.lr-head', 'main']
        },
        {
            step: 4,
            path: '/luyen-tap',
            name: 'Luyện tập',
            badge: 'Trạm 4/11 · Luyện tập',
            title: 'Luyện sâu từng dạng câu hỏi (Nghe · Đọc · Viết)',
            desc: 'Thay vì làm cả đề dài, bạn có thể chọn luyện riêng từng dạng câu hỏi (như Câu 1–2 ngữ pháp, Câu 3–4 tranh, Câu 53 viết biểu đồ...). Mỗi câu đều có đáp án, dịch nghĩa chi tiết và giải thích cặn kẽ để bạn hiểu bản chất.',
            tip: 'Nếu bạn yếu kỹ năng nào, hãy vào đây luyện tập dạng câu đó nhiều lần để khắc phục lỗ hổng kiến thức nhé.',
            selectors: ['#skillTabs', 'main.container > .nav', 'main']
        },
        {
            step: 5,
            path: '/subscription/plans',
            name: 'Gói VIP',
            badge: 'Trạm 5/11 · Gói VIP',
            title: 'Nâng cấp gói VIP · Mở khóa quyền lợi đặc quyền',
            desc: 'Trở thành học viên VIP để mở khóa toàn bộ kho đề thi Full Mock chuẩn thi thật, nhận 20 lượt AI chấm bài viết mỗi ngày và được Giáo viên chuyên môn chấm, sửa lỗi chi tiết từng câu trong bài viết luận.',
            tip: 'Có nhiều gói linh hoạt từ 1 tháng đến Trọn đời. Bạn cũng có thể dùng Xu tích lũy để đổi lượt chấm Viết mà không bắt buộc nạp tiền.',
            selectors: ['.bl-grid', '.bl-plan', '.bl-section', 'main > div:nth-of-type(2)', '.vip-plan-card', 'main']
        },
        {
            step: 6,
            path: '/de-thi',
            name: 'Đề thi',
            badge: 'Trạm 6/11 · Đề thi',
            title: 'Phòng thi thử trực tuyến đúng chuẩn kỳ thi thật',
            desc: 'Kho đề thi thử gồm các bộ đề Full Mock đầy đủ thời gian và số câu như phòng thi thực tế (có đồng hồ đếm ngược, tự động nộp bài khi hết giờ), cùng các bài Mini Test ngắn 15–20 phút giúp bạn rèn phản xạ và căn giờ làm bài.',
            tip: 'Sau khi nộp bài, hệ thống sẽ chấm điểm ngay lập tức, hiển thị biểu đồ phân tích câu đúng/sai và lời giải thích cho từng câu.',
            selectors: ['#examListContainer', '#tab-full', '.exam-top-tab', 'main']
        },
        {
            step: 7,
            path: '/bai-cham',
            name: 'Bài viết gửi GV',
            badge: 'Trạm 7/11 · Bài viết gửi GV',
            title: 'Quản lý các bài Viết gửi Giáo viên TOPIK chấm',
            desc: 'Nơi lưu lại toàn bộ các bài viết luận (Câu 51–54) mà bạn đã gửi cho Giáo viên. Giáo viên TOPIK sẽ chấm điểm, chỉ ra từng lỗi ngữ pháp, cách dùng từ và sửa bài chi tiết cho bạn trong vòng 48 giờ.',
            tip: 'Bạn có thể so sánh điểm AI ước tính với điểm Giáo viên chấm thật. Nếu thấy điểm chưa thỏa đáng, mỗi bài bạn đều có 1 lượt gửi phúc khảo.',
            selectors: ['main.container .p-4.p-md-5.rounded-5', 'main.container', 'main']
        },
        {
            step: 8,
            path: '/ly-thuyet',
            name: 'Lý thuyết',
            badge: 'Trạm 8/11 · Lý thuyết',
            title: 'Sổ tay tra cứu Ngữ pháp, Từ vựng & Hán - Hàn',
            desc: 'Kho kiến thức bỏ túi tổng hợp đầy đủ ngữ pháp TOPIK theo nhóm ý nghĩa, từ vựng theo chủ đề, cùng cẩm nang từ Hán - Hàn (Hanja) giúp người Việt đoán nghĩa từ vựng tiếng Hàn siêu nhanh và nhớ cực lâu.',
            tip: 'Có tính năng Flashcard lật thẻ và "Sổ từ chưa thuộc" để bạn ôn tập ngắt quãng mọi lúc mọi nơi trên điện thoại.',
            selectors: ['#cardsGridContainer', '#tab-vocab', '#levelSelectorRow', 'main']
        },
        {
            step: 9,
            path: '/tien-do',
            name: 'Tiến độ',
            badge: 'Trạm 9/11 · Tiến độ',
            title: 'Báo cáo kết quả & Biểu đồ tiến bộ học tập',
            desc: 'Xem toàn bộ số liệu học tập của bạn: tổng số ngày đã học, số giờ ôn luyện, tỷ lệ làm đúng theo từng kỹ năng (Nghe, Đọc, Viết) và lịch sử làm bài. Nhờ đó bạn biết chính xác mình đang mạnh ở đâu và yếu phần nào.',
            tip: 'Hãy xem biểu đồ này hàng tuần để kịp thời điều chỉnh kế hoạch học, tập trung vào kỹ năng còn đang bị yếu điểm.',
            selectors: ['#panel-LISTENING', '.skill-panel', '#mockScoreChart', 'main']
        },
        {
            step: 10,
            path: '/xu',
            name: 'Kho Xu',
            badge: 'Trạm 10/11 · Kho Xu',
            title: 'Ví Xu thưởng học tập · Học chăm là có quà',
            desc: 'Mỗi khi hoàn thành bài học ở mục "Hôm nay" hoặc "Lộ trình", bạn sẽ được cộng Xu vào ví. Xu tích lũy được dùng trực tiếp để đổi lượt AI chấm Viết, đổi bài gửi Giáo viên chấm hoặc mở khóa đề thi VIP hoàn toàn miễn phí.',
            tip: 'Chăm chỉ hoàn thành nhiệm vụ mỗi ngày là cách tích Xu nhanh nhất mà không cần phải nạp tiền!',
            selectors: ['main.container > .p-4.p-md-5.rounded-5', 'main.container', 'main']
        },
        {
            step: 11,
            path: '/ve-topik#reviewsSection',
            name: 'Đánh giá hệ thống',
            badge: 'Trạm 11/11 · Đánh giá hệ thống',
            title: 'Đóng góp ý kiến & Đánh giá trải nghiệm hệ thống',
            desc: 'Cộng đồng học viên cùng chia sẻ cảm nghĩ, chấm điểm sao và gửi nhận xét về hệ thống HOT TOPIK. Đội ngũ phát triển luôn đọc từng góp ý của bạn để liên tục nâng cấp tính năng và cải thiện chất lượng đề thi.',
            tip: 'Bạn có thể bấm nút "Gửi đánh giá" ở đây bất cứ lúc nào để đóng góp ý kiến giúp Bé Ssak hoàn thiện hơn nhé!',
            selectors: ['#reviewsSection', '#reviewContainer', 'main']
        }
    ];

    const STORAGE_KEY_COMPLETED = 'hottopik_walkthrough_completed';
    const STORAGE_KEY_ACTIVE = 'hottopik_walkthrough_active';
    const STORAGE_KEY_STEP = 'hottopik_walkthrough_step';

    let currentStep = 1;
    let backdropEl = null;
    let spotlightEl = null;
    let popoverEl = null;
    let activeTargetEl = null;

    // Tìm phần tử mục tiêu theo danh sách selector
    function findTargetElement(selectors) {
        if (!selectors || selectors.length === 0) return document.body;
        for (let i = 0; i < selectors.length; i++) {
            const el = document.querySelector(selectors[i]);
            if (el && el.offsetParent !== null) {
                return el;
            }
        }
        return document.querySelector('main') || document.body;
    }

    // Tạo các phần tử UI trong DOM
    function ensureTourDOM() {
        if (!backdropEl) {
            backdropEl = document.createElement('div');
            backdropEl.className = 'ht-tour-backdrop';
            backdropEl.addEventListener('click', skipTour);
            document.body.appendChild(backdropEl);
        }

        if (!spotlightEl) {
            spotlightEl = document.createElement('div');
            spotlightEl.className = 'ht-tour-spotlight';
            document.body.appendChild(spotlightEl);
        }

        if (!popoverEl) {
            popoverEl = document.createElement('div');
            popoverEl.className = 'ht-tour-popover';
            document.body.appendChild(popoverEl);
        }
    }

    // Cập nhật vị trí Spotlight và Popover
    function updatePosition() {
        if (!spotlightEl || !popoverEl || !activeTargetEl) return;

        const rect = activeTargetEl.getBoundingClientRect();
        const scrollX = window.pageXOffset || document.documentElement.scrollLeft;
        const scrollY = window.pageYOffset || document.documentElement.scrollTop;

        // Vị trí spotlight với khoảng đệm thoải mái 14px
        const padding = 14;
        const spotTop = rect.top + scrollY - padding;
        const spotLeft = rect.left + scrollX - padding;
        const spotWidth = Math.max(rect.width + (padding * 2), 80);
        const spotHeight = Math.max(rect.height + (padding * 2), 50);

        spotlightEl.style.top = `${spotTop}px`;
        spotlightEl.style.left = `${spotLeft}px`;
        spotlightEl.style.width = `${spotWidth}px`;
        spotlightEl.style.height = `${spotHeight}px`;

        // Kích thước popover
        const popoverWidth = popoverEl.offsetWidth || 420;
        const popoverHeight = popoverEl.offsetHeight || 240;

        let popTop;
        const spaceBelow = window.innerHeight - rect.bottom;
        const spaceAbove = rect.top;

        // Nếu phần tử mục tiêu quá cao (> 65% chiều cao màn hình), ghim thẻ popover ở góc dưới màn hình để không che khuất
        if (rect.height > window.innerHeight * 0.65) {
            popTop = scrollY + window.innerHeight - popoverHeight - 24;
        } else if (spaceBelow >= popoverHeight + 20 || spaceBelow >= spaceAbove) {
            popTop = rect.bottom + scrollY + 16;
        } else {
            popTop = rect.top + scrollY - popoverHeight - 16;
        }

        // Căn giữa popover theo chiều ngang với đối tượng
        let popLeft = rect.left + scrollX + ((rect.width - popoverWidth) / 2);

        // Giới hạn trong màn hình (margin 20px)
        const minLeft = scrollX + 20;
        const maxLeft = scrollX + window.innerWidth - popoverWidth - 20;
        popLeft = Math.max(minLeft, Math.min(popLeft, maxLeft));

        popoverEl.style.top = `${Math.max(scrollY + 10, popTop)}px`;
        popoverEl.style.left = `${popLeft}px`;
    }

    // Render nội dung trạm
    function renderStation(station, isSinglePageMode) {
        ensureTourDOM();

        const targetEl = findTargetElement(station.selectors);
        activeTargetEl = targetEl;

        // Cuộn mượt đến phần tử mục tiêu
        if (targetEl && targetEl !== document.body) {
            targetEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }

        const isLastStep = station.step === 11 || isSinglePageMode;
        const isFirstStep = station.step === 1;

        const nextBtnText = isLastStep ? 'Hoàn thành Tour 🎉' : `Tiếp tục (${TOUR_STATIONS[station.step].name}) →`;
        const prevBtnHtml = (!isFirstStep && !isSinglePageMode)
            ? `<button type="button" class="ht-tour-btn ht-tour-btn-prev" onclick="window.htTourPrevStep()">← Quay lại</button>`
            : '';

        const progressPercent = Math.round((station.step / 11) * 100);

        popoverEl.innerHTML = `
            <div class="ht-tour-progress-bar">
                <div class="ht-tour-progress-fill" style="width: ${progressPercent}%;"></div>
            </div>
            <div class="ht-tour-popover-header">
                <span class="ht-tour-badge-step">
                    <span>🌱</span> ${station.badge}
                </span>
                <button type="button" class="ht-tour-close-btn" onclick="window.htTourSkip()" title="Đóng hướng dẫn (Esc)">
                    ✕
                </button>
            </div>
            <h3 class="ht-tour-popover-title">${station.title}</h3>
            <p class="ht-tour-popover-desc">${station.desc}</p>
            ${station.tip ? `
                <div style="background: #F0FDF4; border: 1px solid #DCFCE7; border-left: 3.5px solid #2E7D67; padding: 10px 14px; border-radius: 10px; font-size: 13px; color: #166534; line-height: 1.5; margin-bottom: 14px;">
                    <span style="font-weight: 800; color: #2E7D67;">💡 Mẹo Bé Ssak:</span> ${station.tip}
                </div>
            ` : ''}
            <div class="ht-tour-popover-footer">
                <button type="button" class="ht-tour-skip-btn" onclick="window.htTourSkip()">
                    ${isSinglePageMode ? 'Đóng' : 'Bỏ qua tour'}
                </button>
                <div class="ht-tour-actions-right">
                    ${prevBtnHtml}
                    <button type="button" class="ht-tour-btn ht-tour-btn-next" onclick="${isLastStep ? 'window.htTourFinish()' : 'window.htTourNextStep()'}">
                        ${nextBtnText}
                    </button>
                </div>
            </div>
        `;

        // Đợi DOM cập nhật kích thước rồi tính toạ độ
        setTimeout(() => {
            updatePosition();
        }, 80);
    }

    // Chuyển sang bước tiếp theo
    function nextStation() {
        if (currentStep < 11) {
            goToStation(currentStep + 1);
        } else {
            finishTour();
        }
    }

    // Quay lại bước trước
    function prevStation() {
        if (currentStep > 1) {
            goToStation(currentStep - 1);
        }
    }

    // Điều hướng đến Trạm chỉ định
    function goToStation(stepNumber) {
        currentStep = stepNumber;
        const station = TOUR_STATIONS[stepNumber - 1];
        if (!station) return;

        localStorage.setItem(STORAGE_KEY_STEP, stepNumber.toString());
        localStorage.setItem(STORAGE_KEY_ACTIVE, 'true');

        // Phân tích đường dẫn đích
        const targetUrl = new URL(station.path, window.location.origin);
        const currentPath = window.location.pathname;

        // Nếu đang ở đúng trang đích (chỉ khác hash hoặc query)
        if (currentPath === targetUrl.pathname) {
            if (targetUrl.hash) {
                window.location.hash = targetUrl.hash;
            }
            renderStation(station, false);
        } else {
            // Chuyển trang kèm tham số onboarding_step
            targetUrl.searchParams.set('onboarding_step', stepNumber.toString());
            window.location.href = targetUrl.toString();
        }
    }

    // Đóng và dọn dẹp Tour
    function removeTourDOM() {
        if (backdropEl) {
            backdropEl.remove();
            backdropEl = null;
        }
        if (spotlightEl) {
            spotlightEl.remove();
            spotlightEl = null;
        }
        if (popoverEl) {
            popoverEl.remove();
            popoverEl = null;
        }
        activeTargetEl = null;
    }

    // Bỏ qua tour
    function skipTour() {
        localStorage.setItem(STORAGE_KEY_COMPLETED, 'true');
        localStorage.removeItem(STORAGE_KEY_ACTIVE);
        removeTourDOM();
        cleanUrlParam();
    }

    // Hoàn thành tour
    function finishTour() {
        localStorage.setItem(STORAGE_KEY_COMPLETED, 'true');
        localStorage.removeItem(STORAGE_KEY_ACTIVE);
        removeTourDOM();
        cleanUrlParam();

        // Nếu đang ở trang Về TOPIK và trạm 11, cuộn nhẹ vào phần đánh giá
        const reviewsSection = document.getElementById('reviewsSection');
        if (reviewsSection) {
            reviewsSection.scrollIntoView({ behavior: 'smooth' });
        }
    }

    // Xóa tham số onboarding_step khỏi URL mà không tải lại trang
    function cleanUrlParam() {
        const url = new URL(window.location.href);
        if (url.searchParams.has('onboarding_step')) {
            url.searchParams.delete('onboarding_step');
            window.history.replaceState({}, document.title, url.toString());
        }
    }

    // Chuyển modal trợ giúp ra thẳng document.body để thoát khỏi giới hạn z-index của navbar
    function ensureHelpModalDirectChildOfBody() {
        const modalEl = document.getElementById('htHelpGuideModal');
        if (modalEl && modalEl.parentElement !== document.body) {
            document.body.appendChild(modalEl);
        }
    }

    // Khởi tạo và kiểm tra trạng thái
    function initWalkthrough() {
        const path = window.location.pathname;

        // Đảm bảo modal không bị che bởi navbar
        ensureHelpModalDirectChildOfBody();

        // Bỏ qua các trang quản trị và xác thực
        if (path.startsWith('/admin') ||
            path.startsWith('/content') ||
            path.startsWith('/teacher') ||
            path.startsWith('/operation') ||
            path.startsWith('/login') ||
            path.startsWith('/auth') ||
            path.startsWith('/logout')) {
            return;
        }

        // Kiểm tra URL query param: onboarding_step
        const params = new URLSearchParams(window.location.search);
        const stepParam = parseInt(params.get('onboarding_step'), 10);

        if (stepParam >= 1 && stepParam <= 11) {
            currentStep = stepParam;
            localStorage.setItem(STORAGE_KEY_STEP, currentStep.toString());
            localStorage.setItem(STORAGE_KEY_ACTIVE, 'true');
            const station = TOUR_STATIONS[currentStep - 1];
            renderStation(station, false);
            return;
        }

        // Kiểm tra xem tour có đang dang dở không
        const isActive = localStorage.getItem(STORAGE_KEY_ACTIVE) === 'true';
        if (isActive) {
            const savedStep = parseInt(localStorage.getItem(STORAGE_KEY_STEP), 10) || 1;
            const station = TOUR_STATIONS[savedStep - 1];
            if (station && (path === station.path || path === station.path.split('#')[0] || ((station.path === '/subscription/plans' || station.path === '/vip') && (path === '/vip' || path === '/subscription/plans')))) {
                currentStep = savedStep;
                renderStation(station, false);
                return;
            }
        }

        // Kiểm tra lần đầu truy cập (Chỉ tự động bật 1 lần duy nhất cho người mới)
        const isCompleted = localStorage.getItem(STORAGE_KEY_COMPLETED) === 'true';
        if (!isCompleted) {
            if (path === '/ve-topik') {
                currentStep = 1;
                localStorage.setItem(STORAGE_KEY_STEP, '1');
                localStorage.setItem(STORAGE_KEY_ACTIVE, 'true');
                renderStation(TOUR_STATIONS[0], false);
            } else if (path === '/hom-nay' || path === '/') {
                localStorage.setItem(STORAGE_KEY_STEP, '1');
                localStorage.setItem(STORAGE_KEY_ACTIVE, 'true');
                window.location.href = '/ve-topik?onboarding_step=1';
            }
        }

        // Huy hiệu gợi ý nhẹ lần đầu (First-time Pulse Hint) cho tính năng phụ
        initSecondaryFeatureHints();
    }

    // Gợi ý nhẹ lần đầu cho nút Báo lỗi & Khiếu nại
    function initSecondaryFeatureHints() {
        if (!localStorage.getItem('ht_hint_seen_report')) {
            const reportBtns = document.querySelectorAll('.js-report-btn, [data-report-btn]');
            if (reportBtns.length > 0) {
                reportBtns[0].classList.add('ht-pulse-hint');
                const clearHint = () => {
                    localStorage.setItem('ht_hint_seen_report', 'true');
                    reportBtns[0].classList.remove('ht-pulse-hint');
                };
                reportBtns[0].addEventListener('click', clearHint, { once: true });
            }
        }

        if (!localStorage.getItem('ht_hint_seen_appeal')) {
            const appealBtns = document.querySelectorAll('.js-appeal-btn, [data-appeal-btn]');
            if (appealBtns.length > 0) {
                appealBtns[0].classList.add('ht-pulse-hint');
                const clearHint = () => {
                    localStorage.setItem('ht_hint_seen_appeal', 'true');
                    appealBtns[0].classList.remove('ht-pulse-hint');
                };
                appealBtns[0].addEventListener('click', clearHint, { once: true });
            }
        }
    }

    // Các hàm toàn cục cho UI & Menu Trợ giúp
    window.startFullTour = function () {
        localStorage.setItem(STORAGE_KEY_STEP, '1');
        localStorage.setItem(STORAGE_KEY_ACTIVE, 'true');
        if (window.location.pathname === '/ve-topik') {
            currentStep = 1;
            renderStation(TOUR_STATIONS[0], false);
        } else {
            window.location.href = '/ve-topik?onboarding_step=1';
        }
    };

    window.spotlightCurrentPage = function () {
        const path = window.location.pathname;
        let matched = TOUR_STATIONS.find(s => s.path === path || path === s.path.split('#')[0] || ((s.path === '/subscription/plans' || s.path === '/vip') && (path === '/vip' || path === '/subscription/plans')));
        if (!matched) {
            matched = {
                step: 0,
                path: path,
                name: document.title.split('·')[0].trim() || 'Trang hiện tại',
                badge: '💡 Hướng dẫn nhanh trang này',
                title: document.title.split('·')[0].trim() || 'Hướng dẫn chức năng',
                desc: 'Khám phá các chức năng chính trên trang này để hỗ trợ việc học thi TOPIK hiệu quả.',
                tip: 'Bấm biểu tượng dấu hỏi (?) trên thanh điều hướng bất cứ lúc nào khi cần trợ giúp.',
                selectors: ['main.container', 'main', '.container', 'body']
            };
        }
        renderStation(matched, true);
    };

    window.openHelpGuideModal = function () {
        ensureHelpModalDirectChildOfBody();
        const modalEl = document.getElementById('htHelpGuideModal');
        if (!modalEl) return;

        if (window.bootstrap && window.bootstrap.Modal) {
            const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
            modal.show();
        } else {
            modalEl.classList.add('show');
            modalEl.style.display = 'block';
        }
    };

    window.closeHelpGuideModal = function () {
        const modalEl = document.getElementById('htHelpGuideModal');
        if (!modalEl) return;

        if (window.bootstrap && window.bootstrap.Modal) {
            const modal = bootstrap.Modal.getInstance(modalEl);
            if (modal) {
                modal.hide();
            }
        }
        modalEl.classList.remove('show');
        modalEl.style.display = 'none';
        modalEl.setAttribute('aria-hidden', 'true');
        modalEl.removeAttribute('aria-modal');

        // Dọn dẹp backdrop của Bootstrap nếu còn sót lại
        document.querySelectorAll('.modal-backdrop').forEach(b => b.remove());
        document.body.classList.remove('modal-open');
        document.body.style.removeProperty('padding-right');
        document.body.style.removeProperty('overflow');
    };

    window.openSystemFeedbackModal = function () {
        if (window.location.pathname === '/ve-topik') {
            const section = document.getElementById('reviewsSection');
            if (section) section.scrollIntoView({ behavior: 'smooth' });
            if (typeof window.openFeedbackModal === 'function') {
                window.openFeedbackModal();
            }
        } else {
            window.location.href = '/ve-topik#reviewsSection';
        }
    };

    window.toggleHelpDropdown = function (event) {
        if (event) event.stopPropagation();
        const dropdown = document.getElementById('htHelpDropdown');
        if (!dropdown) return;

        // Đóng dropdown thông báo và tài khoản nếu đang mở
        const bellDrop = document.getElementById('bellDropdown');
        if (bellDrop) bellDrop.style.display = 'none';
        const userDrop = document.getElementById('userDropdownMenu');
        if (userDrop) userDrop.style.display = 'none';

        const isVisible = dropdown.style.display === 'block' || dropdown.classList.contains('show');
        if (isVisible) {
            dropdown.style.display = 'none';
            dropdown.classList.remove('show');
        } else {
            dropdown.style.display = 'block';
            dropdown.classList.add('show');
        }
    };

    // Đóng dropdown trợ giúp khi bấm ra ngoài
    document.addEventListener('click', function (e) {
        const dropdown = document.getElementById('htHelpDropdown');
        const btn = document.getElementById('htHelpToggleBtn');
        if (dropdown && dropdown.classList.contains('show')) {
            if (!dropdown.contains(e.target) && (!btn || !btn.contains(e.target))) {
                dropdown.style.display = 'none';
                dropdown.classList.remove('show');
            }
        }
    });

    // Lắng nghe phím bấm: Esc để thoát tour hoặc modal
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') {
            if (popoverEl) {
                skipTour();
            }
            const modalEl = document.getElementById('htHelpGuideModal');
            if (modalEl && (modalEl.classList.contains('show') || modalEl.style.display === 'block')) {
                window.closeHelpGuideModal();
            }
            const dropdown = document.getElementById('htHelpDropdown');
            if (dropdown) {
                dropdown.style.display = 'none';
                dropdown.classList.remove('show');
            }
        }
    });

    // Lắng nghe thay đổi kích thước & cuộn chuột để cố định vị trí spotlight
    window.addEventListener('resize', function () {
        if (activeTargetEl) updatePosition();
    });
    window.addEventListener('scroll', function () {
        if (activeTargetEl) updatePosition();
    }, { passive: true });

    // Gán các hàm điều hướng vào window
    window.htTourNextStep = nextStation;
    window.htTourPrevStep = prevStation;
    window.htTourSkip = skipTour;
    window.htTourFinish = finishTour;

    // Khởi chạy khi DOM sẵn sàng
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initWalkthrough);
    } else {
        initWalkthrough();
    }
})();
