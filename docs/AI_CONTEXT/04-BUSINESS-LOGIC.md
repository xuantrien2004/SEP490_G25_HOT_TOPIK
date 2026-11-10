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

### 3.1. Cấu trúc Đề thi TOPIK chuẩn
- **TOPIK I (Sơ cấp)**:
  - Gồm 2 kỹ năng: **Nghe (30 câu, 100 điểm, 40 phút)** + **Đọc (40 câu, 100 điểm, 60 phút)**.
  - Tổng điểm: **200 điểm**. Thời gian làm bài: **100 phút liên tục**.
- **TOPIK II (Trung & Cao cấp)**:
  - Gồm 3 kỹ năng: **Nghe (50 câu, 100 điểm, 60 phút)** + **Viết (4 câu 51-54, 100 điểm, 50 phút)** + **Đọc (50 câu, 100 điểm, 70 phút)**.
  - Tổng điểm: **300 điểm**. Thời gian làm bài: **180 phút** (chia thành 2 ca thi theo chuẩn thực tế).

### 3.2. Chế độ Mini Test
- Cho phép người học thi riêng từng kỹ năng ngắn: **Mini Nghe** (15-20 câu), **Mini Đọc** (15-20 câu), **Mini Viết** (câu 51-52 hoặc 53).

### 3.3. Quy tắc Hiển thị & Điều hướng Câu hỏi Nhóm vs Câu Đơn (Grouped vs Single Questions)
- **Câu hỏi nhóm dùng chung (Grouped Questions - từ 2 câu trở lên)**:
  - Khi 2 hoặc 3 câu hỏi cùng sử dụng chung 1 đoạn văn, 1 file audio hoặc 1 hình ảnh (`matchingCards.length >= 2`):
    - Hệ thống hiển thị **đồng thời cả nhóm câu hỏi trên màn hình** để người học dễ theo dõi.
    - **Cơ chế Sticky Neo đỉnh màn hình (`.qp-group-sticky`)**: Khi người học cuộn xuống làm câu 2 hoặc câu 3 trong nhóm, **file audio, đoạn văn và hình ảnh dùng chung sẽ tự động ghim cố định ở phía trên** màn hình với nút bấm "Thu gọn / Mở rộng", không bị trôi đi.
    - **Nút chuyển câu**: Chỉ hiển thị nút "Câu tiếp" và "Câu trước" ở **cuối nhóm** (ở câu cuối cùng trong nhóm). Nút "Câu trước" của nhóm phải nhảy về câu đầu của nhóm trước đó (không nhảy lắt nhắt trong nhóm).
- **Câu hỏi đơn (Single Questions - 1 câu/bài)**:
  - Đối với các câu hỏi đơn lẻ bình thường (`matchingCards.length < 2`):
    - **TUYỆT ĐỐI KHÔNG BẬT CHẾ ĐỘ STICKY**: Khung `.qp-group-sticky` bị ẩn hoàn toàn (`qp-group-mode = false`).
    - Ngữ liệu của câu hỏi (file nghe, đoạn văn đọc, hoặc ảnh đề bài) hiển thị trực tiếp bên trong thẻ của chính câu hỏi đó như giao diện câu đơn truyền thống.
    - Nút chuyển câu "Câu trước" và "Câu tiếp" chuyển câu tuần tự từng câu một (+/- 1 câu).
- **Quy tắc chung cho cả hai loại câu**:
  - **Nút "Chưa chắc" & "Báo lỗi"**: Luôn luôn giữ nguyên ở **từng câu hỏi riêng biệt**, không bị ẩn đi.
  - **Highlight Phiếu câu hỏi**: Khi người học cuộn màn hình đến câu nào, thanh "Phiếu câu hỏi" bên cạnh phải cập nhật và đánh dấu chính xác câu đó bằng công nghệ `IntersectionObserver`.

### 3.4. Quy tắc Xem Kết Quả Bài Thi
- Trang kết quả hiển thị bảng điểm chuẩn TOPIK (`ExamScoreCalculator`), phân tích số câu đúng/sai/bỏ qua, kèm xem lại chi tiết từng câu.
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
  - Danh sách các bài viết gửi Giáo viên chấm: **10 bài / trang**.

---

## 5. Nghiệp Vụ Quản Lý Đề & Nhập Câu Hỏi (Content Management)

- **Mã nguồn thẩm định**: `source-detail.html`, `ExamImportService.java`, `ContentExamController.java`.
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
- **File âm thanh bài nghe (MP3)**:
  - Hỗ trợ kéo thả hoặc chọn file từ máy tính.
  - Kiểm tra định dạng bắt buộc phải là `.mp3` (hoặc MIME type `audio/mpeg`). Nếu không đúng, báo lỗi cảnh báo và từ chối tải lên.
- **Xóa bỏ các khung trùng lặp**:
  - Đã loại bỏ khung "File nghe và ảnh" thừa ở cuối trang nhập câu hỏi dạng manual để tránh nhầm lẫn cho quản lý nội dung.

---

## 6. Nghiệp Vụ Báo Lỗi & Xử Lý Sự Cố Câu Hỏi (Report & Correction)

- **Mã nguồn thẩm định**: `QuestionReportService.java`, `QuestionCorrectionService.java`.
- **Luồng xử lý**:
  1. Người học phát hiện lỗi (sai đáp án, lỗi chính tả, dịch nhầm) $\rightarrow$ Bấm nút "Báo lỗi câu hỏi" trên giao diện làm bài.
  2. Báo cáo được gửi đến Cổng Vận hành (`/operation`).
  3. Quản lý vận hành kiểm tra, phối hợp với Content Manager để sửa lại nội dung.
  4. Lưu lại bản ghi lịch sử sửa đổi (`QuestionRevision`).
  5. **Cộng điểm bù tự động**: Nếu lỗi thuộc về việc "Sai đáp án đúng" dẫn đến học viên bị chấm sai oan, hệ thống cho phép kích hoạt tính năng rà soát các lượt thi trước đó của học viên để cộng bù điểm và gửi thông báo xin lỗi đến học viên.
