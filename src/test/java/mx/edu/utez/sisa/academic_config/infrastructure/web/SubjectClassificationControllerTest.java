package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.ClassificationStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase.ClassificationResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase.CreateClassificationCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetSubjectClassificationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase.ClassificationSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase.ListSubjectClassificationsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase.ListSubjectClassificationsResult;
import mx.edu.utez.sisa.academic_config.shared.exception.ClassificationNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateClassificationCodeException;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller tests for {@link SubjectClassificationController}: Phase 1
 * (List) and Phase 2 (Create), mirroring {@code AcademicDivisionControllerTest}'s
 * style.
 */
@WebMvcTest(SubjectClassificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class SubjectClassificationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ListSubjectClassificationsUseCase listSubjectClassificationsUseCase;

	@MockitoBean
	private CreateSubjectClassificationUseCase createSubjectClassificationUseCase;

	@MockitoBean
	private GetSubjectClassificationUseCase getSubjectClassificationUseCase;

	@MockitoBean
	private JwtService jwtService;

	@Test
	void createClassificationReturns201WithBody() throws Exception {
		UUID classificationId = UUID.randomUUID();
		when(createSubjectClassificationUseCase
				.createClassification(new CreateClassificationCommand("Integradora", "INT")))
				.thenReturn(new ClassificationResult(classificationId, "Integradora", "INT",
						ClassificationStatus.ACTIVE));

		mockMvc.perform(post("/subject-classifications").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateClassificationBody("Integradora", "INT"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(classificationId.toString()))
				.andExpect(jsonPath("$.name").value("Integradora"))
				.andExpect(jsonPath("$.code").value("INT"))
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void createClassificationWithBlankNameReturns400() throws Exception {
		mockMvc.perform(post("/subject-classifications").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateClassificationBody("", "INT"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createClassificationWithBlankCodeReturns400() throws Exception {
		mockMvc.perform(post("/subject-classifications").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateClassificationBody("Integradora", ""))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createClassificationWithDuplicateCodeReturns409() throws Exception {
		when(createSubjectClassificationUseCase.createClassification(any()))
				.thenThrow(new DuplicateClassificationCodeException("Classification code already in use: INT"));

		mockMvc.perform(post("/subject-classifications").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateClassificationBody("Integradora", "INT"))))
				.andExpect(status().isConflict());
	}

	@Test
	void listClassificationsReturns200WithItemsAndPaginationMetadata() throws Exception {
		UUID classificationId = UUID.randomUUID();
		ClassificationSummary summary = new ClassificationSummary(classificationId, "Integradora", "INT",
				ClassificationStatus.ACTIVE);
		when(listSubjectClassificationsUseCase
				.listClassifications(new ListSubjectClassificationsQuery(ClassificationStatus.ACTIVE, "integ", 0, 20)))
				.thenReturn(new ListSubjectClassificationsResult(List.of(summary), 1L, 1, 0, 20));

		mockMvc.perform(get("/subject-classifications").param("status", "ACTIVE").param("search", "integ"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(classificationId.toString()))
				.andExpect(jsonPath("$.items[0].name").value("Integradora"))
				.andExpect(jsonPath("$.items[0].code").value("INT"))
				.andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.totalPages").value(1))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void listClassificationsDefaultsPageAndSizeWhenOmitted() throws Exception {
		when(listSubjectClassificationsUseCase.listClassifications(new ListSubjectClassificationsQuery(null, null, 0, 20)))
				.thenReturn(new ListSubjectClassificationsResult(List.of(), 0L, 0, 0, 20));

		mockMvc.perform(get("/subject-classifications")).andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty());

		verify(listSubjectClassificationsUseCase).listClassifications(new ListSubjectClassificationsQuery(null, null, 0, 20));
	}

	@Test
	void listClassificationsWithInvalidStatusQueryParamReturns400() throws Exception {
		mockMvc.perform(get("/subject-classifications").param("status", "NOT_A_STATUS"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void getClassificationReturns200WithBodyWhenFound() throws Exception {
		UUID classificationId = UUID.randomUUID();
		when(getSubjectClassificationUseCase.getById(classificationId)).thenReturn(
				new ClassificationResult(classificationId, "Integradora", "INT", ClassificationStatus.ACTIVE));

		mockMvc.perform(get("/subject-classifications/{id}", classificationId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(classificationId.toString()))
				.andExpect(jsonPath("$.name").value("Integradora"))
				.andExpect(jsonPath("$.code").value("INT"))
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void getClassificationReturns404WhenNotFound() throws Exception {
		UUID unknownId = UUID.randomUUID();
		when(getSubjectClassificationUseCase.getById(unknownId))
				.thenThrow(new ClassificationNotFoundException("Classification not found: " + unknownId));

		mockMvc.perform(get("/subject-classifications/{id}", unknownId)).andExpect(status().isNotFound());
	}

	private record CreateClassificationBody(String name, String code) {
	}
}
