package com.hottopik.service;

import com.hottopik.entity.Grammar;
import com.hottopik.entity.UserSavedGrammar;
import com.hottopik.repository.GrammarRepository;
import com.hottopik.repository.UserSavedGrammarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Lưu / bỏ lưu mẫu ngữ pháp vào "Sổ ngữ pháp" của học viên. */
@Service
public class SavedGrammarService {

    private final UserSavedGrammarRepository savedRepository;
    private final GrammarRepository grammarRepository;

    public SavedGrammarService(UserSavedGrammarRepository savedRepository, GrammarRepository grammarRepository) {
        this.savedRepository = savedRepository;
        this.grammarRepository = grammarRepository;
    }

    /**
     * Đảo trạng thái lưu và trả về trạng thái mới (true = đã lưu).
     * Chỉ cho lưu mẫu đang hiển thị với học viên; bỏ lưu thì luôn được, kể cả khi mẫu đã bị ẩn sau đó.
     */
    @Transactional
    public boolean toggle(Long userId, Long grammarId) {
        if (userId == null) throw new IllegalArgumentException("Vui lòng đăng nhập để lưu ngữ pháp.");
        Optional<UserSavedGrammar> existing = savedRepository.findByUserIdAndGrammar_Id(userId, grammarId);
        if (existing.isPresent()) {
            savedRepository.delete(existing.get());
            return false;
        }
        Grammar grammar = grammarRepository.findDetailById(grammarId)
                .filter(g -> "ACTIVE".equalsIgnoreCase(g.getStatus()))
                .filter(g -> g.getGrammarGroup() != null && "ACTIVE".equalsIgnoreCase(g.getGrammarGroup().getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Mẫu ngữ pháp này không tồn tại hoặc hiện không khả dụng."));
        UserSavedGrammar saved = new UserSavedGrammar();
        saved.setUserId(userId);
        saved.setGrammar(grammar);
        // Unique (user_id, grammar_id) chặn lưu trùng khi bấm 2 lần quá nhanh; controller báo lỗi thay vì lưu 2 dòng.
        savedRepository.save(saved);
        return true;
    }

    @Transactional(readOnly = true)
    public List<UserSavedGrammar> getSavedGrammars(Long userId) {
        if (userId == null) return List.of();
        return savedRepository.findVisibleByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Set<Long> getSavedGrammarIds(Long userId) {
        if (userId == null) return Set.of();
        return new HashSet<>(savedRepository.findGrammarIdsByUserId(userId));
    }

    @Transactional(readOnly = true)
    public boolean isSaved(Long userId, Long grammarId) {
        return userId != null && grammarId != null
                && savedRepository.findByUserIdAndGrammar_Id(userId, grammarId).isPresent();
    }
}
