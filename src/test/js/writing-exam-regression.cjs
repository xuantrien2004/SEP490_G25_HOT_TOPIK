// Run after the writing workflow render tests: node src/test/js/writing-exam-regression.cjs
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');

class Element {
    constructor(dataset = {}) {
        this.dataset = dataset; this.listeners = {}; this.attrs = {}; this.style = {}; this.value = ''; this.textContent = '';
        this.classes = new Set();
        this.classList = {
            add: c => this.classes.add(c), remove: c => this.classes.delete(c), contains: c => this.classes.has(c),
            toggle: (c, enabled) => enabled ? this.classes.add(c) : this.classes.delete(c)
        };
    }
    addEventListener(type, fn) { (this.listeners[type] ??= []).push(fn); }
    async fire(type, options = {}) {
        const event = {preventDefault() {}, ...options};
        for (const listener of this.listeners[type] || []) await listener(event);
    }
    setAttribute(key, value) { this.attrs[key] = value; }
    getAttribute(key) { return this.attrs[key]; }
    removeAttribute(key) { delete this.attrs[key]; }
    focus() { this.focused = true; }
}

function harness() {
    const nodes = Object.fromEntries(['examForm', 'examSubmitNotice', 'examSubmitSummary', 'examContinue',
        'examCancelSubmit', 'examConfirmSubmit', 'saveStatus', 'timer'].map(id => [id, new Element()]));
    const form = nodes.examForm;
    form.action = '/exam/attempt/1/submit'; form.attrs.action = form.action; form.dataset.writingSubmit = 'true';
    const submitted = [];
    const tabs = [], questions = [], editors = [], flags = [];
    for (let no = 51; no <= 54; no++) {
        const q = new Element({writingQuestion: 'w' + no});
        const tab = new Element({writingTarget: 'w' + no}); tab.textContent = 'Câu ' + no;
        if (no === 51) tab.classes.add('current');
        const editor = new Element({writingNo: String(no)});
        const fields = Object.fromEntries(['answer', 'a', 'b', 'count'].map(key => [key, new Element()]));
        editor.fields = fields;
        editor.querySelector = selector => ({'[data-writing-answer]': fields.answer,
            '[data-writing-blank="a"]': fields.a, '[data-writing-blank="b"]': fields.b,
            '[data-writing-count]': fields.count})[selector];
        const flag = new Element(); flag.closest = () => q;
        q.querySelector = selector => selector === '[data-writing-editor]' ? editor : flag;
        tabs.push(tab); questions.push(q); editors.push(editor); flags.push(flag);
    }
    const document = {
        getElementById: id => nodes[id],
        querySelectorAll: selector => ({'[data-writing-tab]': tabs, '[data-writing-question]': questions,
            '[data-writing-editor]': editors, '[data-writing-uncertain]': flags, '.qp-question': []})[selector] || [],
        querySelector: selector => tabs.find(t => selector.includes('"' + t.dataset.writingTarget + '"'))
    };
    const intervals = [], context = vm.createContext({document, URLSearchParams,
        sessionStorage: {getItem: () => null, setItem() {}},
        window: {addEventListener() {}, location: {}}, navigator: {sendBeacon() {}},
        setInterval: (fn, delay) => intervals.push({fn, delay}), setTimeout() {}, clearTimeout() {},
        FormData: class { *[Symbol.iterator]() {} },
        HTMLFormElement: {prototype: {submit() { submitted.push(this.action); }}},
        fetch: async () => ({ok: true, headers: {get: () => 'application/json'},
            json: async () => ({remainingSeconds: 1000, completed: false})})
    });
    vm.runInContext(fs.readFileSync('src/main/resources/static/js/writing-form.js', 'utf8'), context);
    const html = fs.readFileSync('target/exam-writing-shared.html', 'utf8');
    const scripts = [...html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)];
    for (const [, attrs, script] of scripts) if (!/\bsrc=/.test(attrs)) new vm.Script(script);
    const script = scripts.find(([, , script]) => script.includes('function refreshWritingStates'))[2];
    vm.runInContext(script, context);
    return {nodes, form, tabs, questions, editors, flags, context, submitted, intervals,
        state: editor => context.writingAnswerState(editor)};
}

(async () => {
    const h = harness();
    const [short, , essay, longEssay] = h.editors;
    assert.equal(h.state(short), 'empty');
    short.fields.a.value = '첫째'; assert.equal(h.state(short), 'partial');
    short.fields.b.value = '둘째'; assert.equal(h.state(short), 'complete');
    for (const [editor, min, max] of [[essay, 200, 300], [longEssay, 600, 700]]) {
        for (const [length, expected] of [[0, 'empty'], [min - 1, 'partial'], [min, 'complete'],
            [max, 'complete'], [max + 1, 'partial']]) {
            editor.fields.answer.value = '한'.repeat(length);
            assert.equal(h.state(editor), expected, `${editor.dataset.writingNo}: ${length}`);
        }
        editor.fields.answer.value = '';
    }
    await short.fire('input', {target: {matches: () => true}});
    assert.equal(short.fields.answer.value, '㉠ 첫째\n㉡ 둘째');
    assert(h.tabs[0].classes.has('complete'));
    await h.flags[0].fire('click');
    assert(h.tabs[0].classes.has('uncertain')); assert(h.tabs[0].classes.has('complete'));
    await h.tabs[1].fire('click');
    assert(h.tabs[1].classes.has('current')); assert(!h.tabs[1].classes.has('complete'));
    assert(!h.tabs[0].classes.has('current')); assert(h.tabs[0].classes.has('complete'));
    await h.form.fire('submit');
    assert(h.nodes.examSubmitNotice.classes.has('show')); assert.equal(h.submitted.length, 0);
    assert.match(h.nodes.examSubmitSummary.textContent, /Câu 51: Hoàn thành · Chưa chắc/);
    assert.match(h.nodes.examSubmitSummary.textContent, /Câu 52: Chưa làm/);
    await h.nodes.examContinue.fire('click');
    assert(!h.nodes.examSubmitNotice.classes.has('show')); assert.equal(h.submitted.length, 0);
    await h.form.fire('submit'); await h.nodes.examCancelSubmit.fire('click');
    assert.match(h.submitted[0], /\/submit$/);
    await h.nodes.examConfirmSubmit.fire('click'); assert.equal(h.submitted.length, 1);

    const teacher = harness();
    teacher.editors[2].fields.answer.value = '일부';
    await teacher.form.fire('submit');
    assert.match(teacher.nodes.examSubmitSummary.textContent, /Câu 53: Chưa hoàn thành/);
    await teacher.nodes.examConfirmSubmit.fire('click');
    assert.match(teacher.submitted[0], /\/submit-teacher$/);

    const pause = harness();
    await pause.form.fire('submit', {submitter: {getAttribute: () => '/exam/attempt/1/pause'}});
    assert.deepEqual(pause.submitted, ['/exam/attempt/1/pause']);
    const timeout = harness();
    timeout.form.requestSubmit = () => timeout.form.fire('submit');
    vm.runInContext('seconds = 0; tick();', timeout.context);
    await Promise.resolve();
    assert.match(timeout.submitted[0], /\/submit$/);
    assert(!timeout.nodes.examSubmitNotice.classes.has('show'));

    const saving = harness(); let release;
    saving.context.fetch = () => new Promise(resolve => { release = () => resolve({ok: true,
        headers: {get: () => 'application/json'}, json: async () => ({remainingSeconds: 1000, completed: false})}); });
    const save = saving.context.saveAnswers(); await new Promise(setImmediate);
    const submit = saving.nodes.examConfirmSubmit.fire('click');
    assert.equal(saving.submitted.length, 0); release(); await save; await submit;
    assert.match(saving.submitted[0], /\/submit-teacher$/);

    const css = fs.readFileSync('src/main/resources/static/css/question-player.css', 'utf8');
    assert.match(css, /\.writing-number\.current\s*\{\s*border-color:/);
    assert.match(css, /\.writing-number\.uncertain\s*\{\s*box-shadow:/);
    assert.match(css, /\.qp-number\.current\s*\{\s*border-color:/);
    for (let no = 51; no <= 54; no++) {
        const html = fs.readFileSync(`target/writing-page-${no}.html`, 'utf8');
        const nodes = Object.fromEntries([...html.matchAll(/\bid="([^"]+)"/g)].map(([, id]) => [id, new Element()]));
        const scripts = [...html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)];
        for (const [, attrs, script] of scripts) if (!/\bsrc=/.test(attrs)) new vm.Script(script);
        let ready;
        const practice = vm.createContext({document: {getElementById: id => nodes[id],
            addEventListener: (type, fn) => { if (type === 'DOMContentLoaded') ready = fn; }}});
        vm.runInContext(scripts.find(([, , s]) => s.includes('const currentQ'))[2], practice);
        ready();
        assert.match(nodes.promptText.textContent, /문제/);
        assert.equal(nodes.promptHeader.textContent, 'CÂU ' + no);
        practice.submitWriting(false); assert(nodes.submitTeacherModal.classes.has('show'));
        practice.closeTeacherModal(); practice.submitWriting(true);
        assert(nodes.writingNotice.classes.has('show')); assert(!nodes.submitTeacherModal.classes.has('show'));
        nodes[no <= 52 ? 'blankAnsA' : 'essayInput'].value = '일부';
        practice.submitWriting(true); assert(nodes.submitTeacherModal.classes.has('show'));
    }
    console.log('PASS: writing boundaries, independent current/completion/uncertain states, submit/continue/cancel/teacher/pause/timeout, duplicate guard, autosave ordering and practice prompt/runtime (51–54).');
})().catch(error => { console.error(error); process.exitCode = 1; });
