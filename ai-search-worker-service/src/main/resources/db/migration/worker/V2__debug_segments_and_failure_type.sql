alter table video_processing_stage_task
    add column if not exists failure_type varchar(40) null after failure_reason;

create table if not exists video_segment (
    segment_id varchar(120) not null primary key,
    video_id varchar(80) not null,
    start_time_ms bigint not null,
    end_time_ms bigint not null,
    key_frame_time_ms bigint not null,
    strategy_name varchar(80) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    index idx_video_segment_video_start (video_id, start_time_ms)
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
