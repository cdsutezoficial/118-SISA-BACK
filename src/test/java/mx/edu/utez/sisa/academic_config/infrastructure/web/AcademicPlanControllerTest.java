package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.PlanStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.AddPlanLevelUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.AddSubjectToPlanUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicPlanStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.GradeScaleEntryResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.GradeScaleResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetAcademicPlanUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPlansUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.RemoveGradeScaleUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.RemoveGradeScaleUseCase.RemoveGradeScaleCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.RemovePlanLevelUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.RemoveSubjectUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetGradeScaleUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicPlanUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGradeScaleUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePlanLevelUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateSubjectUseCase;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.AcademicPlanJpaRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.ClassificationNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateGradeScaleException;
import mx.edu.utez.sisa.academic_config.shared.exception.GradeScaleNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidGradeScaleEntriesException;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller tests for the {@code GradeScale} nested endpoints on
 * {@link AcademicPlanController} — mirrors {@code SubjectClassificationControllerTest}'s
 * style. Security is bypassed here ({@code addFilters = false}); role
 * authorization coverage lives in {@code AcademicPlanControllerIT}.
 */
@WebMvcTest(AcademicPlanController.class)
@AutoConfigureMockMvc(addFilters = false)
class AcademicPlanControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private CreateAcademicPlanUseCase createAcademicPlanUseCase;

	@MockitoBean
	private UpdateAcademicPlanUseCase updateAcademicPlanUseCase;

	@MockitoBean
	private ListAcademicPlansUseCase listAcademicPlansUseCase;

	@MockitoBean
	private GetAcademicPlanUseCase getAcademicPlanUseCase;

	@MockitoBean
	private ChangeAcademicPlanStatusUseCase changeAcademicPlanStatusUseCase;

	@MockitoBean
	private AddPlanLevelUseCase addPlanLevelUseCase;

	@MockitoBean
	private UpdatePlanLevelUseCase updatePlanLevelUseCase;

	@MockitoBean
	private RemovePlanLevelUseCase removePlanLevelUseCase;

	@MockitoBean
	private AddSubjectToPlanUseCase addSubjectToPlanUseCase;

	@MockitoBean
	private UpdateSubjectUseCase updateSubjectUseCase;

	@MockitoBean
	private RemoveSubjectUseCase removeSubjectUseCase;

	@MockitoBean
	private SetGradeScaleUseCase setGradeScaleUseCase;

	@MockitoBean
	private UpdateGradeScaleUseCase updateGradeScaleUseCase;

	@MockitoBean
	private RemoveGradeScaleUseCase removeGradeScaleUseCase;

	@MockitoBean
	private AcademicPlanJpaRepository academicPlanJpaRepository;

	@MockitoBean
	private JwtService jwtService;

	@Test
	void setGradeScaleReturns201WithBody() throws Exception {
		UUID planId = UUID.randomUUID();
		UUID scaleId = UUID.randomUUID();
		UUID classificationId = UUID.randomUUID();
		GradeScaleResult result = new GradeScaleResult(scaleId, classificationId, BigDecimal.valueOf(0),
				BigDecimal.valueOf(100),
				List.of(new GradeScaleEntryResult(UUID.randomUUID(), BigDecimal.valueOf(0), BigDecimal.valueOf(69),
						"NP", "No competente", false),
						new GradeScaleEntryResult(UUID.randomUUID(), BigDecimal.valueOf(70), BigDecimal.valueOf(100),
								"CO", "Competente", true)));
		when(setGradeScaleUseCase.setGradeScale(any())).thenReturn(result);

		mockMvc.perform(post("/plans/{id}/grade-scales", planId).contentType("application/json")
				.content(objectMapper.writeValueAsString(new SetGradeScaleBody(classificationId, BigDecimal.valueOf(0),
						BigDecimal.valueOf(100),
						List.of(new EntryBody(BigDecimal.valueOf(0), BigDecimal.valueOf(69), "NP", "No competente",
								false), new EntryBody(BigDecimal.valueOf(70), BigDecimal.valueOf(100), "CO",
								"Competente", true))))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(scaleId.toString()))
				.andExpect(jsonPath("$.classificationId").value(classificationId.toString()))
				.andExpect(jsonPath("$.entries").isArray()).andExpect(jsonPath("$.entries[0].letter").value("NP"))
				.andExpect(jsonPath("$.entries[1].letter").value("CO"));
	}

	@Test
	void setGradeScaleWithMissingClassificationIdReturns400() throws Exception {
		UUID planId = UUID.randomUUID();

		mockMvc.perform(post("/plans/{id}/grade-scales", planId).contentType("application/json")
				.content(objectMapper.writeValueAsString(new SetGradeScaleBody(null, BigDecimal.valueOf(0),
						BigDecimal.valueOf(100),
						List.of(new EntryBody(BigDecimal.valueOf(0), BigDecimal.valueOf(100), "CO", "Competente",
								true))))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void setGradeScaleWithUnknownClassificationReturns404() throws Exception {
		UUID planId = UUID.randomUUID();
		when(setGradeScaleUseCase.setGradeScale(any()))
				.thenThrow(new ClassificationNotFoundException("Subject classification not found"));

		mockMvc.perform(post("/plans/{id}/grade-scales", planId).contentType("application/json")
				.content(objectMapper.writeValueAsString(new SetGradeScaleBody(UUID.randomUUID(), BigDecimal.valueOf(0),
						BigDecimal.valueOf(100),
						List.of(new EntryBody(BigDecimal.valueOf(0), BigDecimal.valueOf(100), "CO", "Competente",
								true))))))
				.andExpect(status().isNotFound());
	}

	@Test
	void setGradeScaleWithDuplicateClassificationReturns409() throws Exception {
		UUID planId = UUID.randomUUID();
		when(setGradeScaleUseCase.setGradeScale(any()))
				.thenThrow(new DuplicateGradeScaleException("Grade scale already defined for this classification"));

		mockMvc.perform(post("/plans/{id}/grade-scales", planId).contentType("application/json")
				.content(objectMapper.writeValueAsString(new SetGradeScaleBody(UUID.randomUUID(), BigDecimal.valueOf(0),
						BigDecimal.valueOf(100),
						List.of(new EntryBody(BigDecimal.valueOf(0), BigDecimal.valueOf(100), "CO", "Competente",
								true))))))
				.andExpect(status().isConflict());
	}

	@Test
	void setGradeScaleWithGapInEntriesReturns400() throws Exception {
		UUID planId = UUID.randomUUID();
		when(setGradeScaleUseCase.setGradeScale(any()))
				.thenThrow(new InvalidGradeScaleEntriesException("Gap detected"));

		mockMvc.perform(post("/plans/{id}/grade-scales", planId).contentType("application/json")
				.content(objectMapper.writeValueAsString(new SetGradeScaleBody(UUID.randomUUID(), BigDecimal.valueOf(0),
						BigDecimal.valueOf(100),
						List.of(new EntryBody(BigDecimal.valueOf(0), BigDecimal.valueOf(60), "NP", "No competente",
								false))))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updateGradeScaleReturns200WithBody() throws Exception {
		UUID planId = UUID.randomUUID();
		UUID scaleId = UUID.randomUUID();
		UUID classificationId = UUID.randomUUID();
		GradeScaleResult result = new GradeScaleResult(scaleId, classificationId, BigDecimal.valueOf(0),
				BigDecimal.valueOf(10),
				List.of(new GradeScaleEntryResult(UUID.randomUUID(), BigDecimal.valueOf(0), BigDecimal.valueOf(10),
						"AP", "Aprobado", true)));
		when(updateGradeScaleUseCase.updateGradeScale(any())).thenReturn(result);

		mockMvc.perform(put("/plans/{id}/grade-scales/{scaleId}", planId, scaleId).contentType("application/json")
				.content(objectMapper.writeValueAsString(new SetGradeScaleBody(classificationId, BigDecimal.valueOf(0),
						BigDecimal.valueOf(10),
						List.of(new EntryBody(BigDecimal.valueOf(0), BigDecimal.valueOf(10), "AP", "Aprobado", true))))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.numericMax").value(10));
	}

	@Test
	void updateGradeScaleWithUnknownScaleReturns404() throws Exception {
		UUID planId = UUID.randomUUID();
		UUID scaleId = UUID.randomUUID();
		when(updateGradeScaleUseCase.updateGradeScale(any()))
				.thenThrow(new GradeScaleNotFoundException("Grade scale not found"));

		mockMvc.perform(put("/plans/{id}/grade-scales/{scaleId}", planId, scaleId).contentType("application/json")
				.content(objectMapper.writeValueAsString(new SetGradeScaleBody(UUID.randomUUID(), BigDecimal.valueOf(0),
						BigDecimal.valueOf(100),
						List.of(new EntryBody(BigDecimal.valueOf(0), BigDecimal.valueOf(100), "CO", "Competente",
								true))))))
				.andExpect(status().isNotFound());
	}

	@Test
	void removeGradeScaleReturns204() throws Exception {
		UUID planId = UUID.randomUUID();
		UUID scaleId = UUID.randomUUID();

		mockMvc.perform(delete("/plans/{id}/grade-scales/{scaleId}", planId, scaleId))
				.andExpect(status().isNoContent());

		org.mockito.Mockito.verify(removeGradeScaleUseCase)
				.removeGradeScale(new RemoveGradeScaleCommand(planId, scaleId));
	}

	@Test
	void removeGradeScaleWithUnknownScaleReturns404() throws Exception {
		UUID planId = UUID.randomUUID();
		UUID scaleId = UUID.randomUUID();
		org.mockito.Mockito.doThrow(new GradeScaleNotFoundException("Grade scale not found"))
				.when(removeGradeScaleUseCase).removeGradeScale(any());

		mockMvc.perform(delete("/plans/{id}/grade-scales/{scaleId}", planId, scaleId))
				.andExpect(status().isNotFound());
	}

	@Test
	void listPlanOptionsReturnsOnlyActivePlansLabeledByVersion() throws Exception {
		UUID planId = UUID.randomUUID();
		AcademicPlanJpaRepository.PlanOptionProjection active = mock(
				AcademicPlanJpaRepository.PlanOptionProjection.class);
		when(active.getId()).thenReturn(planId);
		when(active.getVersion()).thenReturn("2024-2");
		when(academicPlanJpaRepository.findByStatusOrderByVersionAsc(PlanStatus.ACTIVE))
				.thenReturn(List.of(active));

		mockMvc.perform(get("/plans/options")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(planId.toString()))
				.andExpect(jsonPath("$[0].label").value("2024-2"))
				.andExpect(jsonPath("$[0].code").isEmpty())
				.andExpect(jsonPath("$[1]").doesNotExist());

		verify(academicPlanJpaRepository).findByStatusOrderByVersionAsc(PlanStatus.ACTIVE);
	}

	@Test
	void listPlanOptionsFiltersByProgram() throws Exception {
		UUID programId = UUID.randomUUID();
		when(academicPlanJpaRepository.findByProgramIdAndStatusOrderByVersionAsc(programId, PlanStatus.ACTIVE))
				.thenReturn(List.of());

		mockMvc.perform(get("/plans/options").param("programId", programId.toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

		verify(academicPlanJpaRepository).findByProgramIdAndStatusOrderByVersionAsc(programId, PlanStatus.ACTIVE);
	}

	private record SetGradeScaleBody(UUID classificationId, BigDecimal numericMin, BigDecimal numericMax,
			List<EntryBody> entries) {
	}

	private record EntryBody(BigDecimal fromValue, BigDecimal toValue, String letter, String description,
			boolean passed) {
	}
}
