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
