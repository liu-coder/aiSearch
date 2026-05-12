package com.aisearch.model.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * DashScope ASR 结果结构校准测试，锁定 transcripts[].sentences[].begin_time/end_time/text 解析。
 */
class DashScopeModelClientTest {
    @Test
    void parsesTranscriptionSentencesWithTimestamps() {
        DashScopeModelClient client = new DashScopeModelClient(RestClient.builder(), new ModelProviderProperties());

        var segments = client.extractSegmentsFromTranscription(Map.of(
                "transcripts", List.of(Map.of(
                        "sentences", List.of(
                                Map.of("begin_time", 1000, "end_time", 2500, "text", "第一句话", "speaker_id", 1),
                                Map.of("begin_time", 2600, "end_time", 4000, "text", "第二句话", "speaker_id", 1))))));

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).startTimeMs()).isEqualTo(1000);
        assertThat(segments.get(0).endTimeMs()).isEqualTo(2500);
        assertThat(segments.get(0).text()).isEqualTo("第一句话");
    }
}
