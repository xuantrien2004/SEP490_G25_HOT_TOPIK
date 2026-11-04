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

// Mỗi bộ nhập giữ một đáp án chuẩn; hai ô 51/52 được đồng bộ trước khi autosave của đề thi nhận input.
document.querySelectorAll('[data-writing-editor]').forEach(editor => {
    const no = Number(editor.dataset.writingNo);
    const answer = editor.querySelector('[data-writing-answer]');
    const a = editor.querySelector('[data-writing-blank="a"]');
    const b = editor.querySelector('[data-writing-blank="b"]');
    const count = editor.querySelector('[data-writing-count]');
    if (no <= 52 && answer.value) {
        const parts = answer.value.match(/^㉠[ \t]*([\s\S]*?)\r?\n㉡[ \t]*([\s\S]*)$/);
        a.value = parts ? parts[1] : answer.value;
        b.value = parts ? parts[2] : '';
    }
    function update(event) {
        if (no <= 52 && event?.target.matches('[data-writing-blank]')) {
            const first = a.value.trim(), second = b.value.trim();
            answer.value = first || second ? '㉠ ' + first + '\n㉡ ' + second : '';
        }
        count.textContent = answer.value.trim().length + ' ký tự';
    }
    editor.addEventListener('input', update);
    update();
});
