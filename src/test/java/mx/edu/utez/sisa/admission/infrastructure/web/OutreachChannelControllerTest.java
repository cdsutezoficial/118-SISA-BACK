package mx.edu.utez.sisa.admission.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.admission.domain.model.OutreachChannelStatus;
import mx.edu.utez.sisa.admission.domain.port.in.ChangeOutreachChannelStatusUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ChangeOutreachChannelStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase.CreateOutreachChannelCommand;
import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase.OutreachChannelResult;
import mx.edu.utez.sisa.admission.domain.port.in.GetOutreachChannelUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ListOutreachChannelsUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ListOutreachChannelsUseCase.ListOutreachChannelsQuery;
import mx.edu.utez.sisa.admission.domain.port.in.ListOutreachChannelsUseCase.ListOutreachChannelsResult;
import mx.edu.utez.sisa.admission.domain.port.in.ListOutreachChannelsUseCase.OutreachChannelSummary;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateOutreachChannelUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateOutreachChannelUseCase.UpdateOutreachChannelCommand;
import mx.edu.utez.sisa.admission.infrastructure.persistence.OutreachChannelJpaRepository;
import mx.edu.utez.sisa.admission.shared.exception.OutreachChannelNotFoundException;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller tests for {@link OutreachChannelController}, mirroring
 * {@code SubjectClassificationControllerTest}'s style.
 */
@WebMvcTest(OutreachChannelController.class)
@AutoConfigureMockMvc(addFilters = false)
class OutreachChannelControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ListOutreachChannelsUseCase listOutreachChannelsUseCase;

	@MockitoBean
	private CreateOutreachChannelUseCase createOutreachChannelUseCase;

	@MockitoBean
	private GetOutreachChannelUseCase getOutreachChannelUseCase;

	@MockitoBean
	private UpdateOutreachChannelUseCase updateOutreachChannelUseCase;

	@MockitoBean
	private ChangeOutreachChannelStatusUseCase changeOutreachChannelStatusUseCase;

	@MockitoBean
	private OutreachChannelJpaRepository outreachChannelJpaRepository;

	@MockitoBean
	private JwtService jwtService;

	private UUID callerId;

	@BeforeEach
	void setUp() {
		callerId = UUID.randomUUID();
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				callerId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void createChannelReturns201WithBody() throws Exception {
		UUID channelId = UUID.randomUUID();
		when(createOutreachChannelUseCase.createChannel(new CreateOutreachChannelCommand("Facebook")))
				.thenReturn(new OutreachChannelResult(channelId, "Facebook", OutreachChannelStatus.ACTIVE));

		mockMvc.perform(post("/outreach-channels").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateChannelBody("Facebook")))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(channelId.toString())).andExpect(jsonPath("$.name").value("Facebook"))
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void createChannelWithBlankNameReturns400() throws Exception {
		mockMvc.perform(post("/outreach-channels").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateChannelBody("")))).andExpect(status().isBadRequest());
	}

	@Test
	void listChannelsReturns200WithItemsAndPaginationMetadata() throws Exception {
		UUID channelId = UUID.randomUUID();
		OutreachChannelSummary summary = new OutreachChannelSummary(channelId, "Facebook", OutreachChannelStatus.ACTIVE);
		when(listOutreachChannelsUseCase
				.listChannels(new ListOutreachChannelsQuery(OutreachChannelStatus.ACTIVE, "face", 0, 20)))
				.thenReturn(new ListOutreachChannelsResult(List.of(summary), 1L, 1, 0, 20));

		mockMvc.perform(get("/outreach-channels").param("status", "ACTIVE").param("search", "face"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(channelId.toString()))
				.andExpect(jsonPath("$.items[0].name").value("Facebook"))
				.andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.totalPages").value(1))
				.andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void listChannelsDefaultsPageAndSizeWhenOmitted() throws Exception {
		when(listOutreachChannelsUseCase.listChannels(new ListOutreachChannelsQuery(null, null, 0, 20)))
				.thenReturn(new ListOutreachChannelsResult(List.of(), 0L, 0, 0, 20));

		mockMvc.perform(get("/outreach-channels")).andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty());

		verify(listOutreachChannelsUseCase).listChannels(new ListOutreachChannelsQuery(null, null, 0, 20));
	}

	@Test
	void listChannelsWithInvalidStatusQueryParamReturns400() throws Exception {
		mockMvc.perform(get("/outreach-channels").param("status", "NOT_A_STATUS")).andExpect(status().isBadRequest());
	}

	@Test
	void getChannelReturns200WithBodyWhenFound() throws Exception {
		UUID channelId = UUID.randomUUID();
		when(getOutreachChannelUseCase.getById(channelId))
				.thenReturn(new OutreachChannelResult(channelId, "Facebook", OutreachChannelStatus.ACTIVE));

		mockMvc.perform(get("/outreach-channels/{id}", channelId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(channelId.toString())).andExpect(jsonPath("$.name").value("Facebook"));
	}

	@Test
	void getChannelReturns404WhenNotFound() throws Exception {
		UUID unknownId = UUID.randomUUID();
		when(getOutreachChannelUseCase.getById(unknownId))
				.thenThrow(new OutreachChannelNotFoundException("Outreach channel not found: " + unknownId));

		mockMvc.perform(get("/outreach-channels/{id}", unknownId)).andExpect(status().isNotFound());
	}

	@Test
	void updateChannelReturns200WithBody() throws Exception {
		UUID channelId = UUID.randomUUID();
		when(updateOutreachChannelUseCase.updateChannel(new UpdateOutreachChannelCommand(channelId, "Feria educativa")))
				.thenReturn(new OutreachChannelResult(channelId, "Feria educativa", OutreachChannelStatus.ACTIVE));

		mockMvc.perform(put("/outreach-channels/{id}", channelId).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateChannelBody("Feria educativa"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Feria educativa"));
	}

	@Test
	void updateChannelWithBlankNameReturns400() throws Exception {
		mockMvc.perform(put("/outreach-channels/{id}", UUID.randomUUID()).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateChannelBody("")))).andExpect(status().isBadRequest());
	}

	@Test
	void updateChannelReturns404WhenNotFound() throws Exception {
		UUID unknownId = UUID.randomUUID();
		when(updateOutreachChannelUseCase.updateChannel(any()))
				.thenThrow(new OutreachChannelNotFoundException("Outreach channel not found: " + unknownId));

		mockMvc.perform(put("/outreach-channels/{id}", unknownId).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateChannelBody("Feria educativa"))))
				.andExpect(status().isNotFound());
	}

	@Test
	void changeStatusReturns200WithUpdatedStatus() throws Exception {
		UUID channelId = UUID.randomUUID();
		when(changeOutreachChannelStatusUseCase
				.changeStatus(new ChangeStatusCommand(callerId, channelId, OutreachChannelStatus.INACTIVE)))
				.thenReturn(new OutreachChannelResult(channelId, "Facebook", OutreachChannelStatus.INACTIVE));

		mockMvc.perform(patch("/outreach-channels/" + channelId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(OutreachChannelStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));

		verify(changeOutreachChannelStatusUseCase)
				.changeStatus(new ChangeStatusCommand(callerId, channelId, OutreachChannelStatus.INACTIVE));
	}

	@Test
	void changeStatusOfUnknownChannelReturns404() throws Exception {
		UUID channelId = UUID.randomUUID();
		when(changeOutreachChannelStatusUseCase.changeStatus(any()))
				.thenThrow(new OutreachChannelNotFoundException("Outreach channel not found: " + channelId));

		mockMvc.perform(patch("/outreach-channels/" + channelId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(OutreachChannelStatus.ACTIVE))))
				.andExpect(status().isNotFound());
	}

	@Test
	void listChannelOptionsReturnsOnlyActiveChannelsLabeledByName() throws Exception {
		UUID channelId = UUID.randomUUID();
		OutreachChannelJpaRepository.OutreachChannelOptionProjection active = mock(
				OutreachChannelJpaRepository.OutreachChannelOptionProjection.class);
		when(active.getId()).thenReturn(channelId);
		when(active.getName()).thenReturn("Facebook");
		when(outreachChannelJpaRepository.findByStatusOrderByNameAsc(OutreachChannelStatus.ACTIVE))
				.thenReturn(List.of(active));

		mockMvc.perform(get("/outreach-channels/options")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(channelId.toString()))
				.andExpect(jsonPath("$[0].label").value("Facebook"))
				.andExpect(jsonPath("$[0].code").isEmpty())
				.andExpect(jsonPath("$[1]").doesNotExist());

		verify(outreachChannelJpaRepository).findByStatusOrderByNameAsc(OutreachChannelStatus.ACTIVE);
	}

	private record CreateChannelBody(String name) {
	}

	private record UpdateChannelBody(String name) {
	}

	private record ChangeStatusBody(OutreachChannelStatus status) {
	}
}
