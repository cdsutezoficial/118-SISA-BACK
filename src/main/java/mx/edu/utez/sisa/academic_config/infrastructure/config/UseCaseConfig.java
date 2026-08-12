package mx.edu.utez.sisa.academic_config.infrastructure.config;

import mx.edu.utez.sisa.academic_config.domain.port.in.AddPlanLevelUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.AddSubjectToPlanUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicDivisionStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicPeriodStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicPlanStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicProgramStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeGenerationStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeGroupStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangePaymentConceptStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeProgramAdmissionConfigStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeSubjectClassificationStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetProgramAdmissionConfigUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentRatesUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListProgramAdmissionConfigsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.OpenProgramAdmissionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetPaymentRateUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateProgramAdmissionConfigUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetAcademicDivisionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetAcademicPeriodUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetAcademicPlanUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetAcademicProgramUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetGenerationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetGroupUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetPaymentConceptUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetSubjectClassificationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicDivisionsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPeriodsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPlansUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicProgramsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGenerationsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGroupsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentConceptsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.RemoveGradeScaleUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.RemovePlanLevelUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.RemoveSubjectUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetGradeScaleUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicDivisionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicPeriodUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicPlanUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicProgramUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGenerationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGradeScaleUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGroupUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePaymentConceptUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePlanLevelUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateSubjectClassificationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateSubjectUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GroupRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentRateRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PersonLookupPort;
import mx.edu.utez.sisa.academic_config.domain.port.out.ProgramAdmissionConfigRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository;
import mx.edu.utez.sisa.academic_config.domain.service.AddPlanLevelUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.AddSubjectToPlanUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ChangeAcademicDivisionStatusUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ChangeAcademicPeriodStatusUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ChangeAcademicPlanStatusUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ChangeAcademicProgramStatusUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ChangeGenerationStatusUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ChangeSubjectClassificationStatusUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.CreateAcademicDivisionUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.CreateAcademicPeriodUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.CreateAcademicPlanUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.CreateAcademicProgramUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.CreateGenerationUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.CreateSubjectClassificationUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.GetAcademicDivisionUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.GetAcademicPeriodUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.GetAcademicPlanUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.GetAcademicProgramUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ChangeGroupStatusUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ChangePaymentConceptStatusUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.CreateGroupUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.CreatePaymentConceptUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.GetGenerationUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.GetGroupUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.GetPaymentConceptUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ListPaymentRatesUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ChangeProgramAdmissionConfigStatusUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.GetProgramAdmissionConfigUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ListProgramAdmissionConfigsUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.OpenProgramAdmissionUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.UpdateProgramAdmissionConfigUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.SetPaymentRateUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.GetSubjectClassificationUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ListAcademicDivisionsUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ListAcademicPeriodsUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ListAcademicPlansUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ListAcademicProgramsUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ListGenerationsUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ListGroupsUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ListPaymentConceptsUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ListSubjectClassificationsUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.UpdateGenerationUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.UpdateGroupUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.UpdatePaymentConceptUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.RemoveGradeScaleUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.RemovePlanLevelUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.RemoveSubjectUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.SetGradeScaleUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.UpdateAcademicDivisionUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.UpdateAcademicPeriodUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.UpdateAcademicPlanUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.UpdateAcademicProgramUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.UpdateGradeScaleUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.UpdatePlanLevelUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.UpdateSubjectClassificationUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.UpdateSubjectUseCaseImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root wiring the 4 {@code academic_config} use case interactors
 * as Spring beans (design.md — Decision: Per-module composition root). The
 * {@code XxxUseCaseImpl} classes are plain, framework-agnostic classes (no
 * stereotype annotations, matching {@code identity}'s convention) so this is
 * the only place that constructs them with their out-port dependencies. A
 * sibling of {@code identity.UseCaseConfig}, not an extension of it — keeps
 * the module independently removable.
 *
 * <p>Explicit {@code @Configuration} bean name ({@code
 * academicConfigUseCaseConfig}): both this class and
 * {@code identity.infrastructure.config.UseCaseConfig} share the same simple
 * class name, which otherwise collide under Spring's default
 * annotation-derived bean naming.
 */
@Configuration("academicConfigUseCaseConfig")
public class UseCaseConfig {

	@Bean
	public CreateAcademicDivisionUseCase createAcademicDivisionUseCase(AcademicDivisionRepository divisionRepository,
			PersonLookupPort personLookupPort) {
		return new CreateAcademicDivisionUseCaseImpl(divisionRepository, personLookupPort);
	}

	@Bean
	public UpdateAcademicDivisionUseCase updateAcademicDivisionUseCase(AcademicDivisionRepository divisionRepository,
			PersonLookupPort personLookupPort) {
		return new UpdateAcademicDivisionUseCaseImpl(divisionRepository, personLookupPort);
	}

	@Bean
	public ListAcademicDivisionsUseCase listAcademicDivisionsUseCase(AcademicDivisionRepository divisionRepository) {
		return new ListAcademicDivisionsUseCaseImpl(divisionRepository);
	}

	@Bean
	public GetAcademicDivisionUseCase getAcademicDivisionUseCase(AcademicDivisionRepository divisionRepository) {
		return new GetAcademicDivisionUseCaseImpl(divisionRepository);
	}

	@Bean
	public ChangeAcademicDivisionStatusUseCase changeAcademicDivisionStatusUseCase(
			AcademicDivisionRepository divisionRepository) {
		return new ChangeAcademicDivisionStatusUseCaseImpl(divisionRepository);
	}

	@Bean
	public CreateAcademicProgramUseCase createAcademicProgramUseCase(AcademicProgramRepository programRepository,
			AcademicDivisionRepository divisionRepository) {
		return new CreateAcademicProgramUseCaseImpl(programRepository, divisionRepository);
	}

	@Bean
	public UpdateAcademicProgramUseCase updateAcademicProgramUseCase(AcademicProgramRepository programRepository,
			AcademicDivisionRepository divisionRepository) {
		return new UpdateAcademicProgramUseCaseImpl(programRepository, divisionRepository);
	}

	@Bean
	public ListAcademicProgramsUseCase listAcademicProgramsUseCase(AcademicProgramRepository programRepository) {
		return new ListAcademicProgramsUseCaseImpl(programRepository);
	}

	@Bean
	public GetAcademicProgramUseCase getAcademicProgramUseCase(AcademicProgramRepository programRepository) {
		return new GetAcademicProgramUseCaseImpl(programRepository);
	}

	@Bean
	public ChangeAcademicProgramStatusUseCase changeAcademicProgramStatusUseCase(
			AcademicProgramRepository programRepository) {
		return new ChangeAcademicProgramStatusUseCaseImpl(programRepository);
	}

	@Bean
	public CreateAcademicPlanUseCase createAcademicPlanUseCase(AcademicPlanRepository planRepository,
			AcademicProgramRepository programRepository) {
		return new CreateAcademicPlanUseCaseImpl(planRepository, programRepository);
	}

	@Bean
	public UpdateAcademicPlanUseCase updateAcademicPlanUseCase(AcademicPlanRepository planRepository) {
		return new UpdateAcademicPlanUseCaseImpl(planRepository);
	}

	@Bean
	public ListAcademicPlansUseCase listAcademicPlansUseCase(AcademicPlanRepository planRepository) {
		return new ListAcademicPlansUseCaseImpl(planRepository);
	}

	@Bean
	public GetAcademicPlanUseCase getAcademicPlanUseCase(AcademicPlanRepository planRepository) {
		return new GetAcademicPlanUseCaseImpl(planRepository);
	}

	@Bean
	public ChangeAcademicPlanStatusUseCase changeAcademicPlanStatusUseCase(AcademicPlanRepository planRepository) {
		return new ChangeAcademicPlanStatusUseCaseImpl(planRepository);
	}

	@Bean
	public AddPlanLevelUseCase addPlanLevelUseCase(AcademicPlanRepository planRepository) {
		return new AddPlanLevelUseCaseImpl(planRepository);
	}

	@Bean
	public UpdatePlanLevelUseCase updatePlanLevelUseCase(AcademicPlanRepository planRepository) {
		return new UpdatePlanLevelUseCaseImpl(planRepository);
	}

	@Bean
	public RemovePlanLevelUseCase removePlanLevelUseCase(AcademicPlanRepository planRepository) {
		return new RemovePlanLevelUseCaseImpl(planRepository);
	}

	@Bean
	public AddSubjectToPlanUseCase addSubjectToPlanUseCase(AcademicPlanRepository planRepository) {
		return new AddSubjectToPlanUseCaseImpl(planRepository);
	}

	@Bean
	public UpdateSubjectUseCase updateSubjectUseCase(AcademicPlanRepository planRepository) {
		return new UpdateSubjectUseCaseImpl(planRepository);
	}

	@Bean
	public RemoveSubjectUseCase removeSubjectUseCase(AcademicPlanRepository planRepository) {
		return new RemoveSubjectUseCaseImpl(planRepository);
	}

	@Bean
	public SetGradeScaleUseCase setGradeScaleUseCase(AcademicPlanRepository planRepository,
			SubjectClassificationRepository classificationRepository) {
		return new SetGradeScaleUseCaseImpl(planRepository, classificationRepository);
	}

	@Bean
	public UpdateGradeScaleUseCase updateGradeScaleUseCase(AcademicPlanRepository planRepository,
			SubjectClassificationRepository classificationRepository) {
		return new UpdateGradeScaleUseCaseImpl(planRepository, classificationRepository);
	}

	@Bean
	public RemoveGradeScaleUseCase removeGradeScaleUseCase(AcademicPlanRepository planRepository) {
		return new RemoveGradeScaleUseCaseImpl(planRepository);
	}

	@Bean
	public ListSubjectClassificationsUseCase listSubjectClassificationsUseCase(
			SubjectClassificationRepository classificationRepository) {
		return new ListSubjectClassificationsUseCaseImpl(classificationRepository);
	}

	@Bean
	public CreateSubjectClassificationUseCase createSubjectClassificationUseCase(
			SubjectClassificationRepository classificationRepository) {
		return new CreateSubjectClassificationUseCaseImpl(classificationRepository);
	}

	@Bean
	public GetSubjectClassificationUseCase getSubjectClassificationUseCase(
			SubjectClassificationRepository classificationRepository) {
		return new GetSubjectClassificationUseCaseImpl(classificationRepository);
	}

	@Bean
	public UpdateSubjectClassificationUseCase updateSubjectClassificationUseCase(
			SubjectClassificationRepository classificationRepository) {
		return new UpdateSubjectClassificationUseCaseImpl(classificationRepository);
	}

	@Bean
	public ChangeSubjectClassificationStatusUseCase changeSubjectClassificationStatusUseCase(
			SubjectClassificationRepository classificationRepository) {
		return new ChangeSubjectClassificationStatusUseCaseImpl(classificationRepository);
	}

	@Bean
	public CreateAcademicPeriodUseCase createAcademicPeriodUseCase(AcademicPeriodRepository periodRepository) {
		return new CreateAcademicPeriodUseCaseImpl(periodRepository);
	}

	@Bean
	public UpdateAcademicPeriodUseCase updateAcademicPeriodUseCase(AcademicPeriodRepository periodRepository) {
		return new UpdateAcademicPeriodUseCaseImpl(periodRepository);
	}

	@Bean
	public ListAcademicPeriodsUseCase listAcademicPeriodsUseCase(AcademicPeriodRepository periodRepository) {
		return new ListAcademicPeriodsUseCaseImpl(periodRepository);
	}

	@Bean
	public GetAcademicPeriodUseCase getAcademicPeriodUseCase(AcademicPeriodRepository periodRepository) {
		return new GetAcademicPeriodUseCaseImpl(periodRepository);
	}

	@Bean
	public ChangeAcademicPeriodStatusUseCase changeAcademicPeriodStatusUseCase(
			AcademicPeriodRepository periodRepository) {
		return new ChangeAcademicPeriodStatusUseCaseImpl(periodRepository);
	}

	@Bean
	public CreateGenerationUseCase createGenerationUseCase(GenerationRepository generationRepository,
			AcademicPlanRepository planRepository, AcademicPeriodRepository periodRepository) {
		return new CreateGenerationUseCaseImpl(generationRepository, planRepository, periodRepository);
	}

	@Bean
	public UpdateGenerationUseCase updateGenerationUseCase(GenerationRepository generationRepository,
			AcademicPlanRepository planRepository, AcademicPeriodRepository periodRepository) {
		return new UpdateGenerationUseCaseImpl(generationRepository, planRepository, periodRepository);
	}

	@Bean
	public ListGenerationsUseCase listGenerationsUseCase(GenerationRepository generationRepository) {
		return new ListGenerationsUseCaseImpl(generationRepository);
	}

	@Bean
	public GetGenerationUseCase getGenerationUseCase(GenerationRepository generationRepository) {
		return new GetGenerationUseCaseImpl(generationRepository);
	}

	@Bean
	public ChangeGenerationStatusUseCase changeGenerationStatusUseCase(GenerationRepository generationRepository) {
		return new ChangeGenerationStatusUseCaseImpl(generationRepository);
	}

	@Bean
	public CreateGroupUseCase createGroupUseCase(GroupRepository groupRepository,
			GenerationRepository generationRepository, AcademicPlanRepository planRepository,
			AcademicPeriodRepository periodRepository) {
		return new CreateGroupUseCaseImpl(groupRepository, generationRepository, planRepository, periodRepository);
	}

	@Bean
	public UpdateGroupUseCase updateGroupUseCase(GroupRepository groupRepository,
			GenerationRepository generationRepository, AcademicPlanRepository planRepository,
			AcademicPeriodRepository periodRepository) {
		return new UpdateGroupUseCaseImpl(groupRepository, generationRepository, planRepository, periodRepository);
	}

	@Bean
	public ListGroupsUseCase listGroupsUseCase(GroupRepository groupRepository) {
		return new ListGroupsUseCaseImpl(groupRepository);
	}

	@Bean
	public GetGroupUseCase getGroupUseCase(GroupRepository groupRepository) {
		return new GetGroupUseCaseImpl(groupRepository);
	}

	@Bean
	public ChangeGroupStatusUseCase changeGroupStatusUseCase(GroupRepository groupRepository) {
		return new ChangeGroupStatusUseCaseImpl(groupRepository);
	}

	@Bean
	public CreatePaymentConceptUseCase createPaymentConceptUseCase(PaymentConceptRepository paymentConceptRepository) {
		return new CreatePaymentConceptUseCaseImpl(paymentConceptRepository);
	}

	@Bean
	public UpdatePaymentConceptUseCase updatePaymentConceptUseCase(PaymentConceptRepository paymentConceptRepository) {
		return new UpdatePaymentConceptUseCaseImpl(paymentConceptRepository);
	}

	@Bean
	public ListPaymentConceptsUseCase listPaymentConceptsUseCase(PaymentConceptRepository paymentConceptRepository) {
		return new ListPaymentConceptsUseCaseImpl(paymentConceptRepository);
	}

	@Bean
	public GetPaymentConceptUseCase getPaymentConceptUseCase(PaymentConceptRepository paymentConceptRepository) {
		return new GetPaymentConceptUseCaseImpl(paymentConceptRepository);
	}

	@Bean
	public ChangePaymentConceptStatusUseCase changePaymentConceptStatusUseCase(
			PaymentConceptRepository paymentConceptRepository) {
		return new ChangePaymentConceptStatusUseCaseImpl(paymentConceptRepository);
	}

	@Bean
	public SetPaymentRateUseCase setPaymentRateUseCase(PaymentRateRepository paymentRateRepository,
			PaymentConceptRepository paymentConceptRepository, AcademicProgramRepository programRepository,
			AcademicPeriodRepository periodRepository) {
		return new SetPaymentRateUseCaseImpl(paymentRateRepository, paymentConceptRepository, programRepository,
				periodRepository);
	}

	@Bean
	public ListPaymentRatesUseCase listPaymentRatesUseCase(PaymentRateRepository paymentRateRepository) {
		return new ListPaymentRatesUseCaseImpl(paymentRateRepository);
	}

	@Bean
	public OpenProgramAdmissionUseCase openProgramAdmissionUseCase(
			ProgramAdmissionConfigRepository programAdmissionConfigRepository, AcademicProgramRepository programRepository,
			AcademicPeriodRepository periodRepository, GenerationRepository generationRepository) {
		return new OpenProgramAdmissionUseCaseImpl(programAdmissionConfigRepository, programRepository, periodRepository,
				generationRepository);
	}

	@Bean
	public UpdateProgramAdmissionConfigUseCase updateProgramAdmissionConfigUseCase(
			ProgramAdmissionConfigRepository programAdmissionConfigRepository, AcademicProgramRepository programRepository,
			AcademicPeriodRepository periodRepository, GenerationRepository generationRepository) {
		return new UpdateProgramAdmissionConfigUseCaseImpl(programAdmissionConfigRepository, programRepository,
				periodRepository, generationRepository);
	}

	@Bean
	public ListProgramAdmissionConfigsUseCase listProgramAdmissionConfigsUseCase(
			ProgramAdmissionConfigRepository programAdmissionConfigRepository) {
		return new ListProgramAdmissionConfigsUseCaseImpl(programAdmissionConfigRepository);
	}

	@Bean
	public GetProgramAdmissionConfigUseCase getProgramAdmissionConfigUseCase(
			ProgramAdmissionConfigRepository programAdmissionConfigRepository) {
		return new GetProgramAdmissionConfigUseCaseImpl(programAdmissionConfigRepository);
	}

	@Bean
	public ChangeProgramAdmissionConfigStatusUseCase changeProgramAdmissionConfigStatusUseCase(
			ProgramAdmissionConfigRepository programAdmissionConfigRepository) {
		return new ChangeProgramAdmissionConfigStatusUseCaseImpl(programAdmissionConfigRepository);
	}
}
