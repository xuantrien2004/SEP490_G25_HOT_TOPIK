/* =====================================================================
   HOT TOPIK — schema SQL Server khớp entity hiện tại
   Microsoft SQL Server 2019+
   Database: HotTopik_2
   Collation: Vietnamese_100_CI_AS

   Script này sinh từ Hibernate 6.6 (SQLServerDialect) và đúng mapping
   trong com.hottopik.entity / com.hottopik.entity.exam.
   Không phải Database v5. Không dùng db/exam_module_sqlserver.sql.

   Chạy trên database TRỐNG.
   Schema v5 (users.role CHECK, question_types.id, question_options,
   roadmaps, source_exams, attempts, orders, ...) không nâng cấp được
   bằng script này. Backup trước. Nếu HotTopik_2 đang là v5, script dừng
   và không DROP gì cả. Muốn làm mới: backup, rồi tự DROP DATABASE
   HotTopik_2 (khối mẫu ở cuối phần đầu), chạy lại file này.

   Collation bắt buộc Vietnamese_100_CI_AS vì nhiều cột tiếng Việt là
   varchar (display_name, pattern, meaning_vi, sentence_vi, ...).
   nvarchar chỉ dùng ở cột @Nationalized hoặc columnDefinition nvarchar.

   Không chèn dữ liệu. Lần chạy Spring Boot đầu tiên tự thêm dạng câu,
   khuôn, ngữ pháp, Hán tự, bản lộ trình, đề mẫu và tài khoản demo.
   Profile local (H2, cổng 8490) không dùng file này.

   Login chạy script phải có default schema dbo (mặc định của sa).
   Bốn bảng users, user_settings, auth_tokens, email_outbox không khai
   báo schema trong entity nên Hibernate tạo chúng ở schema mặc định.
   Tên UK… / FK… là tên Hibernate sinh. Giữ nguyên để ddl-auto=update
   không tạo thêm một ràng buộc trùng.

   Chạy bằng SSMS hoặc sqlcmd. GO tách batch: CREATE DATABASE xong mới USE.
   ===================================================================== */

-- Muốn xoá database cũ (đã backup) thì chạy riêng ba dòng dưới, rồi chạy lại cả file:
-- USE master;
-- ALTER DATABASE HotTopik_2 SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
-- DROP DATABASE HotTopik_2;
-- GO

IF DB_ID(N'HotTopik_2') IS NULL
    CREATE DATABASE HotTopik_2 COLLATE Vietnamese_100_CI_AS;
GO
USE HotTopik_2;
GO

IF OBJECT_ID(N'dbo.roadmaps', N'U') IS NOT NULL
    OR OBJECT_ID(N'dbo.question_options', N'U') IS NOT NULL
    OR OBJECT_ID(N'dbo.source_exams', N'U') IS NOT NULL
    OR COL_LENGTH(N'dbo.question_types', N'id') IS NOT NULL
BEGIN
    THROW 51000, N'HotTopik_2 đang là schema cũ (v5 hoặc script đề cũ). Backup, DROP DATABASE HotTopik_2, rồi chạy lại file này. Script không tự xoá dữ liệu.', 1;
END
GO

IF OBJECT_ID(N'dbo.study_plans', N'U') IS NOT NULL
BEGIN
    THROW 51001, N'HotTopik_2 đã có schema hiện tại (có dbo.study_plans). Không chạy lại script này.', 1;
END
GO

create table dbo.exam_attempts (
    id bigint identity not null,
    answers_json nvarchar(max) not null,
    completed_at datetime2(6),
    correct int not null,
    last_active_at datetime2(6),
    remaining_seconds int not null,
    started_at datetime2(6) not null,
    total int not null,
    user_id bigint not null,
    exam_id bigint not null,
    primary key (id)
);

create table dbo.exam_items (
    id bigint identity not null,
    item_no int not null,
    section varchar(10) not null check (section in ('LISTENING','READING','WRITING')),
    type_code varchar(10) not null,
    exam_id bigint not null,
    question_id bigint not null,
    primary key (id)
);

create table dbo.exam_sources (
    id bigint identity not null,
    activated_at datetime2(6),
    is_assessment bit not null,
    copyright_note nvarchar(1000) not null,
    created_at datetime2(6) not null,
    created_by nvarchar(120),
    expected_count int not null,
    name nvarchar(200) not null,
    note nvarchar(1000),
    exam_round int,
    status varchar(10) not null check (status in ('DRAFT','ACTIVE')),
    track varchar(10) not null check (track in ('TOPIK_I','TOPIK_II')),
    updated_at datetime2(6) not null,
    exam_year int,
    primary key (id)
);

create table dbo.exams (
    id bigint identity not null,
    approval_request_id bigint,
    is_auto bit not null,
    code varchar(20),
    created_at datetime2(6) not null,
    created_by nvarchar(120),
    kind varchar(10) not null check (kind in ('FULL','LISTENING','READING','WRITING')),
    listening_minutes int not null,
    name nvarchar(200) not null,
    pending_action varchar(10),
    published_at datetime2(6),
    reading_minutes int not null,
    seq_no int not null,
    status varchar(20) not null check (status in ('DRAFT','PENDING_APPROVAL','PUBLISHED','ARCHIVED')),
    track varchar(10) not null check (track in ('TOPIK_I','TOPIK_II')),
    updated_at datetime2(6) not null,
    writing_minutes int not null,
    parent_exam_id bigint,
    source_id bigint,
    writing_51_id bigint,
    writing_52_id bigint,
    writing_53_id bigint,
    writing_54_id bigint,
    primary key (id)
);

create table dbo.grammar_examples (
    id bigint identity not null,
    created_at datetime2(6) not null,
    sentence_ko varchar(500) not null,
    sentence_vi varchar(500) not null,
    sort_order smallint not null,
    updated_at datetime2(6) not null,
    grammar_id bigint not null,
    primary key (id)
);

create table dbo.grammar_groups (
    id int identity not null,
    created_at datetime2(6) not null,
    group_no smallint not null,
    level smallint not null,
    name_vi varchar(100) not null,
    updated_at datetime2(6) not null,
    primary key (id)
);

create table dbo.grammars (
    id bigint identity not null,
    created_at datetime2(6) not null,
    explanation nvarchar(max),
    is_frequent bit not null,
    level smallint not null,
    meaning varchar(1000),
    notes nvarchar(max),
    pattern varchar(100) not null,
    sort_order smallint not null,
    status varchar(10) not null,
    translation varchar(200) not null,
    updated_at datetime2(6) not null,
    group_id int not null,
    primary key (id)
);

create table dbo.hanja_root_words (
    hanja_word varchar(20) not null,
    root_id int not null,
    vocabulary_id bigint not null,
    primary key (root_id, vocabulary_id)
);

create table dbo.hanja_roots (
    id int identity not null,
    audio_url varchar(500),
    created_at datetime2(6) not null,
    hanja_char varchar(4) not null,
    ko_reading varchar(10) not null,
    meaning_vi varchar(200),
    sino_vi varchar(30) not null,
    sort_order smallint not null,
    status varchar(10) not null,
    updated_at datetime2(6) not null,
    primary key (id)
);

create table dbo.import_jobs (
    id bigint identity not null,
    created_at datetime2(6) not null,
    created_by nvarchar(120),
    draft_json nvarchar(max),
    error_count int not null,
    file_name nvarchar(255) not null,
    file_path varchar(500) not null,
    issues_json nvarchar(max),
    mcq_rows int not null,
    status varchar(12) not null check (status in ('VALIDATING','HAS_ERRORS','VALID','ACTIVATED','SUPERSEDED')),
    validated_at datetime2(6),
    warning_count int not null,
    writing_rows int not null,
    source_id bigint not null,
    primary key (id)
);

create table dbo.media_assets (
    id bigint identity not null,
    created_at datetime2(6) not null,
    filename nvarchar(255) not null,
    kind varchar(10) not null check (kind in ('AUDIO','IMAGE')),
    size_bytes bigint not null,
    storage_path varchar(500) not null,
    source_id bigint not null,
    primary key (id)
);

create table dbo.placement_attempts (
    id bigint identity not null,
    answers_json varchar(4000) not null,
    completed_at datetime2(6),
    correct_count int not null,
    listen_correct int not null,
    listen_total int not null,
    paper_code varchar(20) not null,
    points int not null,
    question_ids varchar(2000) not null,
    read_correct int not null,
    read_total int not null,
    started_at datetime2(6) not null,
    total int not null,
    user_email varchar(200),
    user_id bigint,
    primary key (id)
);

create table dbo.placement_lines (
    id bigint identity not null,
    paper_code varchar(20) not null,
    question_count int not null,
    sort_order int not null,
    type_code varchar(10) not null,
    primary key (id)
);

create table dbo.practice_attempts (
    id bigint identity not null,
    answers_json varchar(4000) not null,
    completed_at datetime2(6),
    question_ids varchar(4000) not null,
    score int not null,
    skill varchar(10) not null check (skill in ('LISTENING','READING','WRITING')),
    started_at datetime2(6) not null,
    total int not null,
    track varchar(10) not null check (track in ('TOPIK_I','TOPIK_II')),
    type_code varchar(10) not null,
    user_email varchar(255) not null,
    user_id bigint not null,
    primary key (id)
);

create table dbo.program_version_items (
    id bigint identity not null,
    order_no int not null,
    program_id bigint not null,
    template_id bigint not null,
    primary key (id)
);

create table dbo.program_versions (
    id bigint identity not null,
    approval_request_id bigint,
    created_at datetime2(6) not null,
    created_by bigint,
    note nvarchar(500),
    pending_action varchar(10),
    phase_split varchar(20) not null,
    published_at datetime2(6),
    questions_per_session int default 8 not null,
    review_offsets varchar(30) not null,
    sessions_per_type int default 4 not null,
    status varchar(20) not null check (status in ('DRAFT','PENDING_APPROVAL','PUBLISHED','ARCHIVED')),
    updated_at datetime2(6) not null,
    version_code varchar(20) not null,
    weekly_review_questions int not null,
    primary key (id)
);

create table dbo.question_groups (
    id bigint identity not null,
    audio_file nvarchar(255),
    group_key varchar(50),
    image_file nvarchar(255),
    passage_ko nvarchar(4000),
    script_ko nvarchar(4000),
    script_vi nvarchar(4000),
    size int not null,
    skill varchar(10) not null check (skill in ('LISTENING','READING','WRITING')),
    type_code varchar(10) not null,
    source_id bigint,
    primary key (id)
);

create table dbo.question_templates (
    id bigint identity not null,
    allow_mini_test bit not null,
    allow_practice bit not null,
    allow_roadmap bit not null,
    answer_type varchar(20) not null check (answer_type in ('CHOICE_INDEX','ORDER','ACCEPT_KEYS','FREE_TEXT')),
    audio_required bit not null,
    audio_scope varchar(10) not null check (audio_scope in ('NONE','GROUP','QUESTION','OPTIONS')),
    child_max int not null,
    child_min int not null,
    code varchar(20) not null,
    created_at datetime2(6) not null,
    created_by nvarchar(120),
    customized bit not null,
    default_points int,
    description nvarchar(1000) not null,
    display_mode varchar(20) not null check (display_mode in ('SINGLE_CHOICE','IMAGE_CHOICE','ORDERING','FILL_BLANK','INSERT','TEXT_INPUT')),
    grading varchar(12) not null check (grading in ('AUTO','MANUAL','AI_ASSISTED')),
    image_required bit not null,
    image_scope varchar(10) not null check (image_scope in ('NONE','GROUP','QUESTION','OPTIONS')),
    max_chars int,
    min_chars int,
    name nvarchar(200) not null,
    option_count int,
    passage_required bit not null,
    passage_scope varchar(10) not null check (passage_scope in ('NONE','GROUP','QUESTION','OPTIONS')),
    prompt_required bit not null,
    rubric nvarchar(1000),
    script_scope varchar(10) not null check (script_scope in ('NONE','GROUP','QUESTION','OPTIONS')),
    skill varchar(10) not null check (skill in ('LISTENING','READING','WRITING')),
    status varchar(10) not null check (status in ('DRAFT','ACTIVE','INACTIVE')),
    system_owned bit not null,
    tags nvarchar(200),
    track varchar(10) not null check (track in ('TOPIK_I','TOPIK_II')),
    updated_at datetime2(6) not null,
    updated_by nvarchar(120),
    version int not null,
    zone_max int not null,
    zone_min int not null,
    primary key (id)
);

create table dbo.question_types (
    code varchar(10) not null,
    instruction_ko nvarchar(500),
    name_vi nvarchar(200) not null,
    number_ranges varchar(60) not null,
    questions_per_session int not null,
    skill varchar(10) not null check (skill in ('LISTENING','READING','WRITING')),
    order_in_roadmap int,
    template_code varchar(20),
    track varchar(10) not null check (track in ('TOPIK_I','TOPIK_II')),
    zone_max int not null,
    zone_min int not null,
    primary key (code)
);

create table dbo.questions (
    id bigint identity not null,
    answer_index int not null,
    cause_hint varchar(5),
    code varchar(20),
    content_hash varchar(64),
    created_at datetime2(6) not null,
    explanation_vi nvarchar(4000),
    grammar_ids varchar(500),
    hidden_reason nvarchar(500),
    option_1 nvarchar(1000) not null,
    option_2 nvarchar(1000) not null,
    option_3 nvarchar(1000) not null,
    option_4 nvarchar(1000) not null,
    original_no int not null,
    prompt_ko nvarchar(2000) not null,
    purpose varchar(12) not null check (purpose in ('EXAM','PRACTICE','ASSESSMENT')),
    skill varchar(10) not null check (skill in ('LISTENING','READING','WRITING')),
    stats_attempts int not null,
    stats_correct int not null,
    status varchar(10) not null check (status in ('ACTIVE','HIDDEN','DRAFT')),
    template_code varchar(20),
    track varchar(10) not null check (track in ('TOPIK_I','TOPIK_II')),
    type_code varchar(10) not null,
    updated_at datetime2(6) not null,
    vocab_ids varchar(500),
    group_id bigint not null,
    source_id bigint,
    primary key (id)
);

create table dbo.study_plans (
    id bigint identity not null,
    created_at datetime2(6) not null,
    done_keys varchar(max),
    items_per_day int not null,
    minutes_per_day int not null,
    owner_email varchar(180) not null,
    program_id bigint not null,
    start_date date not null,
    total_days int not null,
    track varchar(10) not null check (track in ('TOPIK_I','TOPIK_II')),
    version_code varchar(20) not null,
    primary key (id)
);

create table dbo.vocabularies (
    id bigint identity not null,
    audio_url varchar(500),
    created_at datetime2(6) not null,
    definition_ko varchar(500),
    hanja varchar(20),
    level smallint not null,
    meaning_vi varchar(300) not null,
    pos varchar(30),
    romanization varchar(100),
    sino_vi varchar(100),
    status varchar(10) not null,
    topic_id int not null,
    updated_at datetime2(6) not null,
    word varchar(100) not null,
    primary key (id)
);

create table dbo.writing_prompts (
    id bigint identity not null,
    chart_image nvarchar(255),
    chart_json nvarchar(2000),
    code varchar(20),
    created_at datetime2(6) not null,
    keys_1 nvarchar(1000),
    keys_2 nvarchar(1000),
    model_answer nvarchar(4000) not null,
    question_no int not null,
    prompt_ko nvarchar(4000) not null,
    rubric_note nvarchar(2000),
    status varchar(10) not null check (status in ('ACTIVE','HIDDEN','DRAFT')),
    source_id bigint,
    primary key (id)
);

create index ix_exam_attempt_user_exam 
   on dbo.exam_attempts (user_id, exam_id, started_at);

alter table dbo.exam_items 
   add constraint uq_exam_item_slot unique (exam_id, section, item_no);

create unique nonclustered index UK9vl25tb2un41h3rev69s6gasf 
   on dbo.exams (code) where code is not null;

alter table dbo.grammar_groups 
   add constraint UQ_grammar_groups unique (level, group_no);

alter table dbo.hanja_roots 
   add constraint UKgx5451s6x34j58re770k9ui2j unique (hanja_char);

alter table dbo.media_assets 
   add constraint uq_media_source_file unique (source_id, filename);

alter table dbo.placement_lines 
   add constraint uq_placement_line unique (paper_code, type_code);

create index ix_practice_user_type 
   on dbo.practice_attempts (user_id, type_code, completed_at);

alter table dbo.program_version_items 
   add constraint uq_program_item_template unique (program_id, template_id);

alter table dbo.program_versions 
   add constraint uq_program_versions_code unique (version_code);

create index ix_question_templates_track 
   on dbo.question_templates (track, skill, status);

alter table dbo.question_templates 
   add constraint uq_question_templates_code unique (code);

create index ix_questions_type 
   on dbo.questions (type_code, status, purpose);

create index ix_questions_hash 
   on dbo.questions (content_hash);

create unique nonclustered index UK3fh96b84uk043pou8ew1p8dwn 
   on dbo.questions (code) where code is not null;

alter table dbo.study_plans 
   add constraint uq_study_plans_owner unique (owner_email);

create unique nonclustered index UK2c4l3f0hid3lqsrbbwe77gelk 
   on dbo.writing_prompts (code) where code is not null;

create table auth_tokens (
    id bigint identity not null,
    created_at datetime2(6) not null,
    expires_at datetime2(6) not null,
    ip varchar(45),
    token_hash varchar(128) not null,
    type varchar(10) not null,
    updated_at datetime2(6) not null,
    used_at datetime2(6),
    user_id bigint not null,
    primary key (id)
);

create table email_outbox (
    id bigint identity not null,
    attempts smallint not null,
    created_at datetime2(6) not null,
    last_error varchar(1000),
    payload NVARCHAR(MAX),
    sent_at datetime2(6),
    status varchar(10) not null,
    template varchar(50) not null,
    to_email varchar(255) not null,
    updated_at datetime2(6) not null,
    primary key (id)
);

create table user_settings (
    user_id bigint not null,
    audio_speed numeric(3,2) not null,
    browser_push_enabled bit not null,
    created_at datetime2(6) not null,
    font_size varchar(10) not null,
    reminder_email bit not null,
    reminder_enabled bit not null,
    reminder_time time,
    show_romanization bit not null,
    theme varchar(10) not null,
    updated_at datetime2(6) not null,
    primary key (user_id)
);

create table users (
    id bigint identity not null,
    avatar_url varchar(500),
    coin_balance int not null,
    created_at datetime2(6) not null,
    current_level smallint,
    daily_goal_minutes smallint not null,
    deleted_at datetime2(6),
    display_name varchar(40) not null,
    email varchar(255) not null,
    email_verified_at datetime2(6),
    failed_login_count smallint not null,
    last_login_at datetime2(6),
    leaf_color varchar(20),
    level_source varchar(10),
    lock_reason varchar(500),
    locked_until datetime2(6),
    password_hash varchar(255) not null,
    role varchar(20) not null,
    status varchar(20) not null,
    studied_days int not null,
    target_level smallint,
    timezone varchar(50) not null,
    updated_at datetime2(6) not null,
    primary key (id)
);

alter table auth_tokens 
   add constraint UK3wqaabxbp24ko89s7lkjxch2v unique (token_hash);

alter table users 
   add constraint UK6dotkott2kjsp8vw4d0m25fb7 unique (email);

alter table dbo.exam_attempts 
   add constraint FK5tomiinihc09ywy0wh15pi2cs 
   foreign key (exam_id) 
   references dbo.exams;

alter table dbo.exam_items 
   add constraint FK5boju340v1sybdus0cqwc6mpp 
   foreign key (exam_id) 
   references dbo.exams;

alter table dbo.exam_items 
   add constraint FKpa3cutpsij0jo42gd01tgfrx7 
   foreign key (question_id) 
   references dbo.questions;

alter table dbo.exams 
   add constraint FKofnkrb8r36llxy0673vb86y5k 
   foreign key (parent_exam_id) 
   references dbo.exams;

alter table dbo.exams 
   add constraint FKrcicmh6u48lh6wo0nmmtct6fw 
   foreign key (source_id) 
   references dbo.exam_sources;

alter table dbo.exams 
   add constraint FK4a97fx8dk6foig7w9xlut5hxa 
   foreign key (writing_51_id) 
   references dbo.writing_prompts;

alter table dbo.exams 
   add constraint FK3qcgner8cv9c19b89b6iye5tv 
   foreign key (writing_52_id) 
   references dbo.writing_prompts;

alter table dbo.exams 
   add constraint FK428tufclpylq3owfoetkklm27 
   foreign key (writing_53_id) 
   references dbo.writing_prompts;

alter table dbo.exams 
   add constraint FKhgf280uhkrdln6176043v9d2d 
   foreign key (writing_54_id) 
   references dbo.writing_prompts;

alter table dbo.grammar_examples 
   add constraint FK_grammar_examples_grammar_id 
   foreign key (grammar_id) 
   references dbo.grammars;

alter table dbo.grammars 
   add constraint FK_grammars_group_id 
   foreign key (group_id) 
   references dbo.grammar_groups;

alter table dbo.hanja_root_words 
   add constraint FKmynwfkxpqmq7u5k9cogl9hakh 
   foreign key (root_id) 
   references dbo.hanja_roots;

alter table dbo.hanja_root_words 
   add constraint FKnw6o6xut1heet1dm4t51xl4rv 
   foreign key (vocabulary_id) 
   references dbo.vocabularies;

alter table dbo.import_jobs 
   add constraint FKdvijn2yskf321tcsu48mvpn51 
   foreign key (source_id) 
   references dbo.exam_sources;

alter table dbo.media_assets 
   add constraint FKg6sxt1sgryvk3hge901ni9tbo 
   foreign key (source_id) 
   references dbo.exam_sources;

alter table dbo.program_version_items 
   add constraint FK7kodttnq53q2ex52e5r0wdggo 
   foreign key (program_id) 
   references dbo.program_versions;

alter table dbo.program_version_items 
   add constraint FKhv6mt3akfia6127ldo65m4k33 
   foreign key (template_id) 
   references dbo.question_templates;

alter table dbo.question_groups 
   add constraint FKnxdfooikr38ms6tlepq4lopp 
   foreign key (source_id) 
   references dbo.exam_sources;

alter table dbo.questions 
   add constraint FK14n0bn9if6sqawe8un51k744j 
   foreign key (group_id) 
   references dbo.question_groups;

alter table dbo.questions 
   add constraint FK379yiu5pfnrc3ja00vrm5ep4t 
   foreign key (source_id) 
   references dbo.exam_sources;

alter table dbo.writing_prompts 
   add constraint FK6egdfvxqbui3lebs6kchf06ry 
   foreign key (source_id) 
   references dbo.exam_sources;
