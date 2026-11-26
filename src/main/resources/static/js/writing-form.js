// Trạng thái nhập bài độc lập với đánh dấu chưa chắc và câu đang xem.
function writingAnswerState(editor) {
    const no = Number(editor.dataset.writingNo);
    if (no <= 52) {
        const first = editor.querySelector('[data-writing-blank="a"]').value.trim();
        const second = editor.querySelector('[data-writing-blank="b"]').value.trim();
        return first && second ? 'complete' : first || second ? 'partial' : 'empty';
    }
    const length = Array.from(editor.querySelector('[data-writing-answer]').value.trim()).length;
    const min = no === 53 ? 200 : 600, max = no === 53 ? 300 : 700;
    return length === 0 ? 'empty' : length >= min && length <= max ? 'complete' : 'partial';
}

function updateWritingEditorCounter(editor) {
    if (!editor) return;
    const no = Number(editor.dataset.writingNo);
    const answer = editor.querySelector('[data-writing-answer]');
    const count = editor.querySelector('[data-writing-count]');
    const note = editor.querySelector('[data-writing-note]');
    if (!count || !answer) return;

    // Tính cả dấu câu và khoảng cách (độ dài chuỗi nguyên bản)
    const len = Array.from(answer.value).length;

    if (no <= 52) {
        count.textContent = len + ' ký tự';
        count.className = 'text-success fw-bold';
        count.style.color = '#16a34a';
        if (note) note.textContent = '';
        return;
    }

    const min = no === 53 ? 200 : 600;
    const max = no === 53 ? 300 : 700;
    const rangeLabel = (no === 53 ? '200–300' : '600–700') + ' ký tự';

    count.textContent = len + ' / ' + rangeLabel;

    if (len === 0) {
        count.className = 'text-danger fw-bold';
        count.style.color = '#dc2626';
        if (note) {
            note.className = 'text-danger small fw-semibold';
            note.style.color = '#dc2626';
            note.textContent = '⚠️ Chưa đủ số lượng ký tự yêu cầu (tối thiểu ' + min + ' ký tự)';
        }
    } else if (len < min) {
        count.className = 'text-danger fw-bold';
        count.style.color = '#dc2626';
        if (note) {
            note.className = 'text-danger small fw-semibold';
            note.style.color = '#dc2626';
            note.textContent = '⚠️ Chưa đủ số lượng ký tự yêu cầu (tối thiểu ' + min + ' ký tự, còn thiếu ' + (min - len) + ' ký tự)';
        }
    } else if (len > max) {
        count.className = 'text-danger fw-bold';
        count.style.color = '#dc2626';
        if (note) {
            note.className = 'text-danger small fw-semibold';
            note.style.color = '#dc2626';
            note.textContent = '⚠️ Đã vượt quá số lượng ký tự yêu cầu (tối đa ' + max + ' ký tự, vượt quá ' + (len - max) + ' ký tự)';
        }
    } else {
        count.className = 'text-success fw-bold';
        count.style.color = '#16a34a';
        if (note) {
            note.className = 'text-success small fw-semibold';
            note.style.color = '#16a34a';
            note.textContent = '✓ Số lượng ký tự đạt yêu cầu (' + rangeLabel + ')';
        }
    }
}
window.updateWritingEditorCounter = updateWritingEditorCounter;

// Mỗi bộ nhập giữ một đáp án chuẩn; hai ô 51/52 được đồng bộ trước khi autosave của đề thi nhận input.
document.querySelectorAll('[data-writing-editor]').forEach(editor => {
    const no = Number(editor.dataset.writingNo);
    const answer = editor.querySelector('[data-writing-answer]');
    const a = editor.querySelector('[data-writing-blank="a"]');
    const b = editor.querySelector('[data-writing-blank="b"]');
    if (no <= 52 && answer && answer.value) {
        const parts = answer.value.match(/^㉠[ \t]*([\s\S]*?)\r?\n㉡[ \t]*([\s\S]*)$/);
        if (a) a.value = parts ? parts[1] : answer.value;
        if (b) b.value = parts ? parts[2] : '';
    }
    function update(event) {
        if (no <= 52 && event?.target.matches('[data-writing-blank]')) {
            const first = a ? a.value.trim() : '', second = b ? b.value.trim() : '';
            if (answer) answer.value = first || second ? '㉠ ' + first + '\n㉡ ' + second : '';
        }
        updateWritingEditorCounter(editor);
    }
    editor.addEventListener('input', update);
    updateWritingEditorCounter(editor);
});
