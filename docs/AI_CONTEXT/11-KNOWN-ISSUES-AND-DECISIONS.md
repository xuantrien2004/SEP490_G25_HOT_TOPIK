# 11 - CÁC LỖI ĐÃ BIẾT VÀ QUYẾT ĐỊNH KỸ THUẬT (KNOWN ISSUES & DECISIONS)

> **Trạng thái**: Đã ghi nhận và tổng hợp từ toàn bộ lịch sử phát triển và sửa lỗi của dự án.  
> **Dành cho AI Agent**: Đọc kỹ file này để không lặp lại các sai lầm kỹ thuật cũ và hiểu rõ lý do đằng sau các quyết định kiến trúc đã được chốt với người dùng.

---

## 1. Các Quyết Định Kỹ Thuật Đã Thống Nhất (Architectural & Business Decisions)

### Quyết định 1: Tách biệt triệt để Bài nộp Viết giữa Luyện Tập và Đề Thi
- **Bối cảnh**: Ban đầu, khi học viên làm đề thi kỹ năng Viết (câu 51-54), hệ thống vô tình hiển thị tiến độ và kết quả của câu đó trong phần Luyện tập. Đồng thời, nếu học viên từng gửi một bài câu 51 cho giáo viên chấm thì mọi bài thi sau này có câu 51 đều tự động hiển thị điểm số và lời phê của giáo viên đó.
- **Quyết định đã chốt**:
  1. Hai phân hệ **Luyện tập (Practice)** và **Đề thi (Exam)** là **hoàn toàn độc lập** về mặt dữ liệu và tiến độ.
  2. Một bài làm chỉ được coi là có điểm và lời phê của giáo viên **KHI VÀ CHỈ KHI học viên đã chủ động bấm nút "Gửi Giáo viên chấm" cho chính lượt làm bài (attempt) đó**.
  3. Kể cả khi cùng một đề bài, nếu học viên làm một lượt thi mới (nội dung bài làm khác với bài đã từng gửi giáo viên) và chưa gửi giáo viên chấm, hệ thống **tuyệt đối không được gán điểm hay hiển thị lời phê của bài cũ**.

### Quyết định 2: Loại bỏ bộ lọc Câu đúng / Câu sai ở trang kết quả đề thi Viết
- **Bối cảnh**: Trang kết quả bài thi trắc nghiệm Nghe/Đọc có thanh lọc: "Tất cả", "Câu sai", "Câu đúng".
- **Quyết định đã chốt**:
  - Đối với đề thi kỹ năng Viết thuần túy (`paper.kind == WRITING`), **loại bỏ hoàn toàn bộ lọc Câu đúng / Câu sai** vì bài thi viết là câu tự luận, không có khái niệm đúng/sai nhị phân như trắc nghiệm.
  - Mã nguồn: `templates/exam/result.html` có điều kiện `th:if="${!#lists.isEmpty(paper.questions) and paper.kind.name() != 'WRITING'}"`.

### Quyết định 3: Quy chuẩn phân trang hiển thị cho người học
- **Quyết định đã chốt**:
  - Lịch sử rèn luyện dạng câu hỏi, Lịch sử luyện Viết, Lịch sử làm đề: **5 lần làm bài trên 1 trang**.
  - Danh sách các bài viết gửi Giáo viên chấm: **10 bài trên 1 trang**.

### Quyết định 4: Trải nghiệm người dùng với Câu hỏi nhóm vs Câu hỏi đơn
- **Bối cảnh**: Khi 2-3 câu hỏi cùng sử dụng chung 1 đoạn văn hoặc file audio, ban đầu hệ thống sử dụng khung cố định `.qp-group-sticky` ghim ở đầu màn hình. Tuy nhiên qua sử dụng thực tế, người học nhận thấy khung ghim cố định chiếm nhiều diện tích, gây khó nhìn và khó thao tác cuộn.
- **Quyết định đã chốt**:
  1. **Hiển thị ngữ liệu trực tiếp trên từng câu hỏi**:
     - Loại bỏ hoàn toàn khung sticky `.qp-group-sticky`.
     - Thay vào đó, đoạn văn bản chung hoặc audio/hình ảnh chung được **lặp lại và hiển thị trực tiếp bên trong thẻ của từng câu hỏi con** trong nhóm. Khi người học cuộn xuống làm câu 2 hoặc câu 3, ngữ liệu luôn nằm ngay tầm mắt cùng với các phương án trả lời.
     - Áp dụng đồng bộ cho cả **Đề thi** (`/exam/play.html`) và **Luyện tập** (`/practice/play.html`).
  2. **Gộp câu nhóm trong một lượt làm**:
     - Cả Đề thi và Luyện tập dạng câu hỏi đều gộp toàn bộ các câu hỏi dùng chung ngữ liệu (2-3 câu) để làm cùng lúc trong 1 lần hiển thị.
     - Nút "Câu trước" / "Câu tiếp" chuyển theo từng khối nhóm câu hỏi thay vì ngắt lẻ từng câu con.
  3. Nút **"Chưa chắc"** và **"Báo lỗi câu hỏi"** bắt buộc **giữ nguyên ở từng câu riêng biệt** trong nhóm.
  4. **Đánh dấu Phiếu câu hỏi**: Sử dụng `IntersectionObserver` trong `question-player.js` để phát hiện câu nào đang nằm trong tầm mắt người học và cập nhật highlight chính xác số câu đó trên thanh điều hướng bên phải.

### Quyết định 5: Hỗ trợ linh hoạt Text + Ảnh cho 4 phương án trắc nghiệm
- **Bối cảnh**: Có những câu hỏi ngữ pháp chỉ có text, nhưng có những câu hỏi nghe/đọc cần phương án là hình ảnh (tranh vẽ, biển báo, biểu đồ), hoặc vừa có ảnh minh họa vừa có chú thích text bên dưới.
- **Quyết định đã chốt**:
  1. Khung nhập 4 phương án trắc nghiệm trong `source-detail.html` hỗ trợ **VỪA NHẬP TEXT VỪA TẢI ẢNH ĐỒNG THỜI** cho từng phương án.
  2. Tên file ảnh phương án được lưu trữ ở trường riêng (`opt1Img..opt4Img`), không ghi đè vào trường text.
  3. Mở rộng tính năng tải **ảnh đề bài cho TẤT CẢ các câu hỏi** (kể cả Nghe và Đọc), không giới hạn riêng câu 53-54 của phần Viết.
  4. File nghe tải lên bắt buộc phải là `.mp3` (hoặc `audio/mpeg`); nếu sai định dạng hệ thống từ chối tải lên ngay lập tức.
  5. Loại bỏ khung "File nghe và ảnh" thừa ở cuối trang nhập đề thủ công.

### Quyết định 6: Chuẩn hóa tính điểm bài thi và Đánh giá tự động câu 51 & 52
- **Bối cảnh**: Đề thi cần thang điểm chuẩn hóa mô phỏng kỳ thi thật TOPIK, đồng thời câu điền cụm từ 51 và 52 cần phản hồi và dự tính điểm ngay cho học viên trước khi có giáo viên chấm thủ công.
- **Quyết định đã chốt**:
  1. Áp dụng chuẩn thang điểm qua `ExamScoreCalculator`:
     - TOPIK I: Nghe 30 câu (100đ), Đọc 40 câu (100đ) $\rightarrow$ Tổng 200 điểm.
     - TOPIK II: Nghe 50 câu (100đ), Đọc 50 câu (100đ), Viết 4 câu (100đ) $\rightarrow$ Tổng 300 điểm.
  2. Tự động chấm và phản hồi chi tiết câu 51 & 52 qua `Writing5152Evaluator`:
     - Mỗi câu tối đa 10 điểm (chia 5 điểm cho ý `㉠` và 5 điểm cho ý `㉡`).
     - Tách biệt kiểm tra Nội dung hoàn thành (2.5đ) và Sử dụng ngôn ngữ (2.5đ). Phạt nặng -1.5đ nếu sai đuôi câu (câu 51 bắt buộc kính ngữ trang trọng `-습니다/-습니까`, câu 52 bắt buộc văn viết trần thuật `-ㄴ/는다` và tuyệt đối cấm kính ngữ).

### Quyết định 7: Lưu và khôi phục chính xác thời gian khi Tạm dừng / Lưu & Thoát Đề thi (`remainingSeconds`)
- **Bối cảnh**: Khi người học làm đề thi và bấm "Lưu và thoát" hoặc tắt trình duyệt, nếu hệ thống không ghi nhận chính xác thời gian còn lại từng giây mà khôi phục lại mốc làm tròn cũ (ví dụ còn 65:10 nhưng mở lại vẫn là 66:44) sẽ gây sai lệch tính công bằng và thời gian làm bài thực tế.
- **Quyết định đã chốt**:
  1. Khi người học bấm "Lưu và thoát" hoặc rời khỏi trang, client gửi request kèm tham số `remainingSeconds` chính xác tại thời điểm thoát đến server.
  2. Bảng `exam_attempts` lưu trữ trường `remaining_seconds` cập nhật mới nhất.
  3. Khi người học bấm "Làm tiếp" trên giao diện bài thi đang dở dang (`/exam/play/{attemptId}`), đồng hồ đếm ngược được khởi tạo chính xác từ `remaining_seconds` đã lưu trữ, đảm bảo không bị gian lận hoặc sai lệch thời gian làm bài.

### Quyết định 8: Lưu trạng thái dở dang và Ngăn chặn trùng lặp câu hỏi khi Luyện tập
- **Bối cảnh**: Khi người học luyện tập dạng câu hỏi mà thoát ra giữa chừng, cần lưu lại toàn bộ tiến độ (câu đã làm, đáp án đã chọn, cờ đánh dấu câu chưa chắc, thời gian đã làm). Nếu lịch sử đang có bài dở dang mà người học không làm tiếp mà bấm "Luyện bài mới", hệ thống không được bốc lại các câu hỏi đang nằm trong bài làm dở dang đó để tránh trùng lặp.
- **Quyết định đã chốt**:
  1. Thêm cột `elapsed_seconds` (INT) và `flags_json` (VARCHAR(4000)) vào bảng `practice_attempts` để lưu trữ thời gian đã luyện tập và cờ đánh dấu chưa chắc.
  2. Bổ sung các endpoint AJAX `/practice/save/{attemptId}` (lưu định kỳ trong lúc làm) và `/practice/pause/{attemptId}` (lưu khi bấm Tạm dừng / Thoát).
  3. Trên trang chuẩn bị (`/practice/prepare.html`): Nếu người dùng có một attempt chưa hoàn tất (`completed_at IS NULL`), hệ thống hiển thị modal thông báo với 2 lựa chọn rõ ràng:
     - "Làm tiếp": Tiếp tục làm bài đang dở với đầy đủ trạng thái câu hỏi, đáp án, và bộ đếm thời gian tiếp nối `elapsed_seconds`.
     - "Luyện bài mới": Hệ thống hủy/bỏ qua attempt dở dang cũ và sinh đề luyện tập mới.
  4. Thuật toán chọn câu hỏi trong `PracticeService.findCandidateQuestions`:
     - Gom nhóm câu hỏi dùng chung đoạn văn/audio để đưa vào cùng 1 lượt luyện tập.
     - Lọc bỏ triệt để các `question_id` đang nằm trong các attempt chưa hoàn thành (`completed_at IS NULL`) của học viên đó trước khi bốc câu hỏi mới.

### Quyết định 9: Chuẩn hóa kiến trúc Tải mẫu và Nhập Excel Đề thi theo Kỹ năng và Toàn bộ đề
- **Bối cảnh**: Ban đầu chức năng tải file mẫu đặt tại trang danh sách nguồn đề (`/content/sources.html`) dạng dropdown kỹ năng, gây bất tiện khi quản lý nhiều đề. Đồng thời, giáo viên cần khả năng nhập riêng từng kỹ năng (Nghe, Đọc, Viết) hoặc nhập toàn bộ đề một lần với cấu trúc file mẫu đồng nhất, dễ hiểu.
- **Quyết định đã chốt**:
  1. **Tổ chức giao diện**:
     - Bỏ dropdown tải mẫu theo kỹ năng tại trang danh sách (`/content/sources.html`).
     - Tại trang chi tiết nguồn đề (`/content/sources/{id}`):
       - Đặt 2 nút "Tải excel mẫu" và "Nhập excel" cho **toàn bộ đề** ở thanh công cụ trên cùng, cạnh nút "Xem như học viên".
       - Đặt các nút "Tải excel mẫu" và "Nhập excel" cho **từng kỹ năng** trực tiếp bên trong từng tab kỹ năng tương ứng (Nghe, Đọc, Viết).
  2. **Cấu trúc file Excel mẫu**:
     - **File mẫu Toàn bộ đề**: Gộp toàn bộ các kỹ năng vào 1 workbook với các sheet riêng biệt: `NGHE`, `DOC`, `VIET` và `DANG_CAU` (sheet tra cứu mã & tên dạng câu hỏi). Tên các cột thông tin bằng tiếng Việt đồng nhất hoàn toàn với file mẫu của từng kỹ năng.
     - **File mẫu Kỹ năng Nghe**: Loại bỏ cột `Tên file nghe`. Hệ thống hỗ trợ tải file audio sau hoặc tự động liên kết theo quy ước thư viện media.
     - **Đồng bộ tên dạng câu hỏi**: Toàn bộ tên dạng câu trong file Excel được đồng bộ động bằng tiếng Việt theo `QuestionTypeCatalog` để giáo viên dễ dàng đối chiếu khi nhập liệu.
  3. **Xác thực import (`ExamImportValidator`)**:
     - Nới lỏng kiểm tra file nghe từ mức Chặn (Blocker) sang Cảnh báo (Warning) cho phép nhập đề trước rồi bổ sung file audio sau trên giao diện quản lý.

### Quyết định 10: Cơ Chế Upload Audio Hàng Loạt (.zip) & Phân Bổ Tự Động Kỹ Năng Nghe
- **Bối cảnh**: Kỹ năng Nghe TOPIK gồm 30 đến 50 câu; việc upload thủ công từng file MP3 rất tốn thời gian. Giáo viên cần nén toàn bộ các file MP3 vào 1 file `.zip` rồi tải lên một lần. Tên file theo định dạng số thứ tự câu hỏi (ví dụ `01.mp3`, `14.mp3`, `21, 22.mp3`...).
- **Quyết định đã chốt**:
  1. Thêm nút "Upload audio hàng loạt (.zip)" trong tab Nghe tại trang chi tiết nguồn đề (`source-detail.html`).
  2. Quy trình Pre-check & Confirm 2 bước:
     - **Pre-check**: Giải nén tạm thời vào `temp_bulk_audio/{uuid}`, tự động thử đa bảng mã (`UTF-8`, `MS949`, `ISO-8859-1`) để giải mã tên file tiếng Hàn; bóc tách số nguyên câu hỏi (kể cả câu đơn và câu gộp 21, 22); đối soát với cấu trúc đề thi và trả về thống kê: số file hợp lệ, số câu thiếu file nghe, danh sách file lỗi/không khớp.
     - **Confirm**: Người dùng xem trước báo cáo trên modal, bấm xác nhận mới chính thức lưu vào thư mục `sources/{id}/media`, tạo bản ghi `MediaAsset` và gán tự động `audioFile` vào `QuestionGroup`.
     - **Cancel**: Tự động xóa sạch thư mục tạm khi người dùng bấm Hủy hoặc đóng modal mà chưa xác nhận.

### Quyết định 11: Đồng Bộ Hóa Hai Chiều Giữa Form Nhập Câu Hỏi & Bảng Quản Lý Tệp Đính Kèm
- **Bối cảnh**: Khi upload audio cho từng câu hỏi hoặc xóa file audio ở câu hỏi, dữ liệu không đồng bộ với bảng "Tệp đính kèm đã tải lên" (`#tools`). Ngoài ra, người dùng cần công cụ xóa hàng loạt để dọn dẹp file nhanh.
- **Quyết định đã chốt**:
  1. Khi thêm audio cho câu hỏi (`POST /content/sources/{id}/media/inline`): Frontend gửi form multipart có `files`, server lưu file và trả về JSON metadata; JS tự động thêm dòng mới vào bảng `#mediaTableBody` và tăng bộ đếm số lượng file.
  2. Khi xóa audio ở câu hỏi (`POST /content/sources/{id}/media/clear-card-audio`): Server gỡ liên kết khỏi `QuestionGroup`, kiểm tra nếu không còn câu nào khác dùng chung thì xóa bản ghi `MediaAsset` và dọn dẹp file; frontend tự động xóa dòng tương ứng trong bảng tệp đính kèm mà không cần tải lại trang.
  3. Bổ sung tính năng chọn nhiều file bằng checkbox, nút "🗑 Xóa file đã chọn" (`/media/delete-batch`) và nút "💥 Xóa toàn bộ file" (`/media/delete-all`).

### Quyết định 12: Kiến Trúc Full Mock Tách Kỹ Năng & Luồng Chuyển Ca Thi Tuần Tự
- **Bối cảnh**: Ban đầu đề thi Full Mock dồn toàn bộ câu hỏi và một bộ đếm giờ tổng duy nhất. Người học rất khó kiểm soát thời gian làm bài của từng kỹ năng, gây quá tải tâm lý và danh sách câu hỏi xem lại ở trang kết quả quá dài, khó đối soát.
- **Quyết định đã chốt**:
  1. **Tách riêng ca thi từng kỹ năng**:
     - TOPIK I: Nghe (30 câu, 40p) $\rightarrow$ Đọc (40 câu, 60p) $\rightarrow$ Hoàn thành.
     - TOPIK II: Nghe (50 câu, 60p) $\rightarrow$ Đọc (50 câu, 70p) $\rightarrow$ Viết (4 câu 51-54, 50p) $\rightarrow$ Hoàn thành.
  2. **Thêm các trường vào Entity `ExamAttempt`**:
     - `current_skill` (`Skill`, VARCHAR(20)): Kỹ năng hiện tại đang thi.
     - `listening_seconds`, `reading_seconds`, `writing_seconds` (`Integer`): Thời gian còn lại độc lập của từng kỹ năng.
  3. **Luồng chuyển ca thi tự động & chủ động**: Khi hết giờ của kỹ năng hiện tại hoặc khi học viên bấm nộp bài kỹ năng, hệ thống lưu câu trả lời vào `answers_json`, chuyển `current_skill` sang kỹ năng kế tiếp và đếm ngược số giây của kỹ năng đó. Khi kỹ năng cuối cùng kết thúc, bài thi mới chính thức được chấm điểm và kết thúc (`finish`).
  4. **Bảo toàn số thứ tự câu hỏi thực tế (`q.no`)**: Giao diện player hiển thị đúng số thứ tự trên đề thi (TOPIK I Đọc từ câu 31, TOPIK II Viết từ câu 51), không dùng biến đếm cục bộ làm reset số thứ tự về 1.
  5. **Bộ lọc tab kỹ năng trên trang kết quả (`.rv-skill-tabs`)**: Cung cấp các tab chuyển đổi Nghe / Đọc / Viết / Tất cả để lọc danh sách câu hỏi xem lại và chip số câu tương ứng. Điểm tổng và điểm từng kỹ năng vẫn được cộng gộp và hiển thị đầy đủ theo chuẩn TOPIK.

### Quyết định 13: Xử Lý Xung Đột Spring MVC `@RequestParam Map` Khi Bỏ Chọn Đáp Án Trắc Nghiệm
- **Bối cảnh**: Để người học có thể click vào đáp án đã chọn để bỏ chọn (deselect), ban đầu một thẻ `<input type="hidden" th:name="${q.key}" value="">` được đặt trước các thẻ radio nhằm gửi giá trị rỗng khi không có radio nào được chọn. Tuy nhiên, Spring MVC `RequestParamMapMethodArgumentResolver` khi bind `@RequestParam Map<String, String>` luôn lấy phần tử đầu tiên của mảng (`entry.getValue()[0]`), khiến các radio đã chọn giá trị `1..4` vẫn bị nhận thành `""`, dẫn đến toàn bộ bài làm bị xóa sạch khi lưu hoặc nộp bài.
- **Quyết định đã chốt**:
  1. Xóa bỏ hoàn toàn thẻ `<input type="hidden">` rỗng tĩnh trong `question-player.html`.
  2. Trong `question-player.js`, xây dựng cơ chế động: chỉ chèn `<input type="hidden" class="qp-empty-input" ... value="">` khi câu hỏi thực sự không có radio nào được chọn (sau khi người dùng deselect hoặc chưa chọn). Khi người dùng chọn lại một radio, input rỗng tạm này lập tức bị gỡ bỏ khỏi DOM.
  3. Tại server (`LearnerExamController.java` và `PracticeController.java`), xây dựng phương thức `extractAnswers(HttpServletRequest request)` duyệt mảng giá trị của từng key, ưu tiên nhặt giá trị không rỗng nếu client gửi nhiều giá trị cùng tên, bảo đảm tuyệt đối tính an toàn của dữ liệu bài làm.

### Quyết định 14: Biểu Đồ Tiến Độ Full Mock Dạng Cột Chồng Ghi Nhận Lần Làm Tốt Nhất (Best Attempt)
- **Bối cảnh**: Biểu đồ đường cũ biểu diễn dàn trải tất cả các lượt làm bài Full Mock theo thời gian, nếu học viên làm lại một đề nhiều lần thì biểu đồ bị dày đặc, rối mắt và không thể hiện được cơ cấu điểm giữa các kỹ năng. Người học cần một sơ đồ cột chồng gọn gàng, mỗi đề chỉ lấy điểm cao nhất và có thể bấm vào đề để xem lại bài làm đó.
- **Quyết định đã chốt**:
  1. **Chuyển sang biểu đồ Cột Chồng (Stacked Bar Chart)**: Chart.js với cấu hình `type: 'bar', stacked: true`.
  2. **Quy tắc Best Attempt**: Gom nhóm tất cả các lượt thi Full Mock đã hoàn thành theo `exam.id`. Với mỗi đề thi, chỉ lấy lần làm bài đạt tổng điểm cao nhất (`score.getTotalEarnedScore()`); nếu bằng điểm thì lấy lượt làm mới nhất theo ngày hoàn thành (`completedAt`).
  3. **Phân rã màu sắc theo kỹ năng**: Cột xếp chồng trực quan mức đóng góp của từng kỹ năng: 🎧 Nghe (`#1F6B56`), 📖 Đọc (`#2563EB`), ✍️ Viết (`#D97706`).
  4. **Chuyển đổi Track TOPIK I vs TOPIK II**: Trục Y tối đa 200 điểm cho TOPIK I và 300 điểm cho TOPIK II.
  5. **Tooltip phong phú & Tương tác click**: Hiển thị tên đề đầy đủ, ngày hoàn thành, số lần đã thử sức (`Số lần thử: X lần`), Điểm tổng (`x/200 đ` hoặc `x/300 đ`), Điểm chi tiết từng kỹ năng và gợi ý tương tác. Khi nhấp chuột vào cột, trình duyệt tự động chuyển hướng đến `/ket-qua/{attemptId}` của Best Attempt tương ứng.

### Quyết định 15: Đề Full Mock Một Kỹ Năng, Đánh Số Luyện Tập Từ 1 & Chuẩn Hóa Giao Diện Bài Viết / Topbar
- **Quyết định đã chốt**:
  1. **Đề Full Mock chỉ có 1 kỹ năng**: `LearnerExamService.availableSkills(exam)` kiểm tra kỹ năng thực sự có câu hỏi trong đề `FULL`. Nếu chỉ có 1 kỹ năng (chỉ Nghe, chỉ Đọc, hoặc chỉ Viết), hệ thống mở thẳng ca thi đó và kết thúc khi nộp bài (`nextSkill` trả về `null`), không ép đi qua các ca trống.
  2. **Số câu Luyện tập bắt đầu từ 1**: `PracticeService.questions()` và `PracticeService.result()` gán số thứ tự `no = 1..N`, đồng thời giữ `originalNo` trên `QuestionView` để hiển thị `"Câu gốc #..."` tại `practice/result.html` và gửi báo lỗi câu hỏi chính xác.
  3. **Giao diện "Bài viết gửi Giáo viên" (`/writing/feedback`) đồng bộ & phân trang chuẩn**:
     - Thống nhất layout 2 cột (`writing-feedback-grid`) cho cả bài chờ chấm, đang chấm, đã chấm, khiếu nại và bị từ chối.
     - Đặt banner **Từ chối chấm** (chỉ dẫn dùng nút `"Làm lại câu này"` ở tiêu đề, bỏ nút viết lại trùng lặp) và khối **Lịch sử khiếu nại điểm số** ở **trên cùng** (trên grid 2 cột), hiển thị đầy đủ 3 mốc thời gian (`submittedAt`, `appealedAt`, `appealResolvedAt`) và làm nổi bật **Lời nhắn từ Giáo viên chấm lại**.
     - Link sidebar truyền kèm `page=${currentPage}` và controller `WritingController.feedback` tự động xác định `resolvedPage` từ `id` khi không có `page`, đồng thời chọn bài đầu trang hiện tại khi chuyển trang.
  4. **Chuẩn hóa Topbar Navbar & Trợ giúp (`navbar.html`, `style.css`)**: Bỏ huy hiệu `"Đã học ... ngày"` khỏi thanh topbar, chuẩn hóa chiều cao `36px` cho `ssak-stat-pill` (Xu, VIP), `ssak-icon-btn` và `ssak-avatar-badge`; tích hợp menu Trợ giúp `(?)` với Tour 11 trạm (`walkthrough-tour.js`) và `help-modal.html`.

---

## 2. Các Lỗi Kỹ Thuật Đã Xử Lý & Bài Học Kinh Nghiệm (Lessons Learned)

### 2.1. Lỗi Cú Pháp Thymeleaf Parsing (`TemplateInputException`)
- **Triệu chứng**:
  ```
  org.thymeleaf.exceptions.TemplateInputException: An error happened during template parsing 
  (template: "class path resource [templates/content/exam/source-detail.html]")
  Caused by: org.attoparser.ParseException: Exception evaluating SpringEL expression
  ```
- **Nguyên nhân gốc**: Lồng cú pháp đường dẫn `@{} ` bên trong biểu thức điều kiện `${}`:
  ```html
  <!-- SAI -->
  <audio th:src="${(editCard != null and editCard.audio != '') ? @{/content/media/{s}/{f}(s=${source.id}, f=${editCard.audio})} : null}"></audio>
  ```
  Bộ phân tích SpringEL không thể phân tích thẻ link Spring bên trong biểu thức logic phức tạp.
- **Cách khắc phục chuẩn**:
  ```html
  <!-- ĐÚNG -->
  <audio th:src="${editCard != null and editCard.audio != null and editCard.audio != ''} ? @{/content/media/{s}/{f}(s=${source.id}, f=${editCard.audio})} : null"></audio>
  ```

### 2.2. Lỗi PowerShell Phân Tích Ký Tự Inline Node Scripts
- **Triệu chứng**: Chạy `node -e "..."` trên PowerShell báo lỗi: `Unexpected token in expression or statement`, `Missing closing '}'`.
- **Nguyên nhân gốc**: PowerShell cố gắng phân tích các biến dạng `${source.id}`, các khối lệnh `{ ... }` và ký tự xuống dòng bên trong chuỗi string trước khi truyền cho Node.js.
- **Quy tắc bắt buộc**: Tuyệt đối không dùng `node -e "..."` nhiều dòng trên PowerShell. Luôn sử dụng tool `write_file` để tạo file kịch bản `.js` trong thư mục scratch (`<appDataDir>/scratch/*.js`) rồi thực thi bằng lệnh `node "đường_dẫn_file.js"`.

### 2.3. Lỗi Trùng Khai Báo Biến trong `ParsedWorkbook.java`
- **Triệu chứng**: Lỗi biên dịch `variable optImgs is already defined in class QuestionRow`.
- **Nguyên nhân gốc**: Khi thực hiện chèn trường `public String[] optImgs = new String[4];`, vị trí chèn bị nhầm vào class `QuestionRow` (bị lặp 2 lần) thay vì class `ManualAsk`.
- **Cách khắc phục**: Loại bỏ hoàn toàn khai báo thừa trong `QuestionRow` và đặt đúng vào `ManualAsk`.

### 2.4. Lưu Ý Về Các Lỗi Biên Dịch Pre-existing Trong `UserService.java`
- **Hiện tượng**: Khi chạy `mvnw compile`, có thể xuất hiện các thông báo lỗi biên dịch liên quan đến `UserService.java` (ví dụ: `cannot find symbol method getLockedUntil()`, `getEmail()`).
- **Nguyên nhân**: Đây là lỗi mã nguồn có sẵn từ các nhánh merge trước đó (`origin/QuyND`) do có sự không đồng bộ giữa `User.java` và `UserService.java`.
- **Lưu ý cho AI Agent**:
  1. Không tự ý chỉnh sửa `UserService.java` nếu yêu cầu của người dùng không liên quan đến chức năng Quản lý Người dùng của Admin.
  2. Tập trung hoàn thành chính xác phạm vi yêu cầu được giao và bảo vệ tính toàn vẹn của các module liên quan trực tiếp.

### 2.5. Lỗi `! Mỗi câu cần một ô số trên lưới.` khi Lưu Câu Nhập Đề Thủ Công
- **Triệu chứng**: Khi quản lý nội dung nhập một câu hỏi và bấm nút "Lưu câu", hệ thống từ chối lưu và hiển thị thông báo lỗi `! Mỗi câu cần một ô số trên lưới.`.
- **Nguyên nhân gốc**:
  1. Form nhập trong `source-detail.html` tồn tại các thẻ `<input type="hidden" name="modelAnswer">` và `<input type="hidden" name="rubric">` bị trùng lặp, khiến request gửi lên server trả về danh sách giá trị có size = 2 thay vì 1.
  2. Hàm `askCount(form)` ban đầu lấy giá trị lớn nhất từ độ dài các danh sách đầu vào (`Math.max(...)`), dẫn đến xác định số câu `count = 2`, trong khi người dùng chỉ bấm chọn 1 ô số trên lưới (`numbers.size() = 1`). Biểu thức `numbers.size() != count` (1 ≠ 2) ném ra ngoại lệ `ExamDomainException("Mỗi câu cần một ô số trên lưới.")`.
- **Cách khắc phục**:
  - Dọn sạch các thẻ input ẩn bị trùng lặp trong `source-detail.html`.
  - Cập nhật hàm `askCount(form)` trong `ExamImportService.java` để ưu tiên lấy chính xác số lượng ô số thực tế `form.qno()` được người dùng chọn trên lưới.

### 2.6. Lỗi Ảnh Đề Bài Không Hiển Thị Trong Giao Diện "Xem Như Học Viên" (Preview)
- **Triệu chứng**: Người dùng chọn file ảnh đề bài và lưu thành công, nhưng khi vào màn hình xem trước (`/content/sources/{id}/preview`) thì không thấy ảnh hiển thị.
- **Nguyên nhân gốc**:
  1. Trong `ExamImportService.baisFromCards`, code cũ chỉ gán ảnh cho câu hỏi nếu kỹ năng là Viết (`if (writing && ...)`), bỏ qua kỹ năng Nghe và Đọc.
  2. Record `LearnerAsk` thiếu trường lưu trữ thông tin ảnh đề bài riêng của từng câu hỏi.
  3. `QuestionPlayerItem.preview` chỉ đọc ảnh từ cấp độ bài (`bai.image()`) mà không kiểm tra ảnh của từng câu hỏi (`ask.image()`).
- **Cách khắc phục**:
  - Bổ sung trường `image` vào record `LearnerAsk` trong `ExamImportService.java`.
  - Cập nhật `baisFromCards` để đọc ảnh từ `ask.chart` cho toàn bộ các kỹ năng (Nghe, Đọc, Viết).
  - Cập nhật `QuestionPlayerItem.preview` ưu tiên lấy `ask.image()`, nếu không có mới fallback về `bai.image()`.

### 2.7. Lỗi Tương Thích Ngược Constructor Record `CardForm` Khi Mở Rộng Dữ Liệu
- **Triệu chứng**: Khi thêm các trường lưu file ảnh 4 phương án (`opt1Img`..`opt4Img`) vào record `CardForm`, hàng loạt unit test và regression test cũ bị lỗi biên dịch do khác số lượng tham số constructor.
- **Nguyên nhân gốc**: Record trong Java tự động tạo canonical constructor với đầy đủ các trường mới khai báo.
- **Cách khắc phục**: Khai báo thêm một overloaded constructor trong `CardForm` nhận danh sách 21 tham số cũ và ủy quyền sang canonical constructor với giá trị null cho 4 trường ảnh phương án, giúp toàn bộ kiểm thử cũ và mới cùng hoạt động trơn tru.

### 2.8. Lỗi Xung Đột Nhận Diện Sheet Giữa Format Cũ và Format Mới trong `ExamWorkbookParser.java`
- **Triệu chứng**: Khi chạy test import file Excel mẫu cũ của TOPIK II (`SampleExamFactory.full(Track.TOPIK_II, 1)`), bài thi chỉ nhận phần Viết mà mất toàn bộ câu hỏi trắc nghiệm Nghe/Đọc.
- **Nguyên nhân gốc**: Code kiểm tra `hasSkillSheets = (ngheSheet != null || docSheet != null || vietSheet != null)`. File cũ có sheet tên `WRITING` trùng với bí danh của kỹ năng Viết (`vietSheet != null`), khiến cờ `hasSkillSheets` thành `true` và bỏ qua logic parse sheet `QUESTIONS` cũ.
- **Cách khắc phục**: Điều chỉnh điều kiện nhận diện: `boolean hasSkillSheets = (ngheSheet != null || docSheet != null || (vietSheet != null && qs == null));`. Nếu file có sheet `QUESTIONS` (`qs != null`), hệ thống ưu tiên parse theo format cũ, chỉ kích hoạt parse theo kỹ năng khi có sheet `NGHE`/`DOC` hoặc sheet `VIET` mà không có sheet `QUESTIONS`.

### 2.9. Lỗi Trùng Khai Báo Phương Thức Helper Trong `ExamTemplateBuilder.java`
- **Triệu chứng**: Lỗi biên dịch `class, interface, enum, or record expected` do phương thức tiện ích `writeRow` bị paste lặp hai lần và thừa dấu đóng ngoặc nhọn `}` ở cuối file.
- **Nguyên nhân gốc**: Khi bổ sung các phương thức tạo template Excel kỹ năng riêng biệt (`buildSkillTemplate`) và template gộp đa sheet (`buildFullTemplate`), khối helper method ở đáy file bị duplicate.
- **Cách khắc phục**: Rà soát cấu trúc đóng mở ngoặc nhọn class, hợp nhất một hàm `writeRow(Row r, Object... vals)` duy nhất, kiểm tra chặt chẽ với lệnh `mvnw test-compile`.

### 2.10. Lỗi `Illegal char <?> at index ...` & Audio Không Phát Được Trên Trình Duyệt
- **Triệu chứng**: Khi upload file có tên tiếng Hàn (như `01 트랙 1.mp3`), hệ thống ném ngoại lệ `InvalidPathException: Illegal char <?> at index 20: sources/18/media/01 ?? 1.mp3`. Khi phát audio trên trình duyệt thì audio không chạy được.
- **Nguyên nhân gốc**:
  1. Ký tự tiếng Hàn bị biến thành `??` do collation hoặc lưu chuỗi, trên Windows ký tự `?` là ký tự cấm đối với đường dẫn file (`Paths.get()` ném `InvalidPathException`).
  2. Trình duyệt không phát được audio do response header bị Tomcat tự gắn thêm `;charset=UTF-8` (`Content-Type: audio/mpeg;charset=UTF-8`) khi bật `X-Content-Type-Options: nosniff`.
- **Cách khắc phục**:
  1. Đánh dấu `@Nationalized` trên trường `filename` của `MediaAsset.java` để SQL Server lưu trữ NVARCHAR.
  2. Sanitize ký tự cấm bằng `replaceAll("[?*:\"<>|]", "_")` trong `MediaStorageService.java`, bọc try-catch ngoại lệ trong `resolveStored` và bổ sung `deleteStoredSafe`.
  3. Cập nhật `MediaResponses.java` ghi đè chuẩn xác `response.setHeader(HttpHeaders.CONTENT_TYPE, type)` ngăn chặn Tomcat thêm charset; bổ sung thẻ `<source type="audio/mpeg">`, `preload="metadata"` và `.load()` cho audio player.

### 2.11. Lỗi Biên Dịch `unreported exception java.io.IOException; must be caught or declared to be thrown`
- **Triệu chứng**: Biên dịch Maven thất bại tại `MediaStorageService.java` dòng 98 và 107.
- **Nguyên nhân gốc**: Phương thức `resolveMediaUnchecked` và `deleteMediaFolder` gọi `sourceDir(sourceId)`, trong khi `sourceDir` ném `IOException` mà không được khai báo `throws` hay bọc `try-catch`.
- **Cách khắc phục**: Resolve trực tiếp đường dẫn từ thư mục gốc `root.resolve("sources").resolve(String.valueOf(sourceId)).resolve("media")` mà không gọi `sourceDir` (tránh gọi `Files.createDirectories` không cần thiết và không làm phát sinh checked exception).

### 2.12. Lỗi `The conversion from varchar to NCHAR is unsupported` & `UnexpectedRollbackException` Lúc Khởi Động
- **Triệu chứng**:
  1. Khi khởi động ứng dụng kết nối SQL Server, Spring Boot crash với `UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only` tại `SamplePaperSeeder.run`.
  2. Khi truy cập trang chi tiết nguồn đề, ném ngoại lệ `The conversion from varchar to NCHAR is unsupported` tại cột `storage_path` của `MediaAsset`.
- **Nguyên nhân gốc**:
  1. Cột `storage_path` trong database SQL Server đang là kiểu `VARCHAR(500)`. Khi đánh dấu `@Nationalized` lên `storagePath` trong Java entity, Hibernate JDBC driver sử dụng `getNString()` để đọc cột `VARCHAR`, bị SQL Server driver từ chối chuyển đổi kiểu.
  2. Trong `SamplePaperSeeder.run`, một vòng lặp quét tự động sửa `MediaAsset` được đặt trong phương thức mang annotation `@Transactional`. Khi entity JPA cập nhật bị lỗi, Spring đánh dấu transaction rollback-only, sau đó khi method kết thúc và commit thì ném `UnexpectedRollbackException`. Hơn nữa `SamplePaperSeeder` bị mất annotation `@Profile("local")`, khiến nó chạy ngoài ý muốn trên SQL Server.
- **Cách khắc phục**:
  1. Bỏ `@Nationalized` trên trường `storagePath` của `MediaAsset.java` vì đường dẫn hệ thống tệp chỉ dùng ký tự ASCII thuần, giữ lại `@Nationalized` trên trường `filename`.
  2. Khôi phục `@Profile("local")` cho `SamplePaperSeeder.java` và gỡ bỏ vòng lặp cập nhật DB thừa trong seeder, đảm bảo seeder chỉ chạy trên môi trường H2 local profile.

### 2.13. Lỗi Biên Dịch `cannot find symbol method getTotalScore()` / `getWritingScore()` Trong `ProgressService.java`
- **Triệu chứng**:
  ```
  ProgressService.java: cannot find symbol method getTotalScore()
  ProgressService.java: cannot find symbol method getWritingScore()
  ```
- **Nguyên nhân gốc**: Trong DTO `ExamScoreDTO`, getter của tổng điểm thực tế là `getTotalEarnedScore()` (không phải `getTotalScore()`), và điểm tổng kỹ năng Viết là `getWritingTotalScore()` (không phải `getWritingScore()`).
- **Cách khắc phục**: Sửa các lời gọi trong `ProgressService.java` thành `bestScore.getTotalEarnedScore()` và `bestScore.getWritingTotalScore() != null ? bestScore.getWritingTotalScore() : 0.0`. Chạy lại `mvnw test-compile -DskipTests` xác nhận BUILD SUCCESS.

### 2.14. Chuỗi Tiếng Hàn / Ký Tự Dài Không Dấu Cách Kéo Giãn Ngang Khung Chấm & Xem Bài Viết
- **Triệu chứng**: Khi học viên nhập chuỗi ký tự dài liên tục không có khoảng trắng (ví dụ `hhhh...` hoặc tiếng Hàn liền mạch), khung bài làm ở `/writing/history`, `/writing/feedback` và bàn chấm `/teacher` bị kéo giãn ngang vượt khung nhìn, ép hẹp các cột xung quanh.
- **Nguyên nhân gốc**: Các container ngoài cùng bị ép `min-width: 1280px`, grid dùng `fr` thuần không có `minmax(0, ...)`, flex item thiếu `min-width: 0; overflow: hidden;` và vùng văn bản chỉ đặt `white-space: pre-wrap/pre-line` mà thiếu `word-break: break-word; overflow-wrap: anywhere;`.
- **Cách khắc phục**: Bỏ `min-width: 1280px`, đặt `minmax(0, ...)` cho các cột CSS Grid, thêm `min-width: 0; overflow: hidden;` trên wrapper/main/flex container và áp dụng `word-break: break-word; overflow-wrap: anywhere; white-space: pre-wrap;` cho toàn bộ khung đề bài, bài làm, nhận xét và khiếu nại trong `writing/feedback.html`, `writing/history.html`, `writing/grading-history.html`, `writing/index.html` và `teacher/dashboard.html`.

### 2.15. Mất Thời Gian Khiếu Nại & Không Cập Nhật `gradedAt` Khi Chọn Giữ Nguyên Điểm (`uphold`)
- **Triệu chứng**: Khi học viên nhận kết quả khiếu nại, giao diện thiếu thời gian gửi khiếu nại và thời gian Giáo viên trả kết quả khiếu nại (đặc biệt khi Giáo viên chọn giữ nguyên kết quả `uphold`, `gradedAt` vẫn giữ nguyên ngày chấm cũ vì `s.setGradedAt(...)` chỉ nằm trong nhánh `else` của `revise`).
- **Cách khắc phục**: Bổ sung `appealedAt` và `appealResolvedAt` vào `WritingSubmissionDTO.java`; gán `appealedAt` trong `appealSubmission` và cập nhật cả `appealResolvedAt` lẫn `gradedAt` trong khối `if (appeal)` của `WritingSubmissionService.gradeSubmission`, đồng thời hiển thị đầy đủ 3 mốc thời gian (kèm fallback cho dữ liệu cũ) trên `feedback.html` và `grading-history.html`.

### 2.16. Lưu Nháp Bàn Chấm Giáo Viên (`saveGradingDraft`) & Cơ Chế Cảnh Báo Tránh Mất Dữ Liệu Khi Rời Bàn Chấm
- **Triệu chứng**: Khi Giáo viên đang chấm dở một bài viết (đã nhập một phần điểm tiêu chí hoặc nhận xét dài) mà vô tình bấm quay lại danh sách, đóng tab hoặc tải lại trang, dữ liệu chưa nộp bị mất sạch.
- **Nguyên nhân gốc**: Form bàn chấm `/teacher` trước đây chỉ có một nút nộp kết quả cuối cùng (`POST /teacher/grade`), thiếu endpoint lưu nháp dở dang cho Giáo viên và thiếu cơ chế phát hiện thay đổi trên form (`isGradingDirty`).
- **Cách khắc phục**:
  1. Thêm phương thức `WritingSubmissionService.saveGradingDraft` và endpoint `POST /teacher/save-draft` trong `TeacherPortalController.java`. Phương thức cập nhật điểm, feedback vào payload nhưng giữ nguyên trạng thái bài là `claimed` hoặc `appeal` và không bắn thông báo cho học viên.
  2. Trong `teacher/dashboard.html`, thêm nút "💾 Lưu và thoát" ở cả toolbar và panel dưới, tự động kích hoạt `form.noValidate = true` khi lưu nháp để không bị chặn bởi validation HTML5 khi chấm dở.
  3. Thêm trình bắt sự kiện `beforeunload` khi `isGradingDirty = true`, và chặn các click `a[href]` nội bộ để hiển thị modal cảnh báo `#unsavedGradingModal` (với 3 lựa chọn: *Thoát không lưu*, *Tiếp tục chấm*, *Lưu và thoát*).
  4. Bổ sung bài kiểm thử `WritingSubmissionDraftUnitTest.testSaveGradingDraftKeepsClaimedStatusAndPreservesPartialWork` đảm bảo 100% độ phủ nghiệp vụ.

### 2.17. Lỗi Tràn Cột Lưới Gốc Hán & Tách Danh Sách / Luyện Tập Riêng Cho Từ Đã Học Và Từ Hay Sai (Cầu Nối Hán Việt)
- **Triệu chứng**:
  1. Danh sách gốc Hán bên trái tại `/ly-thuyet/han-viet` bị cắt đôi ở cột thứ 4 và bị khung chi tiết bên phải đè lên.
  2. Người học không có trang danh sách riêng để xem lại các từ đã học, và không có chỗ lưu lại các từ làm sai khi chơi "Đoán nghĩa từ mới" để ôn luyện chuyên biệt.
- **Nguyên nhân gốc**:
  1. Layout lưới cũ ép cột trái `280px` với `repeat(4, 1fr)`. Do min-content của thẻ gốc Hán rộng hơn 64px nên cột 4 bị tràn qua lề phải và chìm dưới khung chi tiết (`rootDetailPanel`).
  2. Chế độ học trước đây chỉ lưu mã gốc Hán vào `localStorage.hanjaLearned` mà không có cơ chế quản lý từ vựng đã học hay từ vựng làm sai (`mistakes`).
- **Cách khắc phục**:
  1. Tăng chiều rộng cột trái lên `340px minmax(0, 1fr)`, đặt `repeat(4, minmax(0, 1fr))` trên `#hanjaGrid` kèm cuộn nội bộ `max-height: 640px; overflow-y: auto`, ghim sticky cho `#rootDetailPanel`.
  2. Chia 4 sub-tab: `📖 Học theo gốc`, `🎯 Đoán nghĩa từ mới`, `✅ Từ đã học`, `⚠️ Cần luyện tập` kèm huy hiệu đếm số lượng thời gian thực.
  3. Hỗ trợ đánh dấu đã học từng từ riêng lẻ qua nút `+` / `✓` trong bảng chi tiết hoặc đánh dấu cả gốc.
  4. Tự động gom từ làm sai trong "Đoán nghĩa từ mới" vào danh sách `⚠️ Cần luyện tập` (`hanjaMistakes`).
  5. Cung cấp chức năng tạo bài quiz trắc nghiệm 4 đáp án riêng biệt cho cả "Từ đã học" và "Từ cần nhớ".
  6. Hiện thực 2 cơ chế tự động xóa từ khỏi danh sách cần luyện: (1) khi học viên đánh dấu đã học từ đó, hoặc (2) khi học viên làm bài luyện tập từ cần nhớ và chọn đáp án chính xác.
