package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.port.in.ListOutreachChannelsUseCase.ListOutreachChannelsQuery;
import mx.edu.utez.sisa.admission.domain.port.in.ListOutreachChannelsUseCase.ListOutreachChannelsResult;
import mx.edu.utez.sisa.admission.domain.port.in.ListOutreachChannelsUseCase.OutreachChannelSummary;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository.OutreachChannelSearchCriteria;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository.OutreachChannelSearchPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListOutreachChannelsUseCaseImplTest {

	@Mock
	private OutreachChannelRepository channelRepository;

	private ListOutreachChannelsUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ListOutreachChannelsUseCaseImpl(channelRepository);
	}

	@Test
	void listChannels_defaultPaginationUsesDefaultSize() {
		List<OutreachChannel> content = List.of(new OutreachChannel("Facebook"), new OutreachChannel("Feria"));
		when(channelRepository.search(any())).thenReturn(new OutreachChannelSearchPage(content, 22, 2));

		ListOutreachChannelsResult result = useCase.listChannels(new ListOutreachChannelsQuery(null, null, 0, 0));

		ArgumentCaptor<OutreachChannelSearchCriteria> captor = ArgumentCaptor
				.forClass(OutreachChannelSearchCriteria.class);
		verify(channelRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListOutreachChannelsQuery.DEFAULT_PAGE_SIZE);
		assertThat(captor.getValue().page()).isZero();
		assertThat(result.totalElements()).isEqualTo(22);
		assertThat(result.totalPages()).isEqualTo(2);
	}

	@Test
	void listChannels_mapsEntitiesToSummaries() {
		List<OutreachChannel> content = List.of(new OutreachChannel("Facebook"), new OutreachChannel("Feria"));
		when(channelRepository.search(any())).thenReturn(new OutreachChannelSearchPage(content, 2, 1));

		ListOutreachChannelsResult result = useCase.listChannels(new ListOutreachChannelsQuery(null, null, 0, 20));

		assertThat(result.items()).hasSize(2).extracting(OutreachChannelSummary::name)
				.containsExactlyInAnyOrder("Facebook", "Feria");
	}

	@Test
	void listChannels_oversizedPageIsCappedAtMax() {
		when(channelRepository.search(any())).thenReturn(new OutreachChannelSearchPage(List.of(), 0, 0));

		useCase.listChannels(new ListOutreachChannelsQuery(null, null, 0, 500));

		ArgumentCaptor<OutreachChannelSearchCriteria> captor = ArgumentCaptor
				.forClass(OutreachChannelSearchCriteria.class);
		verify(channelRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListOutreachChannelsQuery.MAX_PAGE_SIZE);
	}

	@Test
	void listChannels_negativePageIsNormalizedToZero() {
		when(channelRepository.search(any())).thenReturn(new OutreachChannelSearchPage(List.of(), 0, 0));

		useCase.listChannels(new ListOutreachChannelsQuery(null, null, -5, 20));

		ArgumentCaptor<OutreachChannelSearchCriteria> captor = ArgumentCaptor
				.forClass(OutreachChannelSearchCriteria.class);
		verify(channelRepository).search(captor.capture());
		assertThat(captor.getValue().page()).isZero();
	}
}
