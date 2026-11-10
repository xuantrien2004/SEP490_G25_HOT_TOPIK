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
- **Bối cảnh**: Khi 2-3 câu hỏi cùng sử dụng chung 1 đoạn văn hoặc file audio, nếu mỗi câu đều hiển thị đầy đủ bộ nút điều hướng và lặp lại đoạn văn sẽ làm giao diện rối mắt, người học phải cuộn trang liên tục. Ngược lại, nếu câu đơn bình thường cũng bị kích hoạt chế độ nhóm ghim sticky sẽ làm giao diện bị thừa thãi và mất đi cấu trúc chuẩn.
- **Quyết định đã chốt**:
  1. **Phân biệt rạch ròi theo số câu thực tế (`matchingCards.length`)**:
     - **Câu hỏi nhóm (từ 2 câu trở lên - `matchingCards.length >= 2`)**: Kích hoạt chế độ `qp-group-mode`. Khung cố định `.qp-group-sticky` xuất hiện ghim cố định ở đỉnh màn hình chứa audio, đoạn văn và hình ảnh dùng chung khi người học cuộn xuống làm câu 2 hoặc câu 3. Chỉ hiển thị duy nhất 1 nút "Câu trước" và 1 nút "Câu tiếp" ở cuối nhóm (ở câu dưới cùng trong nhóm); nút "Câu trước" nhảy về câu đầu của nhóm ngay trước đó.
     - **Câu hỏi đơn (1 câu/bài - `matchingCards.length < 2`)**: Tuyệt đối **KHÔNG ghim sticky** (`qp-group-mode = false`). Toàn bộ ngữ liệu (audio, đoạn văn, ảnh) hiển thị trực tiếp bên trong thẻ của chính câu hỏi đó. Nút "Câu trước" / "Câu tiếp" di chuyển tuần tự từng câu một (+/- 1).
  2. Nút **"Chưa chắc"** và **"Báo lỗi câu hỏi"** bắt buộc **giữ nguyên ở từng câu riêng biệt** trong cả hai chế độ.
  3. **Đánh dấu Phiếu câu hỏi**: Sử dụng `IntersectionObserver` trong `question-player.js` để phát hiện câu nào đang nằm trong tầm mắt người học và cập nhật highlight chính xác số câu đó trên thanh điều hướng bên phải.

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
