/* =====================================================================
   HotTopik · Module nhập đề / thư viện đề / ngân hàng câu hỏi
   SQL Server (schema dbo). Chạy một lần trên DB của nhóm vì
   application.properties dùng spring.jpa.hibernate.ddl-auto=none.
   Khớp với entity trong com.hottopik.entity.exam (sinh từ Hibernate SQLServerDialect).
   Nếu DB đã có bảng trùng tên (ví dụ dbo.questions cũ) thì đổi tên bảng cũ trước khi chạy.
   ===================================================================== */

IF OBJECT_ID('dbo.question_types', 'U') IS NULL
CREATE TABLE dbo.question_types (
    code            varchar(10)   NOT NULL PRIMARY KEY,
    track           varchar(10)   NOT NULL CHECK (track IN ('TOPIK_I','TOPIK_II')),
    skill           varchar(10)   NOT NULL CHECK (skill IN ('LISTENING','READING','WRITING')),
    name            nvarchar(200) NOT NULL,
    zone_min        int           NOT NULL,
    zone_max        int           NOT NULL,
    ranges          varchar(60)   NOT NULL,
    required_count  int           NOT NULL,
    instruction_ko  nvarchar(500) NULL,
    sort_order      int           NOT NULL,
    template_code   varchar(20)   NULL
);
GO
IF COL_LENGTH('dbo.question_types', 'template_code') IS NULL
    ALTER TABLE dbo.question_types ADD template_code varchar(20) NULL;
GO

IF OBJECT_ID('dbo.question_templates', 'U') IS NULL
CREATE TABLE dbo.question_templates (
    id               bigint IDENTITY NOT NULL PRIMARY KEY,
    code             varchar(20)    NOT NULL,
    name             nvarchar(200)  NOT NULL,
    description      nvarchar(1000) NOT NULL,
    version          int            NOT NULL,
    status           varchar(10)    NOT NULL CHECK (status IN ('DRAFT','ACTIVE','INACTIVE')),
    track            varchar(10)    NOT NULL CHECK (track IN ('TOPIK_I','TOPIK_II')),
    skill            varchar(10)    NOT NULL CHECK (skill IN ('LISTENING','READING','WRITING')),
    zone_min         int            NOT NULL,
    zone_max         int            NOT NULL,
    tags             nvarchar(200)  NULL,
    display_mode     varchar(20)    NOT NULL,
    answer_type      varchar(20)    NOT NULL,
    prompt_required  bit            NOT NULL,
    audio_scope      varchar(10)    NOT NULL,
    audio_required   bit            NOT NULL,
    image_scope      varchar(10)    NOT NULL,
    image_required   bit            NOT NULL,
    passage_scope    varchar(10)   NOT NULL,
    passage_required bit            NOT NULL,
    script_scope     varchar(10)    NOT NULL,
    option_count     int            NULL,
    child_min        int            NOT NULL,
    child_max        int            NOT NULL,
    grading          varchar(12)    NOT NULL,
    default_points   int            NULL,
    min_chars        int            NULL,
    max_chars        int            NULL,
    rubric           nvarchar(1000) NULL,
    allow_mini_test  bit            NOT NULL,
    allow_practice   bit            NOT NULL,
    allow_roadmap    bit            NOT NULL,
    system_owned     bit            NOT NULL,
    customized       bit            NOT NULL,
    created_by       nvarchar(120)  NULL,
    updated_by       nvarchar(120)  NULL,
    created_at       datetime2(6)   NOT NULL,
    updated_at       datetime2(6)   NOT NULL,
    CONSTRAINT uq_question_templates_code UNIQUE (code)
);
GO

IF OBJECT_ID('dbo.exam_sources', 'U') IS NULL
CREATE TABLE dbo.exam_sources (
    id              bigint IDENTITY NOT NULL PRIMARY KEY,
    name            nvarchar(200)  NOT NULL,
    track           varchar(10)    NOT NULL CHECK (track IN ('TOPIK_I','TOPIK_II')),
    exam_year       int            NULL,
    exam_round      int            NULL,
    expected_count  int            NOT NULL,
    is_assessment   bit            NOT NULL,
    copyright_note  nvarchar(1000) NOT NULL,
    note            nvarchar(1000) NULL,
    status          varchar(10)    NOT NULL CHECK (status IN ('DRAFT','ACTIVE')),
    created_by      nvarchar(120)  NULL,
    activated_at    datetime2(6)   NULL,
    created_at      datetime2(6)   NOT NULL,
    updated_at      datetime2(6)   NOT NULL
);
GO

IF OBJECT_ID('dbo.import_jobs', 'U') IS NULL
CREATE TABLE dbo.import_jobs (
    id              bigint IDENTITY NOT NULL PRIMARY KEY,
    source_id       bigint        NOT NULL REFERENCES dbo.exam_sources(id),
    file_name       nvarchar(255) NOT NULL,
    file_path       varchar(500)  NOT NULL,
    status          varchar(12)   NOT NULL CHECK (status IN ('VALIDATING','HAS_ERRORS','VALID','ACTIVATED','SUPERSEDED')),
    mcq_rows        int           NOT NULL,
    writing_rows    int           NOT NULL,
    error_count     int           NOT NULL,
    warning_count   int           NOT NULL,
    issues_json     nvarchar(max) NULL,
    draft_json      nvarchar(max) NULL,
    created_by      nvarchar(120) NULL,
    created_at      datetime2(6)  NOT NULL,
    validated_at    datetime2(6)  NULL
);
GO

IF COL_LENGTH('dbo.import_jobs', 'draft_json') IS NULL
    ALTER TABLE dbo.import_jobs ADD draft_json nvarchar(max) NULL;
GO

IF OBJECT_ID('dbo.media_assets', 'U') IS NULL
CREATE TABLE dbo.media_assets (
    id              bigint IDENTITY NOT NULL PRIMARY KEY,
    source_id       bigint        NOT NULL REFERENCES dbo.exam_sources(id),
    filename        nvarchar(255) NOT NULL,
    kind            varchar(10)   NOT NULL CHECK (kind IN ('AUDIO','IMAGE')),
    size_bytes      bigint        NOT NULL,
    storage_path    varchar(500)  NOT NULL,
    created_at      datetime2(6)  NOT NULL,
    CONSTRAINT uq_media_source_file UNIQUE (source_id, filename)
);
GO

IF OBJECT_ID('dbo.question_groups', 'U') IS NULL
CREATE TABLE dbo.question_groups (
    id              bigint IDENTITY NOT NULL PRIMARY KEY,
    source_id       bigint         NULL REFERENCES dbo.exam_sources(id),
    group_key       varchar(50)    NULL,
    type_code       varchar(10)    NOT NULL,
    skill           varchar(10)    NOT NULL CHECK (skill IN ('LISTENING','READING','WRITING')),
    script_ko       nvarchar(4000) NULL,
    passage_ko      nvarchar(4000) NULL,
    audio_file      nvarchar(255)  NULL,
    image_file      nvarchar(255)  NULL,
    size            int            NOT NULL
);
GO

IF OBJECT_ID('dbo.questions', 'U') IS NULL
CREATE TABLE dbo.questions (
    id              bigint IDENTITY NOT NULL PRIMARY KEY,
    code            varchar(20)    NULL,
    source_id       bigint         NULL REFERENCES dbo.exam_sources(id),
    type_code       varchar(10)    NOT NULL,
    template_code   varchar(20)    NULL,
    track           varchar(10)    NOT NULL CHECK (track IN ('TOPIK_I','TOPIK_II')),
    skill           varchar(10)    NOT NULL CHECK (skill IN ('LISTENING','READING','WRITING')),
    original_no     int            NOT NULL,
    group_id        bigint         NOT NULL REFERENCES dbo.question_groups(id),
    prompt_ko       nvarchar(2000) NOT NULL,
    option_1        nvarchar(1000) NOT NULL,
    option_2        nvarchar(1000) NOT NULL,
    option_3        nvarchar(1000) NOT NULL,
    option_4        nvarchar(1000) NOT NULL,
    answer_index    int            NOT NULL,
    explanation_vi  nvarchar(4000) NULL,
    purpose         varchar(12)    NOT NULL CHECK (purpose IN ('EXAM','PRACTICE','ASSESSMENT')),
    status          varchar(10)    NOT NULL CHECK (status IN ('ACTIVE','HIDDEN','DRAFT')),
    cause_hint      varchar(5)     NULL,
    vocab_ids       varchar(500)   NULL,
    grammar_ids     varchar(500)   NULL,
    stats_attempts  int            NOT NULL,
    stats_correct   int            NOT NULL,
    content_hash    varchar(64)    NULL,
    hidden_reason   nvarchar(500)  NULL,
    created_at      datetime2(6)   NOT NULL,
    updated_at      datetime2(6)   NOT NULL
);
GO
IF COL_LENGTH('dbo.questions', 'template_code') IS NULL
    ALTER TABLE dbo.questions ADD template_code varchar(20) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uq_questions_code')
    CREATE UNIQUE NONCLUSTERED INDEX uq_questions_code ON dbo.questions (code) WHERE code IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_questions_type')
    CREATE INDEX ix_questions_type ON dbo.questions (type_code, status, purpose);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_questions_hash')
    CREATE INDEX ix_questions_hash ON dbo.questions (content_hash);
GO

IF OBJECT_ID('dbo.writing_prompts', 'U') IS NULL
CREATE TABLE dbo.writing_prompts (
    id              bigint IDENTITY NOT NULL PRIMARY KEY,
    code            varchar(20)    NULL,
    source_id       bigint         NULL REFERENCES dbo.exam_sources(id),
    question_no     int            NOT NULL,
    prompt_ko       nvarchar(4000) NOT NULL,
    keys_1          nvarchar(1000) NULL,
    keys_2          nvarchar(1000) NULL,
    chart_json      nvarchar(2000) NULL,
    chart_image     nvarchar(255)  NULL,
    model_answer    nvarchar(4000) NOT NULL,
    rubric_note     nvarchar(2000) NULL,
    status          varchar(10)    NOT NULL CHECK (status IN ('ACTIVE','HIDDEN','DRAFT')),
    created_at      datetime2(6)   NOT NULL
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uq_writing_prompts_code')
    CREATE UNIQUE NONCLUSTERED INDEX uq_writing_prompts_code ON dbo.writing_prompts (code) WHERE code IS NOT NULL;
GO

IF OBJECT_ID('dbo.exams', 'U') IS NULL
CREATE TABLE dbo.exams (
    id                  bigint IDENTITY NOT NULL PRIMARY KEY,
    code                varchar(20)   NULL,
    name                nvarchar(200) NOT NULL,
    track               varchar(10)   NOT NULL CHECK (track IN ('TOPIK_I','TOPIK_II')),
    kind                varchar(10)   NOT NULL CHECK (kind IN ('FULL','LISTENING','READING','WRITING')),
    seq_no              int           NOT NULL,
    parent_exam_id      bigint        NULL REFERENCES dbo.exams(id),
    is_auto             bit           NOT NULL,
    source_id           bigint        NULL REFERENCES dbo.exam_sources(id),
    status              varchar(20)   NOT NULL CHECK (status IN ('DRAFT','PENDING_APPROVAL','PUBLISHED','ARCHIVED')),
    pending_action      varchar(10)   NULL,
    approval_request_id bigint        NULL,
    listening_minutes   int           NOT NULL,
    writing_minutes     int           NOT NULL,
    reading_minutes     int           NOT NULL,
    writing_51_id       bigint        NULL REFERENCES dbo.writing_prompts(id),
    writing_52_id       bigint        NULL REFERENCES dbo.writing_prompts(id),
    writing_53_id       bigint        NULL REFERENCES dbo.writing_prompts(id),
    writing_54_id       bigint        NULL REFERENCES dbo.writing_prompts(id),
    created_by          nvarchar(120) NULL,
    published_at        datetime2(6)  NULL,
    created_at          datetime2(6)  NOT NULL,
    updated_at          datetime2(6)  NOT NULL
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uq_exams_code')
    CREATE UNIQUE NONCLUSTERED INDEX uq_exams_code ON dbo.exams (code) WHERE code IS NOT NULL;
GO

IF OBJECT_ID('dbo.exam_items', 'U') IS NULL
CREATE TABLE dbo.exam_items (
    id              bigint IDENTITY NOT NULL PRIMARY KEY,
    exam_id         bigint      NOT NULL REFERENCES dbo.exams(id),
    section         varchar(10) NOT NULL CHECK (section IN ('LISTENING','READING','WRITING')),
    item_no         int         NOT NULL,
    type_code       varchar(10) NOT NULL,
    question_id     bigint      NOT NULL REFERENCES dbo.questions(id),
    CONSTRAINT uq_exam_item_slot UNIQUE (exam_id, section, item_no)
);
GO

/* Bản lộ trình Content Manager phát hành. Học viên tạo lịch cá nhân từ bản PUBLISHED. */
IF OBJECT_ID('dbo.program_versions', 'U') IS NULL
CREATE TABLE dbo.program_versions (
    id                       bigint IDENTITY NOT NULL PRIMARY KEY,
    version_code             varchar(20)   NOT NULL,
    note                     nvarchar(500) NULL,
    status                   varchar(20)   NOT NULL CHECK (status IN ('DRAFT','PENDING_APPROVAL','PUBLISHED','ARCHIVED')),
    review_offsets           varchar(30)   NOT NULL,
    weekly_review_questions  int           NOT NULL,
    phase_split              varchar(20)   NOT NULL,
    sessions_per_type        int           NOT NULL,
    questions_per_session    int           NOT NULL,
    pending_action           varchar(10)   NULL,
    approval_request_id      bigint        NULL,
    created_by               nvarchar(120) NULL,
    published_at             datetime2(6)  NULL,
    created_at               datetime2(6)  NOT NULL,
    updated_at               datetime2(6)  NOT NULL,
    CONSTRAINT uq_program_versions_code UNIQUE (version_code)
);
GO

IF OBJECT_ID('dbo.program_version_items', 'U') IS NULL
CREATE TABLE dbo.program_version_items (
    id          bigint IDENTITY NOT NULL PRIMARY KEY,
    program_id  bigint NOT NULL REFERENCES dbo.program_versions(id),
    template_id bigint NOT NULL REFERENCES dbo.question_templates(id),
    order_no    int    NOT NULL,
    CONSTRAINT uq_program_item_template UNIQUE (program_id, template_id)
);
GO
