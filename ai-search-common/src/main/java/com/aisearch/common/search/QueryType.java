package com.aisearch.common.search;

/**
 * 查询输入类型，用于决定后续走文本、图片或混合召回链路。
 */
public enum QueryType {
    TEXT,
    IMAGE,
    MIXED
}
