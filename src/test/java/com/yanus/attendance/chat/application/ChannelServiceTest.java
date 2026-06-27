package com.yanus.attendance.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanus.attendance.chat.FakeChannelRepository;
import com.yanus.attendance.chat.domain.Channel;
import com.yanus.attendance.chat.domain.ChannelType;
import com.yanus.attendance.chat.presentation.dto.ChannelResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ChannelServiceTest {

    private ChannelService channelService;
    private FakeChannelRepository channelRepository;

    @BeforeEach
    void setUp() {
        channelRepository = new FakeChannelRepository();
        channelService = new ChannelService(channelRepository);
    }

    @Test
    @DisplayName("전체 채널 목록 조회")
    void find_all() {
        // given
        channelRepository.save(Channel.create("General", ChannelType.GENERAL));
        channelRepository.save(Channel.create("개발팀", ChannelType.TEAM));

        // when
        List<ChannelResponse> result = channelService.findAll();

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("채널이 없으면 빈 목록을 반환")
    void find_all_empty() {
        // when
        List<ChannelResponse> result = channelService.findAll();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("조회 결과에 채널 이름과 타입이 매핑된다")
    void find_all_maps_fields() {
        // given
        channelRepository.save(Channel.create("General", ChannelType.GENERAL));

        // when
        ChannelResponse result = channelService.findAll().get(0);

        // then
        assertThat(result.name()).isEqualTo("General");
        assertThat(result.type()).isEqualTo(ChannelType.GENERAL);
    }
}
