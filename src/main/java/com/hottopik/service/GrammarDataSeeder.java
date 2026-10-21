package com.hottopik.service;

import com.hottopik.entity.Grammar;
import com.hottopik.entity.GrammarExample;
import com.hottopik.entity.GrammarGroup;
import com.hottopik.repository.GrammarExampleRepository;
import com.hottopik.repository.GrammarGroupRepository;
import com.hottopik.repository.GrammarRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * GrammarDataSeeder:
 * 1. Tự động chuẩn hóa và sửa lỗi font tiếng Việt cho bảng grammar_groups (loại bỏ dấu ? do lỗi encoding trong SQL).
 * 2. Khởi tạo sẵn đầy đủ các Dạng/Nhóm ngữ pháp chuẩn từ TOPIK 1 đến TOPIK 6.
 * 3. Khởi tạo sẵn các mẫu ngữ pháp kèm câu ví dụ cụ thể cho từng nhóm để Content Manager và Học viên có sẵn dữ liệu chuẩn để tra cứu, quản lý và demo.
 */
@Component
@Order(25)
public class GrammarDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GrammarDataSeeder.class);

    private final GrammarGroupRepository groupRepo;
    private final GrammarRepository grammarRepo;
    private final GrammarExampleRepository exampleRepo;

    public GrammarDataSeeder(GrammarGroupRepository groupRepo,
                             GrammarRepository grammarRepo,
                             GrammarExampleRepository exampleRepo) {
        this.groupRepo = groupRepo;
        this.grammarRepo = grammarRepo;
        this.exampleRepo = exampleRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("[GrammarDataSeeder] Đang kiểm tra và chuẩn hóa dữ liệu Dạng/Nhóm Ngữ pháp TOPIK 1-6...");
        seedAllGroups();
        seedSampleGrammars();
        log.info("[GrammarDataSeeder] Đã hoàn tất đồng bộ và chuẩn hóa dữ liệu Ngữ pháp TOPIK 1-6.");
    }

    private void seedAllGroups() {
        // Định nghĩa danh sách nhóm ngữ pháp chuẩn có dấu tiếng Việt cho TOPIK 1 -> 6
        List<GroupDef> defs = new ArrayList<>();

        // TOPIK 1
        defs.add(new GroupDef((short) 1, (short) 1, "Trợ từ"));
        defs.add(new GroupDef((short) 1, (short) 2, "Đuôi câu, kính ngữ"));
        defs.add(new GroupDef((short) 1, (short) 3, "Thì, thể"));
        defs.add(new GroupDef((short) 1, (short) 4, "Phủ định"));
        defs.add(new GroupDef((short) 1, (short) 5, "Biến đổi từ, liên từ"));
        defs.add(new GroupDef((short) 1, (short) 6, "Nguyên nhân, lý do"));
        defs.add(new GroupDef((short) 1, (short) 7, "Mục đích, ý định"));
        defs.add(new GroupDef((short) 1, (short) 8, "Điều kiện, giả định"));
        defs.add(new GroupDef((short) 1, (short) 9, "Đối lập, nhượng bộ"));
        defs.add(new GroupDef((short) 1, (short) 10, "Thời gian, trình tự"));
        defs.add(new GroupDef((short) 1, (short) 11, "Khả năng, mong muốn"));
        defs.add(new GroupDef((short) 1, (short) 12, "Đề nghị, mệnh lệnh"));
        defs.add(new GroupDef((short) 1, (short) 13, "So sánh, lựa chọn"));

        // TOPIK 2
        defs.add(new GroupDef((short) 2, (short) 1, "Trợ từ nâng cao"));
        defs.add(new GroupDef((short) 2, (short) 2, "Đuôi câu cảm thán, gián tiếp"));
        defs.add(new GroupDef((short) 2, (short) 3, "Trạng thái, tiếp diễn"));
        defs.add(new GroupDef((short) 2, (short) 4, "Nguyên nhân, kết quả"));
        defs.add(new GroupDef((short) 2, (short) 5, "Mục đích, định hướng"));
        defs.add(new GroupDef((short) 2, (short) 6, "Điều kiện, giả định nâng cao"));
        defs.add(new GroupDef((short) 2, (short) 7, "Đối lập, nhượng bộ nâng cao"));
        defs.add(new GroupDef((short) 2, (short) 8, "Thời gian, trình tự"));
        defs.add(new GroupDef((short) 2, (short) 9, "Phỏng đoán, ước lượng"));
        defs.add(new GroupDef((short) 2, (short) 10, "Gián tiếp, trích dẫn"));

        // TOPIK 3
        defs.add(new GroupDef((short) 3, (short) 1, "Nhượng bộ, tương phản"));
        defs.add(new GroupDef((short) 3, (short) 2, "Nguyên nhân gián tiếp"));
        defs.add(new GroupDef((short) 3, (short) 3, "Dự đoán, khả năng"));
        defs.add(new GroupDef((short) 3, (short) 4, "Mức độ, so sánh"));
        defs.add(new GroupDef((short) 3, (short) 5, "Dự định, ý chí"));
        defs.add(new GroupDef((short) 3, (short) 6, "Trích dẫn rút gọn"));
        defs.add(new GroupDef((short) 3, (short) 7, "Giả định ngược, tiếc nuối"));
        defs.add(new GroupDef((short) 3, (short) 8, "Trạng thái kéo dài"));

        // TOPIK 4
        defs.add(new GroupDef((short) 4, (short) 1, "Nhấn mạnh & Hạn định"));
        defs.add(new GroupDef((short) 4, (short) 2, "Nhân quả phức hợp"));
        defs.add(new GroupDef((short) 4, (short) 3, "Điều kiện tương hỗ"));
        defs.add(new GroupDef((short) 4, (short) 4, "Thay thế & Chuyển đổi"));
        defs.add(new GroupDef((short) 4, (short) 5, "Thừa nhận & Đối chiếu"));
        defs.add(new GroupDef((short) 4, (short) 6, "Thời điểm bất ngờ"));
        defs.add(new GroupDef((short) 4, (short) 7, "Cảm xúc, tiếc nuối"));

        // TOPIK 5
        defs.add(new GroupDef((short) 5, (short) 1, "Văn viết học thuật, lập luận"));
        defs.add(new GroupDef((short) 5, (short) 2, "Phủ định kép & Khẳng định mạnh"));
        defs.add(new GroupDef((short) 5, (short) 3, "Giả thuyết tiêu cực"));
        defs.add(new GroupDef((short) 5, (short) 4, "Liên kết trang trọng"));
        defs.add(new GroupDef((short) 5, (short) 5, "Quy chiếu & Căn cứ"));
        defs.add(new GroupDef((short) 5, (short) 6, "Nhượng bộ cùng cực"));

        // TOPIK 6
        defs.add(new GroupDef((short) 6, (short) 1, "Cấu trúc báo chí, nghị luận"));
        defs.add(new GroupDef((short) 6, (short) 2, "Giả định cực đoan & Tuyệt đối"));
        defs.add(new GroupDef((short) 6, (short) 3, "Quy luật, châm ngôn"));
        defs.add(new GroupDef((short) 6, (short) 4, "Thành ngữ ngữ pháp nâng cao"));
        defs.add(new GroupDef((short) 6, (short) 5, "Kết luận & Tổng kết học thuật"));

        for (GroupDef def : defs) {
            Optional<GrammarGroup> opt = groupRepo.findByLevelAndGroupNo(def.level, def.groupNo);
            if (opt.isPresent()) {
                GrammarGroup g = opt.get();
                // Nếu tên bị lỗi font (chứa dấu ? hoặc sai khác tên chuẩn) thì cập nhật lại tiếng Việt có dấu chuẩn
                if (g.getNameVi() == null || g.getNameVi().contains("?") || !g.getNameVi().equals(def.nameVi)) {
                    g.setNameVi(def.nameVi);
                                        groupRepo.save(g);
                }
            } else {
                GrammarGroup g = new GrammarGroup();
                g.setLevel(def.level);
                g.setGroupNo(def.groupNo);
                g.setNameVi(def.nameVi);
                                                groupRepo.save(g);
            }
        }
    }

    private void seedSampleGrammars() {
        // Đảm bảo mỗi nhóm đều có ít nhất một số ngữ pháp mẫu chuẩn
        addGrammarIfNotExists((short) 1, (short) 1, "-은/는", "Trợ từ chủ đề", "Gắn sau danh từ để biểu thị chủ đề của câu hoặc sự nhấn mạnh/đối chiếu.", "N + 은/는", "-저는 학생입니다.\n- 사과는 맛있습니다.", "Tôi là học sinh. / Táo thì ngon.");
        addGrammarIfNotExists((short) 1, (short) 1, "-이/가", "Trợ từ chủ ngữ", "Gắn sau danh từ biểu thị chủ ngữ thực hiện hành động hoặc trạng thái.", "N + 이/가", "-비가 옵니다.\n- 동생이 예쁩니다.", "Trời mưa. / Em gái xinh đẹp.");
        addGrammarIfNotExists((short) 1, (short) 1, "-을/를", "Trợ từ tân ngữ", "Gắn sau danh từ làm tân ngữ trực tiếp chịu tác động của hành động.", "N + 을/를", "-밥을 먹습니다.\n- 한국어를 공부합니다.", "Ăn cơm. / Học tiếng Hàn.");
        addGrammarIfNotExists((short) 1, (short) 2, "-ㅂ니다/습니다", "Đuôi câu kính trọng", "Đuôi kết thúc câu trần thuật trang trọng, dùng trong giao tiếp lịch sự hoặc hội nghị.", "V/A + ㅂ니다/습니다", "-만나서 반갑습니다.", "Rất vui được gặp bạn.");
        addGrammarIfNotExists((short) 1, (short) 2, "-아/어요", "Đuôi câu thân mật lịch sự", "Đuôi câu giao tiếp phổ biến hàng ngày, vừa thân thiện vừa tôn trọng.", "V/A + 아/어요", "-지금 어디에 가요?", "Bây giờ bạn đi đâu thế?");
        addGrammarIfNotExists((short) 1, (short) 3, "-았/었-", "Thì quá khứ", "Biểu thị hành động hoặc trạng thái đã xảy ra trong quá khứ.", "V/A + 았/었어요", "-어제 친구를 만났어요.", "Hôm qua tôi đã gặp bạn bè.");
        addGrammarIfNotExists((short) 1, (short) 6, "-아서/어서", "Vì... nên...", "Biểu thị nguyên nhân - kết quả tự nhiên giữa hai vế câu.", "V/A + 아서/어서", "-배가 아파서 병원에 갔어요.", "Vì đau bụng nên tôi đã đi bệnh viện.");
        addGrammarIfNotExists((short) 1, (short) 7, "-(으)러 가다", "Đi để làm gì", "Biểu thị mục đích của việc đi lại.", "V + (으)러 가다/오다", "-점심을 먹으러 식당에 가요.", "Tôi đến nhà hàng để ăn trưa.");
        addGrammarIfNotExists((short) 1, (short) 8, "-(으)면", "Nếu... thì...", "Biểu thị giả định hoặc điều kiện xảy ra hành động.", "V/A + (으)면", "-시간이 있으면 영화를 볼까요?", "Nếu có thời gian thì chúng ta xem phim nhé?");

        // TOPIK 2
        addGrammarIfNotExists((short) 2, (short) 4, "-느라고", "Vì mải... nên...", "Biểu thị nguyên nhân dẫn đến kết quả tiêu cực hoặc hao tổn thời gian.", "V + 느라고", "-시험공부를 하느라고 잠을 못 잤어요.", "Vì mải học thi nên tôi không ngủ được.");
        addGrammarIfNotExists((short) 2, (short) 4, "-는 바람에", "Do đột nhiên... nên...", "Biểu thị lý do bất ngờ, khách quan dẫn đến kết quả không mong muốn.", "V + 는 바람에", "-갑자기 비가 오는 바람에 옷이 다 젖었어요.", "Do trời đột ngột mưa nên áo quần ướt sạch.");
        addGrammarIfNotExists((short) 2, (short) 8, "-자마자", "Ngay sau khi...", "Hành động vế sau diễn ra ngay tức thì sau hành động vế trước.", "V + 자마자", "-수업이 끝나자마자 집으로 갔어요.", "Ngay sau khi tan học tôi đã về nhà.");
        addGrammarIfNotExists((short) 2, (short) 9, "-(으)ㄹ 것 같다", "Dường như, có vẻ như", "Phỏng đoán dựa trên quan sát hoặc cảm nhận cá nhân.", "V/A + (으)ㄹ 것 같다", "-내일은 날씨가 좋을 것 같아요.", "Ngày mai dường như thời tiết sẽ đẹp.");
        addGrammarIfNotExists((short) 2, (short) 10, "-다고 하다", "Nghe nói rằng, bảo rằng", "Dùng để trích dẫn gián tiếp câu trần thuật của người khác.", "V + ㄴ/는다고 하다, A + 다고 하다", "-친구가 내일 고향에 간다고 해요.", "Bạn tôi bảo rằng ngày mai sẽ về quê.");

        // TOPIK 3
        addGrammarIfNotExists((short) 3, (short) 1, "-음에도 불구하고", "Mặc dù... nhưng vẫn...", "Biểu thị sự đối lập mạnh mẽ giữa hai vế, bất chấp hoàn cảnh vế 1.", "N + 에도 불구하고, V/A + (으)ㅁ에도 불구하고", "-어려운 환경에도 불구하고 그는 꿈을 이루었다.", "Bất chấp hoàn cảnh khó khăn, anh ấy vẫn thực hiện được ước mơ.");
        addGrammarIfNotExists((short) 3, (short) 2, "-덕분에", "Nhờ có...", "Biểu thị nguyên nhân đem lại kết quả tích cực, tốt đẹp.", "N + 덕분에, V + (으)ㄴ 덕분에", "-선생님께서 도와주신 덕분에 합격했습니다.", "Nhờ có thầy giúp đỡ nên em đã thi đỗ.");
        addGrammarIfNotExists((short) 3, (short) 3, "-기 십상이다", "Dễ dẫn đến, rất dễ bị...", "Dự đoán khả năng cao xảy ra một kết quả tiêu cực nếu tiếp tục.", "V + 기 십상이다", "-서두르다 보면 실수를 하기 십상이다.", "Nếu quá vội vàng thì rất dễ mắc sai lầm.");
        addGrammarIfNotExists((short) 3, (short) 7, "-았/었더라면", "Giá như mà... thì đã...", "Giả định tiếc nuối về một điều trong quá khứ không xảy ra.", "V/A + 았/었더라면", "-조금만 더 일찍 출발했더라면 기차를 놓치지 않았을 텐데.", "Giá như xuất phát sớm hơn một chút thì đã không bị lỡ tàu.");

        // TOPIK 4
        addGrammarIfNotExists((short) 4, (short) 1, "-(으)ㄹ 따름이다", "Chỉ có thể là, chỉ đành...", "Biểu thị sự bất lực hoặc nhấn mạnh mức độ duy nhất không còn cách khác.", "V/A + (으)ㄹ 따름이다", "-그저 감사할 따름입니다.", "Tôi chỉ biết vô cùng biết ơn mà thôi.");
        addGrammarIfNotExists((short) 4, (short) 2, "-(으)로 인하여", "Do bởi, bắt nguồn từ...", "Văn phong học thuật, nguyên nhân sâu xa hoặc yếu tố quyết định.", "N + (으)로 인하여", "-환경 오염으로 인하여 기후 변화가 심각해지고 있다.", "Do ô nhiễm môi trường nên biến đổi khí hậu đang trở nên nghiêm trọng.");
        addGrammarIfNotExists((short) 4, (short) 3, "-(으)ㄹ수록", "Càng... càng...", "Biểu thị mức độ biến thiên tỷ lệ thuận giữa hai vế.", "V/A + (으)ㄹ수록", "-한국어는 배울수록 흥미롭다.", "Tiếng Hàn càng học càng cảm thấy thú vị.");

        // TOPIK 5
        addGrammarIfNotExists((short) 5, (short) 1, "-기 짝이 없다", "Vô cùng, hết sức, không gì sánh bằng", "Văn phong trang trọng, diễn tả trạng thái cảm xúc cực điểm.", "A + 기 짝이 없다", "-그 소식을 듣고 안타깝기 짝이 없었습니다.", "Nghe tin đó xong tôi cảm thấy vô cùng xót xa.");
        addGrammarIfNotExists((short) 5, (short) 2, "-지 않고서는", "Nếu không... thì không thể...", "Khẳng định điều kiện tiên quyết mang tính tất yếu.", "V + 지 않고서는", "-직접 경험해 보지 않고서는 그 고통을 이해할 수 없다.", "Nếu không trực tiếp trải nghiệm thì không thể hiểu được nỗi đau ấy.");
        addGrammarIfNotExists((short) 5, (short) 5, "-에 비추어", "Căn cứ vào, soi chiếu theo...", "Dựa vào quy chuẩn hoặc tình hình thực tế để đánh giá/nhận định.", "N + 에 비추어 볼 때", "-과거의 사례에 비추어 볼 때 신중한 접근이 필요하다.", "Soi chiếu vào các tiền lệ quá khứ, cần có cách tiếp cận thận trọng.");

        // TOPIK 6
        addGrammarIfNotExists((short) 6, (short) 1, "-는 한편", "Một mặt thì... mặt khác...", "Diễn đạt hai hành động hoặc xu hướng cùng đồng thời diễn ra đối lập hoặc bổ sung.", "V + 는 한편", "-정부는 경제 성장을 추진하는 한편 물가 안정에도 힘써야 한다.", "Chính phủ một mặt thúc đẩy tăng trưởng kinh tế, mặt khác phải nỗ lực bình ổn giá cả.");
        addGrammarIfNotExists((short) 6, (short) 2, "-기 마련이다", "Đương nhiên là, tất yếu phải...", "Diễn tả quy luật tự nhiên, chân lý tất yếu.", "V/A + 기 마련이다", "-사람은 누구나 나이가 들면 건강에 신경 쓰기 마련이다.", "Con người ai ai khi có tuổi cũng tất yếu quan tâm đến sức khỏe.");
        addGrammarIfNotExists((short) 6, (short) 4, "-에 지나지 않다", "Chẳng qua chỉ là, không hơn không kém", "Nhấn mạnh mức độ không đáng kể hoặc chỉ là bề nổi của vấn đề.", "N + 에 지나지 않다", "-이번 사건은 빙산의 일각에 지나지 않는다.", "Sự việc lần này chẳng qua chỉ là phần nổi của tảng băng chìm.");
    }

    private void addGrammarIfNotExists(short level, short groupNo, String pattern, String translation,
                                       String meaning, String explanation, String exKo, String exVi) {
        Optional<GrammarGroup> gOpt = groupRepo.findByLevelAndGroupNo(level, groupNo);
        if (gOpt.isEmpty()) return;
        GrammarGroup group = gOpt.get();

        // Kiểm tra xem mẫu ngữ pháp này đã tồn tại trong nhóm chưa
        List<Grammar> list = grammarRepo.findByGrammarGroup_IdOrderBySortOrderAscIdAsc(group.getId());
        boolean exists = list.stream().anyMatch(g -> g.getPattern().equalsIgnoreCase(pattern));
        if (exists) return;

        Grammar g = new Grammar();
        g.setGrammarGroup(group);
        g.setLevel(level);
        g.setPattern(pattern);
        g.setTranslation(translation);
        g.setMeaning(meaning);
        g.setExplanation(explanation);
        g.setNotes("Dữ liệu mẫu chuẩn TOPIK " + level);
        g.setIsFrequent(true);
        g.setSortOrder((short) (list.size() + 1));
        g.setStatus("ACTIVE");
                        grammarRepo.save(g);

        if (exKo != null && exVi != null) {
            GrammarExample ex = new GrammarExample();
            ex.setGrammar(g);
            ex.setSentenceKo(exKo);
            ex.setSentenceVi(exVi);
            ex.setSortOrder((short) 1);
                                    exampleRepo.save(ex);
        }
    }

    private record GroupDef(short level, short groupNo, String nameVi) {}
}
