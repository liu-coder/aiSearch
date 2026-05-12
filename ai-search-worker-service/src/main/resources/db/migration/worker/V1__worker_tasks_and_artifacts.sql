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
