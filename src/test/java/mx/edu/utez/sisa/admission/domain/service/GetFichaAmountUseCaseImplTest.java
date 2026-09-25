package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.admission.domain.port.in.GetFichaAmountUseCase.FichaAmountQuote;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort.AdmissionConfigInfo;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The wizard's price preview resolves the config to its program and delegates
 * to the shared {@link FichaAmountResolver}, so a quote can never disagree with
 * the amount the registration command later charges.
 */
@ExtendWith(MockitoExtension.class)
class GetFichaAmountUseCaseImplTest {

	private static final UUID CONFIG_ID = UUID.randomUUID();

	private static final UUID PROGRAM_ID = UUID.randomUUID();

	private static final LocalDate QUOTE_DATE = LocalDate.of(2026, 9, 25);

	@Mock
	private ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	@Mock
	private FichaAmountResolver fichaAmountResolver;

	private GetFichaAmountUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new GetFichaAmountUseCaseImpl(programAdmissionConfigQueryPort, fichaAmountResolver, QUOTE_DATE);
	}

	@Test
	void quotesTheProgramConceptAmount() {
		when(programAdmissionConfigQueryPort.findById(CONFIG_ID))
				.thenReturn(Optional.of(new AdmissionConfigInfo(CONFIG_ID, ProgramAdmissionConfigStatus.OPEN,
						PROGRAM_ID, "Ingeniería en Desarrollo y Gestión de Software", null, null)));
		when(fichaAmountResolver.resolve(PROGRAM_ID, QUOTE_DATE))
				.thenReturn(new FichaAmountResolver.FichaAmount(new BigDecimal("1578.00"), "Inscripción"));

		FichaAmountQuote quote = useCase.quote(CONFIG_ID);

		assertThat(quote.amount()).isEqualByComparingTo("1578.00");
		assertThat(quote.conceptName()).isEqualTo("Inscripción");
		assertThat(quote.programName()).isEqualTo("Ingeniería en Desarrollo y Gestión de Software");
		verify(fichaAmountResolver).resolve(PROGRAM_ID, QUOTE_DATE);
	}

	@Test
	void unknownConfigIs404() {
		when(programAdmissionConfigQueryPort.findById(CONFIG_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.quote(CONFIG_ID))
				.isInstanceOf(ProgramAdmissionConfigNotFoundException.class)
				.hasMessageContaining("No existe la configuración de admisión");
	}
}
