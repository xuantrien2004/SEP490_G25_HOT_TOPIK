# 04 - NGHIỆP VỤ CỐT LÕI (CORE BUSINESS LOGIC)

> **Trạng thái**: Đã xác minh 100% từ mã nguồn thực tế và các quyết định kỹ thuật đã thống nhất.  
> **CẢNH BÁO CHO AI AGENT**: Đây là các quy tắc bất biến của hệ thống HOT TOPIK. Tuyệt đối không tự ý thay đổi các công thức, ngưỡng điểm hay quy định nghiệp vụ được nêu tại đây nếu không có yêu cầu rõ ràng từ người dùng.

---

## 1. Nghiệp Vụ Đánh Giá Năng Lực Đầu Vào (Placement Test)

- **Mã nguồn thẩm định**: `PlacementRules.java`, `PlacementService.java`, `PlacementController.java`.
- **Cấu trúc đề test**: Đúng **20 câu trắc nghiệm** (10 câu Nghe + 10 câu Đọc).
  - Mỗi câu: **10 điểm**.
  - Tổng điểm tối đa: **200 điểm**.
  - Câu hỏi bắt buộc là các câu đơn lẻ (Single Question), không dùng câu nhóm.
- **Quy tắc phân loại và tính điểm (`PlacementRules.suggestedLevel`)**:
  - **Dưới 80 điểm** (Đúng dưới 8 câu / 20 câu): Xếp **Cấp 0** (*"Chưa đạt cấp 1"*).
  - **Từ 80 đến 139 điểm** (Đúng từ 8 đến 13 câu): Xếp **Cấp 1** (*"Sơ cấp 1"*).
  - **Từ 140 điểm trở lên** (Đúng từ 14 câu trở lên): Xếp **Cấp 2** (*"Sơ cấp 2"*).
- **Quy tắc tư vấn Lộ trình (`PlacementRules.suggestedTrack`)**:
  - Đạt Cấp 0 hoặc Cấp 1 $\rightarrow$ Hệ thống gợi ý học lộ trình **TOPIK I** (Mục tiêu: Cấp 1 hoặc Cấp 2).
  - Đạt Cấp 2 $\rightarrow$ Hệ thống gợi ý học lộ trình **TOPIK II** (Mục tiêu: Cấp 3, 4, 5 hoặc 6).
- **Quy tắc thời gian lộ trình**:
  - Người học được quyền chọn thời gian ôn luyện từ **31 ngày đến 200 ngày** (Thời gian mặc định là **90 ngày** - 3 tháng).

---

## 2. Nghiệp Vụ Lộ Trình Học Cá Nhân Hóa (Personalized Roadmap)

- **Mã nguồn thẩm định**: `LearnerRoadmapService.java`, `ProgramRoadmapService.java`, `ProgramRules.java`.
- **Quy tắc sinh lịch học**:
  - Dựa trên trình độ hiện tại (từ Placement Test), mục tiêu (TOPIK 1 - 6) và thời hạn (số ngày).
  - Mỗi ngày được phân bổ nhiệm vụ học tập cụ thể:
    - Ngày học từ vựng theo chủ đề (20-30 từ).
    - Ngày học cấu trúc ngữ pháp (2-3 mẫu câu kèm ví dụ).
    - Ngày luyện tập dạng câu hỏi chuẩn đề thi.
    - Ngày ôn tập tuần (Weekly Review).
    - Ngày thi thử Mini Test hoặc Full Mock.
- **Cơ chế phục hồi nhịp học (Catch-up / Rhythm Recovery)**:
  - Nếu người học nghỉ học liên tiếp **từ 3 ngày trở lên**, hệ thống không cộng dồn bài tập dồn ứ gây nản chí, mà tự động kích hoạt tính năng **bắt nhịp lại**:
    - Giữ nguyên mục tiêu điểm số.
    - Phân bổ lại các bài học trọng tâm vào những ngày còn lại.
    - Tạo các bài tập tổng hợp ngắn gọn để bù đắp kiến thức bị gián đoạn.

---

## 3. Nghiệp Vụ Đề Thi Thử (Full Mock & Mini Test)

- **Mã nguồn thẩm định**: `LearnerExamService.java`, `ExamComposerService.java`, `ExamScoreCalculator.java`.

### 3.1. Cấu trúc Đề thi TOPIK Chuẩn & Phân Tách Kỹ Năng (Full Mock Skill Separation)
- **Cơ chế tách kỹ năng trong đề Full Mock**:
  Thay vì dồn toàn bộ câu hỏi và thời gian làm bài cùng một lượt, đề Full Mock chia thành các ca thi kỹ năng độc lập, mô phỏng đúng trình tự thi thực tế:
  - **TOPIK I (Sơ cấp - Thang điểm 200)**:
    - Ca 1: **Nghe (30 câu, 100 điểm, 40 phút)** $\rightarrow$ Ca 2: **Đọc (40 câu, 100 điểm, 60 phút)** $\rightarrow$ Kết quả.
  - **TOPIK II (Trung & Cao cấp - Thang điểm 300)**:
    - Ca 1: **Nghe (50 câu, 100 điểm, 60 phút)** $\rightarrow$ Ca 2: **Đọc (50 câu, 100 điểm, 70 phút)** $\rightarrow$ Ca 3: **Viết (4 câu 51-54, 100 điểm, 50 phút)** $\rightarrow$ Kết quả.
- **Bộ đếm thời gian độc lập (`listening_seconds`, `reading_seconds`, `writing_seconds`)**:
  - Khi bắt đầu đề Full Mock, hệ thống khởi tạo thời gian riêng cho từng kỹ năng vào `exam_attempts`.
  - Trong quá trình làm, đồng hồ đếm ngược phản ánh đúng số giây của kỹ năng đang thi (`current_skill`).
- **Luồng chuyển ca thi tự động & chủ động**:
  - **Đề Full Mock chỉ có một kỹ năng (`LearnerExamService.availableSkills`)**: Hệ thống xác định danh sách kỹ năng thực sự có câu hỏi trong đề (`LISTENING`, `READING`, `WRITING` cho `TOPIK_II`). Nếu đề `FULL` chỉ chứa đúng 1 kỹ năng (ví dụ chỉ có đề Viết TOPIK II), `start()` khởi tạo `current_skill` vào thẳng kỹ năng đó và `nextSkill()` trả về `null` để nộp xong là hoàn thành bài thi ngay, không đi qua các ca kỹ năng trống.
  - Khi hết giờ của kỹ năng hiện tại (`remainingSeconds <= 0`) hoặc học viên chủ động bấm nộp kỹ năng:
    1. Hệ thống lưu toàn bộ câu trả lời của kỹ năng đó vào `answers_json`.
    2. Cập nhật `current_skill` sang kỹ năng kế tiếp (`nextSkill`).
    3. Gán thời gian còn lại của kỹ năng tiếp theo vào `remaining_seconds` và chuyển hướng học viên tiếp tục làm bài (`/lam-bai/{attemptId}`).
    4. Khi kỹ năng cuối cùng hoàn thành, hệ thống gọi `finish(attempt)`, chốt điểm số và chuyển hướng đến trang kết quả (`/ket-qua/{attemptId}`).

### 3.2. Chế độ Mini Test
- Cho phép người học thi riêng từng kỹ năng ngắn: **Mini Nghe** (15-20 câu), **Mini Đọc** (15-20 câu), **Mini Viết** (câu 51-52 hoặc 53).

### 3.3. Quy tắc Hiển thị & Điều hướng Câu hỏi Nhóm vs Câu Đơn (Grouped vs Single Questions)
- **Câu hỏi nhóm dùng chung (Grouped Questions - từ 2 câu trở lên)**:
  - Khi 2 hoặc 3 câu hỏi cùng sử dụng chung 1 đoạn văn, 1 file audio hoặc 1 hình ảnh (`matchingCards.length >= 2`):
    - Hệ thống hiển thị **đồng thời cả nhóm câu hỏi trên màn hình** để người học làm cùng 1 lượt.
    - **Hiển thị trực tiếp ngữ liệu trên từng câu con**: Đoạn văn chung, file audio nghe hoặc hình ảnh minh họa được hiển thị trực tiếp lặp lại trên từng thẻ câu hỏi con trong nhóm. Khi học viên cuộn xuống làm câu 2 hoặc câu 3, ngữ liệu nằm ngay tại thẻ câu hỏi đó giúp quan sát liền mạch và tiện thao tác (thay thế cho cơ chế neo đỉnh/sticky scroll cũ gây khó nhìn khi cuộn).
    - **Nút chuyển câu**: Chỉ hiển thị nút "Câu tiếp" và "Câu trước" ở **cuối nhóm** (ở câu cuối cùng trong nhóm). Nút "Câu trước" của nhóm phải nhảy về câu đầu của nhóm trước đó (không nhảy lắt nhắt trong nhóm).
- **Câu hỏi đơn (Single Questions - 1 câu/bài)**:
  - Đối với các câu hỏi đơn lẻ bình thường (`matchingCards.length < 2`):
    - Ngữ liệu của câu hỏi (file nghe, đoạn văn đọc, hoặc ảnh đề bài) hiển thị trực tiếp bên trong thẻ của chính câu hỏi đó.
    - Nút chuyển câu "Câu trước" và "Câu tiếp" chuyển câu tuần tự từng câu một (+/- 1 câu).
- **Bảo toàn số thứ tự câu hỏi thực tế (`q.no`)**:
  - Số thứ tự câu hỏi hiển thị trên thẻ câu hỏi (`.qp-pill`) và các nút ô số trên thanh sidebar phiếu câu hỏi (`.qp-number`) luôn sử dụng `q.no` (số thứ tự thực tế trong đề thi), **tuyệt đối không dùng chỉ số lặp cục bộ (`stat.count`)**.
  - Ví dụ: Trong đề Full Mock TOPIK I, khi chuyển sang phần Đọc, câu đầu tiên hiển thị đúng là **Câu 31**; trong TOPIK II, phần Viết hiển thị từ **Câu 51**.
- **Quy tắc chung cho cả hai loại câu**:
  - **Nút "Chưa chắc" & "Báo lỗi"**: Luôn luôn giữ nguyên ở **từng câu hỏi riêng biệt**, không bị ẩn đi.
  - **Highlight Phiếu câu hỏi**: Khi người học cuộn màn hình đến câu nào, thanh "Phiếu câu hỏi" bên cạnh phải cập nhật và đánh dấu chính xác câu đó bằng công nghệ `IntersectionObserver`.

### 3.4. Quy tắc Lưu & Tiếp Tục Làm Bài Thi (Exam Pause & Resume)
- Khi học viên bấm "Lưu và thoát" hoặc rời khỏi bài thi, hệ thống ghi nhận chính xác thời gian thực tế còn lại của kỹ năng hiện tại (`remainingSeconds`) và các đáp án đã chọn vào `exam_attempts`.
- Khi học viên quay lại bấm làm tiếp bài thi đang dở, hệ thống nạp lại đúng số giây còn lại từ bản lưu gần nhất (ví dụ thoát lúc còn 65:10 thì làm tiếp sẽ đếm ngược chính xác từ 65:10, không bị trả về mốc thời gian cũ).
- **Xử lý bỏ chọn đáp án trắc nghiệm & Gửi dữ liệu an toàn**:
  - Trên client (`question-player.js`): Không dùng thẻ input hidden rỗng tĩnh trong form để tránh xung đột với cơ chế bind tham số của Spring MVC. Chỉ tự động sinh dynamic input `.qp-empty-input` khi một câu hỏi không có bất kỳ radio nào được chọn (giúp hỗ trợ bỏ chọn đáp án khi click lại).
  - Trên server (`LearnerExamController.java`, `PracticeController.java`): Hàm `extractAnswers` duyệt mảng giá trị của request param, ưu tiên lấy giá trị không rỗng nếu tồn tại nhiều value cùng key, loại bỏ triệt để nguy cơ giá trị rỗng đè mất đáp án đã chọn.
- **Dọn dẹp lượt làm bài rỗng khi thoát**: Nếu học viên bấm "Lưu và thoát" nhưng chưa trả lời bất kỳ câu nào và chưa đánh dấu cờ nào (`!hasAnswer && !hasFlag`), hệ thống tự động xóa bản ghi `ExamAttempt` để tránh rác CSDL và trả về thông báo phù hợp.

### 3.5. Nghiệp Vụ Luyện Tập Theo Dạng & Lưu Nháp (Practice Logic)
- **Đánh số thứ tự câu hỏi luyện tập từ 1**: Trong `PracticeService.questions()` và `PracticeService.result()`, các câu hỏi của lượt luyện tập luôn được gán số thứ tự tuần tự `no = 1..N` (không lấy số thứ tự gốc của đề thi), đồng thời lưu giữ `originalNo` trong `QuestionView` để hiển thị nhãn `"Câu gốc #..."` ở trang kết quả và gửi báo lỗi câu hỏi đúng mã gốc.
- **Gom nhóm câu hỏi dùng chung ngữ liệu**: Đối với các dạng câu hỏi có từ 2-3 câu dùng chung đoạn văn hoặc audio (theo `QuestionTypeCatalog`), hệ thống gom các câu trong cùng một khối (block) lại để học viên làm đồng thời trong 1 lượt làm bài, đồng bộ đầy đủ đoạn văn và audio vào từng câu hỏi con qua `QuestionPlayerItem.practice`.
- **Cơ chế bốc câu hỏi luyện tập**:
  - Ưu tiên lựa chọn các nhóm câu hỏi chưa từng làm (`unseenGroups`).
  - Khi kho câu hỏi chưa làm đã hết, hệ thống bốc từ các nhóm đã làm (`seenGroups`) nhưng xáo trộn ngẫu nhiên thứ tự các nhóm để không lặp lại nguyên vẹn cấu trúc đề của các lần trước.
  - Tự động loại trừ các câu hỏi đang nằm trong bài luyện dở (`pendingAttempt`) có hoạt động của người dùng để tránh trùng lặp.
- **Cơ chế Lưu nháp bài luyện tập (Save & Pause / Resume)**:
  - Khi học viên bấm "Lưu và thoát" hoặc tạm dừng: hệ thống gọi `/practice/save/{attemptId}` hoặc `/practice/pause/{attemptId}` để lưu các đáp án đã chọn (`answers_json`), các câu đánh dấu chưa chắc (`flags_json`), và thời gian đã thực hiện (`elapsed_seconds`).
  - Khi vào trang chuẩn bị (`/practice/prepare?typeCode=...`), hệ thống kiểm tra và hiển thị bài làm dở (`pendingAttempt`) với thời gian và số câu đã làm, cho phép bấm "Làm tiếp" hoặc hủy nháp.
  - Nếu bài nháp không có bất kỳ câu trả lời hay cờ đánh dấu nào (`!hasActivity`), hệ thống tự động xóa bản ghi nháp rác để giữ sạch CSDL.

### 3.6. Quy tắc Xem Kết Quả Bài Thi & Lọc Danh Sách Câu Theo Kỹ Năng
- Trang kết quả hiển thị bảng điểm chuẩn TOPIK (`ExamScoreCalculator`), phân tích số câu đúng/sai/bỏ qua, kèm xem lại chi tiết từng câu.
- **Bộ lọc kỹ năng trên trang kết quả Full Mock (`.rv-skill-tabs`)**:
  - Với đề Full Mock, cung cấp thanh tab điều hướng kỹ năng: `🎧 Nghe`, `📖 Đọc`, `✍️ Viết` (chỉ với TOPIK II) và `📑 Tất cả`.
  - Khi người học bấm chọn tab kỹ năng nào, hệ thống chỉ hiển thị các câu hỏi và các chip ô số bên sidebar thuộc về kỹ năng đó, giúp người học tập trung xem lại từng phần thi thuận tiện và dễ dàng kiểm soát.
  - Điểm tổng (`totalEarnedScore`) và điểm thành phần từng kỹ năng (Nghe, Đọc, Viết) vẫn được giữ nguyên đầy đủ trên bảng điểm tổng quan, phản ánh chính xác kết quả toàn bài thi.
- **QUY TẮC ĐẶC BIỆT**: Riêng trang kết quả của **Đề thi kỹ năng Viết** (`paper.kind == WRITING`), hệ thống **BẮT BUỘC KHÔNG HIỂN THỊ bộ lọc "Câu sai / Câu đúng"** vì bài thi Viết là câu tự luận, không áp dụng logic đúng/sai như trắc nghiệm.

---

## 4. Nghiệp Vụ Phân Hệ Kỹ Năng Viết (Writing Module)

- **Mã nguồn thẩm định**: `Writing5152Evaluator.java`, `WritingSubmissionService.java`.

### 4.1. Quy tắc Chấm Tự Động Câu 51 & Câu 52
Mỗi câu tối đa **10 điểm**, chia làm 2 vị trí điền: `㉠` (tối đa 5 điểm) và `㉡` (tối đa 5 điểm).
Mỗi vị trí điền được đánh giá trên 2 phương diện: **Nội dung hoàn thành (2.5 điểm)** + **Sử dụng ngôn ngữ (2.5 điểm)**.

#### Quy tắc câu 51 (Thư từ, Thông báo, Lời nhắn thường nhật):
- **BẮT BUỘC sử dụng đuôi câu kính ngữ trang trọng**:
  - `-습니다 / -습니까`, `-시기 바랍니다`, `-십시오`, `-(으)려고 합니다`.
- **HÌNH PHẠT**:
  - Nếu dùng đuôi văn viết (`-다 / -ㄴ다 / -는다`) hoặc đuôi thân mật (`-아요 / -어요`): **Trừ nặng 1.5 điểm** ở tiêu chí Sử dụng ngôn ngữ.
  - Lỗi chia sai trợ từ (`-이/가`, `-을/를`): Trừ 0.5 - 1.0 điểm.

#### Quy tắc câu 52 (Đoạn văn giải thích, định nghĩa khoa học/xã hội):
- **BẮT BUỘC sử dụng đuôi văn viết (ngữ thể tường thuật)**:
  - `-다 / -ㄴ다 / -는다`, `-라`.
- **HÌNH PHẠT**:
  - **TUYỆT ĐỐI CẤM sử dụng kính ngữ ở câu 52**.
  - Nếu dùng kính ngữ (`-습니다`, `-습니까`, `-아요/어요`, `-십시오`): **Trừ nặng 1.5 điểm** ở tiêu chí Sử dụng ngôn ngữ.
  - Lỗi chia sai trợ từ hoặc chính tả: Trừ 0.5 - 1.0 điểm.

### 4.2. Quy tắc Câu 53 (Giải thích Biểu đồ / Số liệu - 30 điểm)
- Dung lượng chuẩn: **200 - 300 chữ**.
- Yêu cầu: Nêu rõ chủ đề biểu đồ, cơ quan điều tra, số liệu tăng/giảm, phân tích nguyên nhân và triển vọng tương lai.
- Học viên luyện viết trên giao diện giấy thi TOPIK (Wonkwanji) có bộ đếm ký tự thực.

### 4.3. Quy tắc Câu 54 (Nghị luận Xã hội - 50 điểm)
- Dung lượng chuẩn: **600 - 700 chữ**.
- Yêu cầu: Trả lời 3 câu hỏi gợi ý của đề bài theo cấu trúc 3 phần: Mở bài (giới thiệu vấn đề), Thân bài (trả lời 2 ý chính kèm lập luận), Kết bài (nêu suy nghĩ cá nhân/giải pháp).

### 4.4. Quy tắc Gửi Bài Viết Cho Giáo Viên Chấm
- **Quyền hạn**: Chỉ dành riêng cho tài khoản **Học viên VIP (`user.isVip()`)**.
- **Phân tách Đề thi (Exam) và Luyện tập (Practice)**:
  - Hai bài làm này **hoàn toàn độc lập** với nhau kể cả khi cùng làm chung một mã đề.
  - Bài thi viết của học viên chỉ hiển thị điểm và lời nhận xét của giáo viên **KHI VÀ CHỈ KHI học viên đã thực sự bấm nút gửi bài đó cho giáo viên chấm**.
  - Nếu học viên làm một bài thi mới (nội dung khác với bài từng gửi), hệ thống không được tự ý gán điểm của bài cũ sang bài mới.
- **Phân trang danh sách bài viết**:
  - Lịch sử rèn luyện dạng, Lịch sử luyện Viết, Lịch sử làm đề: **5 lần làm bài / trang**.
  - Danh sách các bài viết gửi Giáo viên chấm (`/writing/feedback`): **10 nhóm bài / trang** (`WritingController.feedback`). Khi chuyển trang phân trang (không truyền `id`), hệ thống mặc định mở bài đầu tiên của trang đang xem; khi mở bài theo `id` mà không truyền `page`, hệ thống tự động tìm đúng trang chứa bài đó (`resolvedPage`) để đồng bộ danh sách sidebar.
- **Quy tắc Từ chối & Khiếu nại điểm số (`WritingSubmissionService`)**:
  - **Từ chối chấm (`rejectSubmission`)**: Chỉ áp dụng khi bài ở trạng thái `waiting`; hệ thống xóa phân công giáo viên, đưa trạng thái về `submitted` kèm `rejectReason` và gửi thông báo cho học viên. Trên UI `/writing/feedback`, banner thông báo từ chối hiển thị ở trên cùng và hướng dẫn học viên dùng nút `"Làm lại câu này"` ở tiêu đề trang (không tạo nút viết lại trùng lặp).
  - **Khiếu nại (`appealSubmission`)**: Mỗi bài đã chấm (`graded`) chỉ được khiếu nại tối đa **1 lần** (`appealUsed = true`, lý do 10–2000 ký tự). Hệ thống sao lưu `originalScore`, `originalFeedback`, `originalModelAnswer`, `originalGradedAt` và ghi nhận mốc thời gian gửi khiếu nại (`appealedAt`).
  - **Xử lý khiếu nại (`gradeSubmission`)**: Giáo viên xử lý chọn `revise` (sửa điểm/nhận xét) hoặc `uphold` (giữ nguyên điểm kèm `appealMessage` giải thích bắt buộc). Hệ thống đánh dấu `appealResolved = true` và cập nhật cả `appealResolvedAt` lẫn `gradedAt` (kể cả khi `uphold`), hiển thị đầy đủ 3 mốc thời gian (`submittedAt`, `appealedAt`, `appealResolvedAt`) cùng lời nhắn của Giáo viên tại khối đầu trang `/writing/feedback` và `grading-history.html`.

### 4.5. Quy tắc Tự Động Lưu Nháp & Lưu & Thoát Kỹ Năng Viết
- Hệ thống hỗ trợ tự động lưu nháp sau mỗi 15 giây khi người học đang gõ bài viết hoặc tự động gửi lưu qua `navigator.sendBeacon('/writing/draft', ...)` khi học viên đóng trình duyệt hoặc chuyển tab.
- Nút "Lưu & Thoát" lưu lại nội dung và số giây đã làm (`durationSeconds`), sau đó chuyển hướng học viên an toàn về danh mục luyện tập.
- Đối với câu 51-52, nội dung nháp được lưu dạng chuỗi phân cách `㉠ {đáp án A}\n㉡ {đáp án B}` và được tự động bóc tách điền ngược vào 2 ô trống khi mở lại bài viết.

### 4.6. Quy tắc Lưu Nháp Bài Đang Chấm Của Giáo Viên (`saveGradingDraft`)
- **Mục tiêu**: Bảo toàn dữ liệu điểm số và nhận xét dở dang khi Giáo viên đang chấm bài mà vô tình đóng tab, tải lại trang hoặc bấm link chuyển trang khác.
- **Quyền hạn & Ràng buộc**:
  - Chỉ Giáo viên sở hữu bài (`mentorId == user.getId()`) hoặc Quản trị viên khi bài ở trạng thái `claimed` hoặc `appeal`.
  - Giới hạn độ dài nhận xét: tối đa 6000 ký tự; lời nhắn khiếu nại: tối đa 2000 ký tự.
- **Giữ nguyên trạng thái & Không bắn thông báo**:
  - Khác với `gradeSubmission` (đổi trạng thái sang `graded` và gửi notification cho học viên), phương thức `saveGradingDraft` lưu tạm toàn bộ điểm thành phần/tổng điểm, nhận xét, đáp án mẫu và quyết định khiếu nại dở dang vào payload của bản ghi, **giữ nguyên trạng thái là `claimed` hoặc `appeal`**, và **tuyệt đối không gửi thông báo** cho học viên.
- **Cơ chế phát hiện thay đổi dở dang & Cảnh báo an toàn**:
  - JavaScript trên `teacher/dashboard.html` lắng nghe sự kiện `input` và `change` trên `#gradingForm` để kích hoạt cờ `isGradingDirty = true`.
  - Cảnh báo trình duyệt `beforeunload`: Hiển thị hộp thoại cảnh báo khi Giáo viên reload hoặc đóng tab mà có thay đổi chưa lưu.
  - Chặn điều hướng nội bộ (`a[href]`): Hiển thị modal xác nhận `#unsavedGradingModal` (với 3 lựa chọn: *Thoát không lưu*, *Tiếp tục chấm*, *Lưu và thoát*).
  - Nút **"💾 Lưu và thoát"**: Đặt cả ở thanh công cụ đầu bàn chấm và cạnh nút nộp kết quả. Khi bấm, gán `form.noValidate = true` (để tránh bị chặn bởi HTML5 required khi chưa chấm xong tất cả các mục), đổi form action sang `/teacher/save-draft` và gửi lưu an toàn về server.

---

## 5. Nghiệp Vụ Quản Lý Đề & Nhập Câu Hỏi (Content Management)

- **Mã nguồn thẩm định**: `source-detail.html`, `sources.html`, `ExamImportService.java`, `ExamImportValidator.java`, `ExamTemplateBuilder.java`, `ExamWorkbookParser.java`, `ContentExamController.java`.
- **Tải file Excel mẫu và Import Excel theo kỹ năng và toàn bộ đề**:
  - **Trang Nhập đề mới (`/content/sources`)**: Không hiển thị tải file mẫu theo kỹ năng tại trang này (đã loại bỏ dropdown tải mẫu cũ để tránh nhầm lẫn khi chưa chọn nguồn đề cụ thể).
  - **Trang Nhập đề chi tiết (`/content/sources/{id}`)**:
    - **Cạnh nút "Xem như học viên"**: Hiển thị nút `📥 Tải mẫu toàn bộ đề` (tạo file Excel đa sheet `NGHE`, `DOC`, `VIET`, `DANG_CAU`) và nút `📤 Import excel toàn bộ đề` cho phép nạp đề trọn gói một lần.
    - **Dưới tab kỹ năng (`.skill-tabs`)**: Khi chuyển sang kỹ năng nào (Nghe / Đọc / Viết), hệ thống hiển thị nút `📥 Tải mẫu [kỹ năng]` và `📤 Import excel [kỹ năng]` tương ứng của kỹ năng đó.
    - **File Excel mẫu kỹ năng Nghe**: Đã loại bỏ cột `Tên file nghe` (tổng 11 cột). Hệ thống tự động liên kết audio theo số thứ tự câu hỏi từ danh sách media đã tải lên. Trường hợp chưa tải file âm thanh, hệ thống chỉ sinh cảnh báo (warning) nhắc nhở chứ không chặn kích hoạt đề.
    - **File Excel mẫu toàn bộ đề**: Mỗi kỹ năng được phân chia làm 1 sheet riêng biệt (`NGHE`, `DOC`, `VIET`), giữ nguyên 100% cấu trúc cột và tiêu đề tiếng Việt như file mẫu từng kỹ năng.
    - **Đồng bộ tên dạng câu**: Tên dạng câu trong các file template Excel và giao diện nhập đề luôn được đồng bộ tự động theo danh mục chuẩn mới nhất của `QuestionTypeCatalog`.
- **Quy tắc kiểm tra ô số trên lưới (`num-grid`)**:
  - Mỗi câu hỏi trong card bắt buộc phải được gán tương ứng vào một ô số trên lưới (`numbers.size() == count`).
  - Đối với dạng câu dùng chung theo Question Type Catalog (ví dụ dạng 2 câu như câu 19-20): Bắt buộc người nhập phải chọn đủ tất cả các ô số thuộc nhóm đó trên lưới; nếu chọn thiếu ô, hệ thống sẽ ngăn chặn lưu và yêu cầu chọn đủ.
  - Hàm tính số câu `askCount(form)` ưu tiên xác định số câu theo danh sách ô thực tế `form.qno()`, ngăn ngừa lỗi sai lệch đếm dữ liệu ngầm.
- **Hỗ trợ ảnh đề bài câu hỏi**:
  - Cho phép chọn ảnh làm đề bài cho **TẤT CẢ các câu hỏi** (kỹ năng Nghe, Đọc và Viết), không giới hạn riêng câu 53-54.
  - Đồng bộ hiển thị ảnh đề bài trong màn hình "Xem như học viên" (`/content/sources/{id}/preview`): Ưu tiên hiển thị ảnh riêng của từng câu hỏi con (`ask.image()`), nếu không có mới lấy ảnh chung của bài (`bai.image()`).
- **Khung nhập 4 phương án trắc nghiệm**:
  - Hỗ trợ **VỪA NHẬP TEXT VỪA TẢI ẢNH ĐỒNG THỜI** cho từng phương án (cả hai cùng tồn tại, không bắt buộc chọn 1 trong 2).
  - Tên file ảnh phương án được lưu trữ riêng biệt (`opt1Img`, `opt2Img`, `opt3Img`, `opt4Img`), không được ghi đè vào ô text của phương án.
  - Khi tải ảnh lên, giao diện phải hiển thị ảnh xem trước (preview) ngay lập tức với kích thước vừa mắt để người quản lý kiểm tra.
- **File âm thanh bài nghe (MP3) & Upload Audio Hàng Loạt (.zip)**:
  - Hỗ trợ tải file lẻ (kéo thả hoặc chọn file MP3) trực tiếp tại form từng câu hỏi qua API `/content/sources/{id}/media/inline`. Khi tải thành công, file tự động thêm vào bảng danh sách Tệp đính kèm (`#tools`).
  - Hỗ trợ nút `🎵 Upload audio hàng loạt (.zip)` trên tab Nghe:
    - Cho phép nạp 1 file nén `.zip` chứa toàn bộ các file MP3 của đề thi.
    - **Quy tắc đặt tên file audio**: Tên file phải chứa số thứ tự câu hỏi để hệ thống tự động bóc tách và phân tán đúng câu (ví dụ `01.mp3`, `14.mp3`, hoặc file nghe chung nhiều câu như `21, 22.mp3` hay `21-22.mp3`).
    - **Quy trình 2 bước (Pre-check & Confirm)**:
      - *Pre-check*: Giải nén vào thư mục tạm thời, hỗ trợ fallback đa bảng mã (`UTF-8`, `MS949`, `ISO-8859-1`) nhằm tránh lỗi font chữ tiếng Hàn. Hệ thống trích xuất số câu hỏi, đối soát với các câu hỏi hiện có của đề và hiển thị Modal báo cáo: tổng số câu kỳ vọng, số file hợp lệ, danh sách câu thiếu audio, danh sách file lỗi/không khớp.
      - *Confirm*: Quản lý kiểm tra báo cáo và bấm xác nhận; hệ thống chính thức sao chép file vào thư mục lưu trữ `sources/{id}/media`, tạo bản ghi `MediaAsset` và gán tự động vào trường `audioFile` của `QuestionGroup`.
      - *Cancel*: Tự động xóa sạch thư mục tạm khi người dùng bấm Hủy hoặc đóng modal mà chưa xác nhận.
  - Kiểm tra định dạng bắt buộc phải là `.mp3` (hoặc MIME type `audio/mpeg`).
- **Đồng Bộ Dữ Liệu Tệp Đính Kèm & Quản Lý Hàng Loạt (`#tools`)**:
  - Khi xóa file âm thanh ở từng câu (nút `🗑 Xóa` tại form câu): Tự động gọi API `/content/sources/{id}/media/clear-card-audio` để gỡ bỏ file khỏi `QuestionGroup`. Nếu không còn câu nào khác dùng chung file này, hệ thống tự động xóa bản ghi `MediaAsset` và gỡ bỏ dòng tương ứng trong bảng "Tệp đính kèm đã tải lên" trên giao diện.
  - Quản lý xóa file hàng loạt: Hỗ trợ ô checkbox chọn từng file hoặc chọn tất cả (`#selectAllMediaCb`), nút `🗑 Xóa file đã chọn` (`/media/delete-batch`) và nút `💥 Xóa toàn bộ file` (`/media/delete-all`).
  - Streaming audio: Header `Content-Type` được gán chính xác `audio/mpeg` (không bị Tomcat tự ý thêm `;charset=UTF-8`), hỗ trợ Byte-Range request (HTTP 206) để phát mượt mà và tua audio trên trình duyệt.
- **Xóa bỏ các khung trùng lặp**:
  - Đã loại bỏ các card import trùng lặp ở cuối trang nhập câu hỏi dạng manual (`#tools`), chỉ giữ lại bảng quản lý danh sách file âm thanh (.mp3) và ảnh đính kèm đã tải lên.

---

## 6. Nghiệp Vụ Báo Lỗi & Xử Lý Sự Cố Câu Hỏi (Report & Correction)

- **Mã nguồn thẩm định**: `QuestionReportService.java`, `QuestionCorrectionService.java`.
- **Luồng xử lý**:
  1. Người học phát hiện lỗi (sai đáp án, lỗi chính tả, dịch nhầm) $\rightarrow$ Bấm nút "Báo lỗi câu hỏi" trên giao diện làm bài.
  2. Báo cáo được gửi đến Cổng Vận hành (`/operation`).
  3. Quản lý vận hành kiểm tra, phối hợp với Content Manager để sửa lại nội dung.
  4. Lưu lại bản ghi lịch sử sửa đổi (`QuestionRevision`).
  5. **Cộng điểm bù tự động**: Nếu lỗi thuộc về việc "Sai đáp án đúng" dẫn đến học viên bị chấm sai oan, hệ thống cho phép kích hoạt tính năng rà soát các lượt thi trước đó của học viên để cộng bù điểm và gửi thông báo xin lỗi đến học viên.

---

## 7. Nghiệp Vụ Tiến Độ Học Tập & Biểu Đồ Thi Thử (Progress & Best Attempt Analytics)

- **Mã nguồn thẩm định**: `ProgressService.java`, `LearnerController.java`, `progress/index.html`.

### 7.1. Biểu Đồ Cột Chồng Điểm Thi Thử Theo Thời Gian (Stacked Bar Chart)
- **Mục tiêu**: Trực quan hóa kết quả thi thử Full Mock của học viên theo thời gian mà không bị rối mắt khi làm đi làm lại một đề nhiều lần, đồng thời thể hiện rõ cơ cấu điểm số đóng góp của từng kỹ năng.
- **Quy tắc tính toán và tổng hợp dữ liệu (`buildMockBarStats`)**:
  1. **Lọc lượt thi hợp lệ**: Chỉ lấy các bản ghi `ExamAttempt` của bài thi Full Mock (`exam.kind == FULL`) đã nộp bài (`completed_at IS NOT NULL`) của người học.
  2. **Gom nhóm theo đề thi (`exam.id`)**: Toàn bộ các lần làm bài của cùng một đề thi được gom lại để so sánh.
  3. **Lựa chọn Best Attempt (Lần làm cao điểm nhất)**:
     - Hệ thống tính toán điểm chi tiết cho từng lượt làm qua `ExamScoreCalculator.calculate(...)`.
     - So sánh tổng điểm đạt được (`score.getTotalEarnedScore()`).
     - Lượt thi có điểm cao nhất được chọn làm **Best Attempt** đại diện cho cột của đề thi đó trên biểu đồ.
     - Trường hợp có nhiều lượt làm đạt cùng số điểm cao nhất bằng nhau, hệ thống ưu tiên chọn lượt làm có thời gian nộp bài (`completedAt`) mới nhất.
  4. **Thống kê số lần làm bài**: Ghi nhận tổng số lần học viên đã thử sức với đề thi đó (`attemptCount = examAttemptsList.size()`).
  5. **Phân rã thành phần điểm xếp chồng**:
     - 🎧 **Nghe (Listening)**: Màu xanh lục (`#1F6B56`).
     - 📖 **Đọc (Reading)**: Màu xanh dương (`#2563EB`).
     - ✍️ **Viết (Writing)**: Màu cam hổ phách (`#D97706` - chỉ có ở TOPIK II).
- **Phân tách Track TOPIK I vs TOPIK II**:
  - Giao diện cung cấp nút chuyển đổi: **TOPIK I** (trục Y tối đa 200 điểm) và **TOPIK II** (trục Y tối đa 300 điểm).
- **Tooltip thông tin phong phú**:
  - Tên đầy đủ của đề thi (ví dụ: "Đề thi thử TOPIK I số 1").
  - Ngày nộp bài của Best Attempt (`dd/MM/yyyy`).
  - Số lần thử sức: `Số lần thử: X lần`.
  - Tổng điểm: `Điểm tổng: X/200 đ` (hoặc `X/300 đ`).
  - Điểm chi tiết: `🎧 Nghe: Xđ · 📖 Đọc: Yđ · ✍️ Viết: Zđ`.
  - Gợi ý click: `👉 Nhấp để xem chi tiết bài làm`.
- **Tương tác click mở trang kết quả**:
  - Khi học viên bấm vào bất kỳ vị trí nào trên cột hoặc nhãn tên đề, Chart.js bắt sự kiện click và chuyển hướng trình duyệt đến `/ket-qua/{attemptId}` của Best Attempt đó.

---

## 8. Nghiệp Vụ Cầu Nối Hán Việt & Luyện Tập Từ Vựng (Hanja Theory & Practice)

- **Mã nguồn thẩm định**: `TheoryController.java`, `LearnerHanjaController.java`, `HanjaService.java`, `HanjaServiceImpl.java`, `theory/hanja.html`.
- **4 Chế độ học tập & Luyện tập chuyên sâu**:
  1. **📖 Học theo gốc (`mode=learn`)**:
     - Lưới 4 cột hiển thị toàn bộ các gốc Hán tự (cấu trúc CSS Grid `repeat(4, minmax(0, 1fr))` chống tràn layout và có cuộn nội bộ mượt).
     - Bảng chi tiết gốc Hán: Hiển thị âm Hàn, âm Hán Việt, nghĩa gốc, phát âm Audio và danh sách từ vựng cùng họ.
     - Đánh dấu đã học: Cho phép học viên bấm nút `+` / `✓` để đánh dấu từng từ riêng lẻ đã học, hoặc bấm "Đánh dấu đã học cả gốc này" để đánh dấu toàn bộ gốc kèm các từ liên quan.
  2. **🎯 Đoán nghĩa từ mới (`mode=guess`)**:
     - Bài quiz trắc nghiệm 4 phương án ngẫu nhiên từ kho từ Hán tự.
     - **Tự động lưu từ làm sai**: Bất kỳ từ nào học viên chọn sai đáp án sẽ tự động được ghi nhận vào danh sách `⚠️ Cần luyện tập` (`hanjaMistakes` trong `localStorage`) kèm thông tin từ vựng và số lần làm sai.
  3. **✅ Từ đã học (`mode=learned`)**:
     - Danh sách tổng hợp toàn bộ các từ vựng người học đã đánh dấu đã học (tự động kế thừa từ các gốc đã học trước đó).
     - Cho phép tìm kiếm nhanh theo từ khóa, nghe phát âm và bỏ đánh dấu nếu cần ôn lại.
     - **🎯 Luyện tập từ đã học**: Tự động trích xuất các từ đã thuộc để sinh bài quiz trắc nghiệm 4 đáp án (lấy nghĩa nhiễu từ kho nghĩa Hán Việt) để học viên ôn luyện và củng cố trí nhớ.
  4. **⚠️ Cần luyện tập (`mode=mistakes`)**:
     - Danh sách các từ vựng học viên từng chọn sai đáp án trong bài quiz "Đoán nghĩa từ mới".
     - Hỗ trợ nút **"🎯 Luyện tập từ cần nhớ"** tạo đề quiz ôn tập riêng cho các từ đang bị sai.
     - **Hai cơ chế tự động gỡ từ khỏi danh sách cần luyện**:
       - *Cơ chế 1 (Học lại & Đánh dấu)*: Học viên bấm nút `✓ Đã học` ngay trong danh sách cần luyện hoặc đánh dấu đã học tại bảng chi tiết gốc $\rightarrow$ từ tự động được gỡ bỏ khỏi danh sách cần luyện và chuyển sang danh sách từ đã học.
       - *Cơ chế 2 (Luyện tập chọn đúng)*: Khi học viên làm bài quiz trong chế độ "Luyện tập từ cần nhớ" và chọn trúng đáp án đúng $\rightarrow$ hệ thống ngay lập tức gỡ từ đó khỏi danh sách cần luyện tập, cập nhật lại số lượng huy hiệu trên tab và thanh thống kê.
