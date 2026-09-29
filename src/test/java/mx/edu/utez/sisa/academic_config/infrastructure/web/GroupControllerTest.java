package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.GroupStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeGroupStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeGroupStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupUseCase.CreateGroupCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupUseCase.GroupResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetGroupUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGroupsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGroupsUseCase.GroupSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGroupsUseCase.ListGroupsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGroupsUseCase.ListGroupsResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGroupUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGroupUseCase.UpdateGroupCommand;
import mx.edu.utez.sisa.academic_config.shared.exception.GenerationReferenceNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.GroupNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PeriodNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelNotFoundException;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.identity.infrastructure.security.PermissionCache;
import mx.edu.utez.sisa.shared.model.Shift;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller tests for {@link GroupController}, mirroring
 * {@code GenerationControllerTest}'s style.
 */
@WebMvcTest(GroupController.class)
@AutoConfigureMockMvc(addFilters = false)
class GroupControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ListGroupsUseCase listGroupsUseCase;

	@MockitoBean
	private CreateGroupUseCase createGroupUseCase;

	@MockitoBean
	private GetGroupUseCase getGroupUseCase;

	@MockitoBean
	private UpdateGroupUseCase updateGroupUseCase;

	@MockitoBean
	private ChangeGroupStatusUseCase changeGroupStatusUseCase;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private PermissionCache permissionCache;

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
	void createGroupReturns201WithBody() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID generationId = UUID.randomUUID();
		UUID periodId = UUID.randomUUID();
		UUID planLevelId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		when(createGroupUseCase
				.createGroup(new CreateGroupCommand(generationId, periodId, planLevelId, "3A", 35, Shift.MORNING)))
				.thenReturn(new GroupResult(groupId, generationId, periodId, planLevelId, programId, "3A", 35,
						Shift.MORNING, GroupStatus.OPEN));

		mockMvc.perform(post("/groups").contentType("application/json")
				.content(objectMapper
						.writeValueAsString(new CreateBody(generationId, periodId, planLevelId, "3A", 35, Shift.MORNING))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(groupId.toString()))
				.andExpect(jsonPath("$.code").value("3A")).andExpect(jsonPath("$.maxCapacity").value(35))
				.andExpect(jsonPath("$.shift").value("MORNING")).andExpect(jsonPath("$.status").value("OPEN"));
	}

	@Test
	void createGroupIgnoresClientSuppliedProgramIdField() throws Exception {
		// CreateGroupRequest has no `programId` property at all — an extra
		// unknown field in the JSON body must simply be ignored, not rejected
		// or echoed back.
		UUID groupId = UUID.randomUUID();
		UUID generationId = UUID.randomUUID();
		UUID periodId = UUID.randomUUID();
		UUID planLevelId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		when(createGroupUseCase
				.createGroup(new CreateGroupCommand(generationId, periodId, planLevelId, "3A", 35, Shift.MORNING)))
				.thenReturn(new GroupResult(groupId, generationId, periodId, planLevelId, programId, "3A", 35,
						Shift.MORNING, GroupStatus.OPEN));

		mockMvc.perform(post("/groups").contentType("application/json")
				.content("{\"generationId\":\"" + generationId + "\",\"periodId\":\"" + periodId + "\",\"planLevelId\":\""
						+ planLevelId + "\",\"code\":\"3A\",\"maxCapacity\":35,\"shift\":\"MORNING\",\"programId\":\""
						+ UUID.randomUUID() + "\"}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.programId").value(programId.toString()));
	}

	@Test
	void createGroupWithMissingGenerationIdReturns400() throws Exception {
		mockMvc.perform(post("/groups").contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreateBody(null, UUID.randomUUID(), UUID.randomUUID(), "3A", 35, Shift.MORNING))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createGroupWithNonExistentGenerationIdReturns400() throws Exception {
		when(createGroupUseCase.createGroup(any()))
				.thenThrow(new GenerationReferenceNotFoundException("Generation not found: x"));

		mockMvc.perform(post("/groups").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(UUID.randomUUID(), UUID.randomUUID(),
						UUID.randomUUID(), "3A", 35, Shift.MORNING))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createGroupWithPlanLevelFromDifferentPlanReturns404() throws Exception {
		when(createGroupUseCase.createGroup(any())).thenThrow(new PlanLevelNotFoundException("Plan level not found: x"));

		mockMvc.perform(post("/groups").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(UUID.randomUUID(), UUID.randomUUID(),
						UUID.randomUUID(), "3A", 35, Shift.MORNING))))
				.andExpect(status().isNotFound());
	}

	@Test
	void createGroupWithNonExistentPeriodIdReturns400() throws Exception {
		when(createGroupUseCase.createGroup(any())).thenThrow(new PeriodNotFoundException("Academic period not found: x"));

		mockMvc.perform(post("/groups").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(UUID.randomUUID(), UUID.randomUUID(),
						UUID.randomUUID(), "3A", 35, Shift.MORNING))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void listGroupsReturns200WithItemsAndPaginationMetadata() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID generationId = UUID.randomUUID();
		UUID periodId = UUID.randomUUID();
		UUID planLevelId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		GroupSummary summary = new GroupSummary(groupId, generationId, periodId, planLevelId, programId, "3A", 35,
				Shift.MORNING, GroupStatus.OPEN);
		when(listGroupsUseCase.listGroups(
				new ListGroupsQuery(GroupStatus.OPEN, "3A", programId, generationId, 0, 20)))
				.thenReturn(new ListGroupsResult(List.of(summary), 1L, 1, 0, 20));

		mockMvc.perform(get("/groups").param("status", "OPEN").param("search", "3A")
				.param("programId", programId.toString()).param("generationId", generationId.toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(groupId.toString()))
				.andExpect(jsonPath("$.items[0].code").value("3A")).andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void listGroupsDefaultsPageAndSizeWhenOmitted() throws Exception {
		when(listGroupsUseCase.listGroups(new ListGroupsQuery(null, null, null, null, 0, 20)))
				.thenReturn(new ListGroupsResult(List.of(), 0L, 0, 0, 20));

		mockMvc.perform(get("/groups")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());

		verify(listGroupsUseCase).listGroups(new ListGroupsQuery(null, null, null, null, 0, 20));
	}

	@Test
	void getGroupReturns200WithBodyWhenFound() throws Exception {
		UUID groupId = UUID.randomUUID();
		when(getGroupUseCase.getById(groupId)).thenReturn(new GroupResult(groupId, UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), "3A", 35, Shift.MORNING, GroupStatus.OPEN));

		mockMvc.perform(get("/groups/{id}", groupId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(groupId.toString()));
	}

	@Test
	void getGroupReturns404WhenNotFound() throws Exception {
		UUID unknownId = UUID.randomUUID();
		when(getGroupUseCase.getById(unknownId)).thenThrow(new GroupNotFoundException("Group not found: " + unknownId));

		mockMvc.perform(get("/groups/{id}", unknownId)).andExpect(status().isNotFound());
	}

	@Test
	void updateGroupReturns200WithBody() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID generationId = UUID.randomUUID();
		UUID periodId = UUID.randomUUID();
		UUID planLevelId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		when(updateGroupUseCase.updateGroup(
				new UpdateGroupCommand(groupId, generationId, periodId, planLevelId, "3B", 40, Shift.AFTERNOON)))
				.thenReturn(new GroupResult(groupId, generationId, periodId, planLevelId, programId, "3B", 40,
						Shift.AFTERNOON, GroupStatus.OPEN));

		mockMvc.perform(put("/groups/{id}", groupId).contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new UpdateBody(generationId, periodId, planLevelId, "3B", 40, Shift.AFTERNOON))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("3B"))
				.andExpect(jsonPath("$.maxCapacity").value(40));
	}

	@Test
	void updateGroupReturns404WhenNotFound() throws Exception {
		when(updateGroupUseCase.updateGroup(any())).thenThrow(new GroupNotFoundException("Group not found: x"));

		mockMvc.perform(put("/groups/{id}", UUID.randomUUID()).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody(UUID.randomUUID(), UUID.randomUUID(),
						UUID.randomUUID(), "3B", 40, Shift.AFTERNOON))))
				.andExpect(status().isNotFound());
	}

	@Test
	void changeStatusReturns200WithUpdatedStatus() throws Exception {
		UUID groupId = UUID.randomUUID();
		when(changeGroupStatusUseCase.changeStatus(new ChangeStatusCommand(callerId, groupId, GroupStatus.CLOSED)))
				.thenReturn(new GroupResult(groupId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
						UUID.randomUUID(), "3A", 35, Shift.MORNING, GroupStatus.CLOSED));

		mockMvc.perform(patch("/groups/" + groupId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(GroupStatus.CLOSED))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));

		verify(changeGroupStatusUseCase).changeStatus(new ChangeStatusCommand(callerId, groupId, GroupStatus.CLOSED));
	}

	@Test
	void changeStatusOfUnknownGroupReturns404() throws Exception {
		UUID groupId = UUID.randomUUID();
		when(changeGroupStatusUseCase.changeStatus(any()))
				.thenThrow(new GroupNotFoundException("Group not found: " + groupId));

		mockMvc.perform(patch("/groups/" + groupId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(GroupStatus.CLOSED))))
				.andExpect(status().isNotFound());
	}

	private record CreateBody(UUID generationId, UUID periodId, UUID planLevelId, String code, int maxCapacity,
			Shift shift) {
	}

	private record UpdateBody(UUID generationId, UUID periodId, UUID planLevelId, String code, int maxCapacity,
			Shift shift) {
	}

	private record ChangeStatusBody(GroupStatus status) {
	}
}
