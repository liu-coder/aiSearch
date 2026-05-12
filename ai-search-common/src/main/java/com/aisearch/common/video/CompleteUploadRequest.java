package com.aisearch.common.video;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 上传完成确认请求。服务端会校验对象存储中是否已经存在对应文件。
 */
public record CompleteUploadRequest(
        @Size(max = 200) String objectETag,
        @Positive Long fileSize
) {
}
