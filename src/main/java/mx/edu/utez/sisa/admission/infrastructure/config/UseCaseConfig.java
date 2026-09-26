package mx.edu.utez.sisa.admission.infrastructure.config;

import mx.edu.utez.sisa.admission.domain.port.in.AccessFichaPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ChangeHighSchoolTypeStatusUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ChangeOutreachChannelStatusUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.GetHighSchoolTypeUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.GetOutreachChannelUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ListHighSchoolTypesUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ListOutreachChannelsUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateHighSchoolTypeUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateOutreachChannelUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmFichaPaymentVerifiedUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.GetFichaAmountUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.InitiateFichaPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.CandidatePersonRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.domain.port.out.PlaceNameLookupPort;
import mx.edu.utez.sisa.admission.domain.port.out.PaymentConceptQueryPort;
import mx.edu.utez.sisa.admission.domain.service.AccessFichaPaymentUseCaseImpl;
import mx.edu.utez.sisa.admission.domain.service.ChangeHighSchoolTypeStatusUseCaseImpl;
import mx.edu.utez.sisa.admission.domain.service.ChangeOutreachChannelStatusUseCaseImpl;
import mx.edu.utez.sisa.admission.domain.service.ConfirmAdmissionPaymentUseCaseImpl;
import mx.edu.utez.sisa.admission.domain.service.ConfirmFichaPaymentVerifiedUseCaseImpl;
import mx.edu.utez.sisa.admission.domain.service.FichaAmountResolver;
import mx.edu.utez.sisa.admission.domain.service.GetCandidateFichaUseCaseImpl;
import mx.edu.utez.sisa.admission.domain.service.GetFichaAmountUseCaseImpl;
import mx.edu.utez.sisa.admission.domain.service.CreateHighSchoolTypeUseCaseImpl;
import mx.edu.utez.sisa.admission.domain.service.CreateOutreachChannelUseCaseImpl;
import mx.edu.utez.sisa.admission.domain.service.GetHighSchoolTypeUseCaseImpl;
import mx.edu.utez.sisa.admission.domain.service.GetOutreachChannelUseCaseImpl;
import mx.edu.utez.sisa.admission.domain.service.InitiateFichaPaymentUseCaseImpl;
import mx.edu.utez.sisa.admission.domain.service.ListHighSchoolTypesUseCaseImpl;
import mx.edu.utez.sisa.admission.domain.service.ListOutreachChannelsUseCaseImpl;
import mx.edu.utez.sisa.admission.domain.service.OrderIdBuilder;
import mx.edu.utez.sisa.admission.domain.service.RegisterCandidateUseCaseImpl;
import mx.edu.utez.sisa.admission.domain.service.UpdateHighSchoolTypeUseCaseImpl;
import mx.edu.utez.sisa.admission.domain.service.UpdateOutreachChannelUseCaseImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Composition root wiring the {@code admission} bounded context's use case
 * interactors as Spring beans (same "per-module composition root" decision
 * as {@code academic_config.UseCaseConfig} and {@code identity.UseCaseConfig}).
 * The {@code XxxUseCaseImpl} classes are plain, framework-agnostic classes
 * (no stereotype annotations) so this is the only place that constructs them
 * with their out-port dependencies. A sibling of
 * {@code academic_config.UseCaseConfig} and {@code identity.UseCaseConfig},
 * not an extension of either — keeps the module independently removable,
 * same rationale that already separates those two bounded contexts.
 *
 * <p>Explicit {@code @Configuration} bean name ({@code
 * admissionUseCaseConfig}): this class, {@code academic_config.UseCaseConfig}
 * and {@code identity.UseCaseConfig} all share the same simple class name,
 * which otherwise collide under Spring's default annotation-derived bean
 * naming.
 *
 * <p>{@code HighSchoolType}'s 5 use cases (Fase A of plan:
 * {@code docs/plans/2026-07-28-inegi-catalogs-and-highschooltype.md}) are
 * wired right after {@code OutreachChannel}'s, same per-module composition
 * root convention.
 */
@Configuration("admissionUseCaseConfig")
public class UseCaseConfig {

	@Bean
	public CreateOutreachChannelUseCase createOutreachChannelUseCase(OutreachChannelRepository channelRepository) {
		return new CreateOutreachChannelUseCaseImpl(channelRepository);
	}

	@Bean
	public UpdateOutreachChannelUseCase updateOutreachChannelUseCase(OutreachChannelRepository channelRepository) {
		return new UpdateOutreachChannelUseCaseImpl(channelRepository);
	}

	@Bean
	public ListOutreachChannelsUseCase listOutreachChannelsUseCase(OutreachChannelRepository channelRepository) {
		return new ListOutreachChannelsUseCaseImpl(channelRepository);
	}

	@Bean
	public GetOutreachChannelUseCase getOutreachChannelUseCase(OutreachChannelRepository channelRepository) {
		return new GetOutreachChannelUseCaseImpl(channelRepository);
	}

	@Bean
	public ChangeOutreachChannelStatusUseCase changeOutreachChannelStatusUseCase(
			OutreachChannelRepository channelRepository) {
		return new ChangeOutreachChannelStatusUseCaseImpl(channelRepository);
	}

	@Bean
	public CreateHighSchoolTypeUseCase createHighSchoolTypeUseCase(HighSchoolTypeRepository highSchoolTypeRepository) {
		return new CreateHighSchoolTypeUseCaseImpl(highSchoolTypeRepository);
	}

	@Bean
	public UpdateHighSchoolTypeUseCase updateHighSchoolTypeUseCase(HighSchoolTypeRepository highSchoolTypeRepository) {
		return new UpdateHighSchoolTypeUseCaseImpl(highSchoolTypeRepository);
	}

	@Bean
	public ListHighSchoolTypesUseCase listHighSchoolTypesUseCase(HighSchoolTypeRepository highSchoolTypeRepository) {
		return new ListHighSchoolTypesUseCaseImpl(highSchoolTypeRepository);
	}

	@Bean
	public GetHighSchoolTypeUseCase getHighSchoolTypeUseCase(HighSchoolTypeRepository highSchoolTypeRepository) {
		return new GetHighSchoolTypeUseCaseImpl(highSchoolTypeRepository);
	}

	@Bean
	public ChangeHighSchoolTypeStatusUseCase changeHighSchoolTypeStatusUseCase(
			HighSchoolTypeRepository highSchoolTypeRepository) {
		return new ChangeHighSchoolTypeStatusUseCaseImpl(highSchoolTypeRepository);
	}

	@Bean
	public RegisterCandidateUseCase registerCandidateUseCase(CandidateRepository candidateRepository,
			CandidatePersonRepository candidatePersonRepository,
			AdmissionPaymentRepository admissionPaymentRepository,
			ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort, FichaAmountResolver fichaAmountResolver,
			OutreachChannelRepository outreachChannelRepository, HighSchoolTypeRepository highSchoolTypeRepository,
			@Value("${sisa.admission.payment.deadline-days:10}") int paymentDeadlineDays) {
		return new RegisterCandidateUseCaseImpl(candidateRepository, candidatePersonRepository,
				admissionPaymentRepository, programAdmissionConfigQueryPort, fichaAmountResolver,
				outreachChannelRepository, highSchoolTypeRepository, LocalDate.now(), paymentDeadlineDays);
	}

	/**
	 * One resolver shared by the registration command (which persists the ficha
	 * amount) and the public quote endpoint (which previews it), so the previewed
	 * price and the charged price can never diverge.
	 */
	@Bean
	public FichaAmountResolver fichaAmountResolver(PaymentConceptQueryPort paymentConceptQueryPort) {
		return new FichaAmountResolver(paymentConceptQueryPort);
	}

	@Bean
	public GetFichaAmountUseCase getFichaAmountUseCase(
			ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort,
			FichaAmountResolver fichaAmountResolver) {
		return new GetFichaAmountUseCaseImpl(programAdmissionConfigQueryPort, fichaAmountResolver, LocalDate.now());
	}

	@Bean
	public ConfirmAdmissionPaymentUseCase confirmAdmissionPaymentUseCase(CandidateRepository candidateRepository,
			AdmissionPaymentRepository admissionPaymentRepository) {
		return new ConfirmAdmissionPaymentUseCaseImpl(candidateRepository, admissionPaymentRepository);
	}

	@Bean
	public ConfirmFichaPaymentVerifiedUseCase confirmFichaPaymentVerifiedUseCase(CandidateRepository candidateRepository,
			AdmissionPaymentRepository admissionPaymentRepository, EvoPaymentsGatewayPort evoPaymentsGateway,
			ConfirmAdmissionPaymentUseCase confirmAdmissionPaymentUseCase) {
		return new ConfirmFichaPaymentVerifiedUseCaseImpl(candidateRepository, admissionPaymentRepository,
				evoPaymentsGateway, confirmAdmissionPaymentUseCase);
	}

	@Bean
	public OrderIdBuilder orderIdBuilder(EvoConfig evoConfig) {
		return new OrderIdBuilder(evoConfig.orderIdPrefix(), evoConfig.orderIdLength());
	}

	/**
	 * {@code returnPath} is an open-redirect surface, so only these in-app paths
	 * are ever honoured; every other value silently falls back to
	 * {@link EvoConfig#returnUrl()}. The scheme+host always come from the
	 * configured return URL, so the allowlist is the only thing a caller can
	 * influence and it cannot leave our origin.
	 */
	@Bean
	public InitiateFichaPaymentUseCase initiateFichaPaymentUseCase(CandidateRepository candidateRepository,
			AdmissionPaymentRepository admissionPaymentRepository, EvoPaymentsGatewayPort evoPaymentsGateway,
			OrderIdBuilder orderIdBuilder, EvoConfig evoConfig,
			@Value("${sisa.evo.allowed-return-paths:/portal/registro/ficha,/portal/ficha/pago}") String allowedReturnPaths) {
		Set<String> allowlist = Arrays.stream(allowedReturnPaths.split(",")).map(String::trim)
				.filter(path -> !path.isEmpty()).collect(Collectors.toUnmodifiableSet());
		return new InitiateFichaPaymentUseCaseImpl(candidateRepository, admissionPaymentRepository,
				evoPaymentsGateway, orderIdBuilder, evoConfig.currency(), evoConfig.returnUrl(),
				evoConfig.cancelUrl(), evoConfig.checkoutJsUrl(), allowlist);
	}

	@Bean
	public GetCandidateFichaUseCase getCandidateFichaUseCase(CandidateRepository candidateRepository,
			CandidatePersonRepository candidatePersonRepository,
			AdmissionPaymentRepository admissionPaymentRepository,
			ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort, PlaceNameLookupPort placeNameLookupPort,
			OutreachChannelRepository outreachChannelRepository, HighSchoolTypeRepository highSchoolTypeRepository) {
		return new GetCandidateFichaUseCaseImpl(candidateRepository, candidatePersonRepository,
				admissionPaymentRepository, programAdmissionConfigQueryPort, placeNameLookupPort,
				outreachChannelRepository, highSchoolTypeRepository);
	}

	/**
	 * "Vuelve a pagar mi ficha" access by folio + CURP suffix. Separate from
	 * {@link GetCandidateFichaUseCase} on purpose: that one projects the WHOLE
	 * ficha (address, health, income) and stays behind the UUID, while this one
	 * answers a weak identity proof and therefore only ever returns payment
	 * fields.
	 */
	@Bean
	public AccessFichaPaymentUseCase accessFichaPaymentUseCase(CandidateRepository candidateRepository,
			CandidatePersonRepository candidatePersonRepository,
			AdmissionPaymentRepository admissionPaymentRepository,
			ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort) {
		return new AccessFichaPaymentUseCaseImpl(candidateRepository, candidatePersonRepository,
				admissionPaymentRepository, programAdmissionConfigQueryPort);
	}
}
