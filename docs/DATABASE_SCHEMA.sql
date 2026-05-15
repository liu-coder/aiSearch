-- AI Search 数据库 Schema 快照
-- 来源：
--   ai-search-video-service/src/main/resources/db/migration/video
--   ai-search-worker-service/src/main/resources/db/migration/worker
--
-- 规则：
--   1. 本文件用于阅读、代码生成和规则引用，不替代 Flyway 迁移。
--   2. 真实数据库演进必须新增 Flyway migration，禁止修改已发布迁移。
--   3. 修改迁移时同步更新本文件、JPA 实体、Repository、API/调试视图和测试。

create table if not exists video_asset (
    video_id varchar(80) not null primary key,
    bucket varchar(128) not null,
    object_key varchar(512) not null,
    file_name varchar(255) not null,
    file_size bigint not null,
    content_type varchar(120),
    title varchar(255),
    status varchar(32) not null,
    object_etag varchar(200),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    index idx_video_asset_status (status),
    index idx_video_asset_created_at (created_at)
);

create table if not exists event_outbox (
    id bigint not null auto_increment primary key,
    event_type varchar(80) not null,
    aggregate_id varchar(80) not null,
    payload text not null,
    status varchar(24) not null,
    attempts int not null,
    next_attempt_at datetime(6) not null,
    last_error varchar(1000),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    index idx_event_outbox_status_next (status, next_attempt_at),
    index idx_event_outbox_aggregate (aggregate_id)
);

create table if not exists video_processing_stage_task (
    id bigint not null auto_increment primary key,
    event_id varchar(80) not null,
    video_id varchar(80) not null,
    bucket varchar(128) not null,
    object_key varchar(512) not null,
    stage varchar(40) not null,
    status varchar(24) not null,
    stage_sequence int not null,
    attempts int not null,
    failure_reason varchar(1000),
    failure_type varchar(40),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    unique key uk_video_stage_event_stage (event_id, stage),
    index idx_video_stage_status_created (status, created_at),
    index idx_video_stage_status_updated (status, updated_at),
    index idx_video_stage_video (video_id, stage_sequence)
);

create table if not exists video_processing_artifact (
    id bigint not null auto_increment primary key,
    video_id varchar(80) not null,
    artifact_type varchar(60) not null,
    payload text not null,
    updated_at datetime(6) not null,
    unique key uk_video_artifact_type (video_id, artifact_type),
    index idx_video_artifact_video (video_id)
);

create table if not exists video_segment (
    segment_id varchar(120) not null primary key,
    video_id varchar(80) not null,
    start_time_ms bigint not null,
    end_time_ms bigint not null,
    key_frame_time_ms bigint not null,
    strategy_name varchar(80) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    index idx_video_segment_video_time (video_id, start_time_ms, end_time_ms)
);

create table if not exists video_segment_artifact (
    id bigint not null auto_increment primary key,
    video_id varchar(80) not null,
    segment_id varchar(120) not null,
    artifact_type varchar(60) not null,
    payload mediumtext not null,
    updated_at datetime(6) not null,
    unique key uk_segment_artifact_type (segment_id, artifact_type),
    index idx_segment_artifact_video (video_id),
    index idx_segment_artifact_segment (segment_id)
);

