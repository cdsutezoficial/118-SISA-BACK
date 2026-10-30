package mx.edu.utez.sisa.finance.infrastructure.config;

import mx.edu.utez.sisa.finance.domain.port.in.ApplyCreditToPaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.ChangeChargeProgramUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.CancelBenefitUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.CreateBenefitUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.GetAccountStatementUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.GetDiscountReportUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.GetPaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.ListBenefitsUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.QuoteBenefitUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.RegisterExemptionUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.RegisterPaymentEntryUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.UpdateBenefitUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.CancelDebtUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.GetChargeStatusUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.QuoteDebtUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.RaiseDebtUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.SettleDebtUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.AnchorQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptRateQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentCreditRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentEntryRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.domain.service.ApplyCreditToPaymentUseCaseImpl;
import mx.edu.utez.sisa.finance.domain.service.ChangeChargeProgramUseCaseImpl;
import mx.edu.utez.sisa.finance.domain.service.BenefitResolver;
import mx.edu.utez.sisa.finance.domain.service.CancelBenefitUseCaseImpl;
import mx.edu.utez.sisa.finance.domain.service.CreateBenefitUseCaseImpl;
import mx.edu.utez.sisa.finance.domain.service.CreatePaymentUseCaseImpl;
import mx.edu.utez.sisa.finance.domain.service.GetAccountStatementUseCaseImpl;
import mx.edu.utez.sisa.finance.domain.service.GetDiscountReportUseCaseImpl;
import mx.edu.utez.sisa.finance.domain.service.ListBenefitsUseCaseImpl;
import mx.edu.utez.sisa.finance.domain.service.PaymentQuoter;
import mx.edu.utez.sisa.finance.domain.service.PaymentSettlementDecider;
import mx.edu.utez.sisa.finance.domain.service.QuoteBenefitUseCaseImpl;
import mx.edu.utez.sisa.finance.domain.service.QuotePaymentUseCaseImpl;
import mx.edu.utez.sisa.finance.domain.service.RegisterExemptionUseCaseImpl;
import mx.edu.utez.sisa.finance.domain.service.RegisterPaymentEntryUseCaseImpl;
import mx.edu.utez.sisa.finance.domain.service.RetroactiveDiscountGranter;
import mx.edu.utez.sisa.finance.domain.service.UpdateBenefitUseCaseImpl;
import mx.edu.utez.sisa.finance.domain.service.CancelDebtUseCaseImpl;
import mx.edu.utez.sisa.finance.domain.service.GetChargeStatusUseCaseImpl;
import mx.edu.utez.sisa.finance.domain.service.DebtQuoter;
import mx.edu.utez.sisa.finance.domain.service.FreezeDebtQuoteUseCaseImpl;
import mx.edu.utez.sisa.finance.domain.service.QuoteDebtUseCaseImpl;
import mx.edu.utez.sisa.finance.domain.service.RaiseDebtUseCaseImpl;
import mx.edu.utez.sisa.finance.domain.service.SettleDebtUseCaseImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Composition root wiring {@code finance}'s interactors as Spring beans
 * (design.md — Per-module composition root). The {@code XxxUseCaseImpl}
 * classes are plain, framework-agnostic classes constructed only here — same
 * convention as {@code academicConfigUseCaseConfig}. The {@link Clock} bean is
 * the shared Mexico-zone clock defined by the admission module
 * ({@code sisa.admission.zone}), so benefit/concept windows and business dates
 * agree across modules.
 */
@Configuration("financeUseCaseConfig")
public class UseCaseConfig {

	@Bean
	public PaymentQuoter paymentQuoter() {
		return new PaymentQuoter();
	}

	@Bean
	public PaymentSettlementDecider paymentSettlementDecider() {
		return new PaymentSettlementDecider();
	}

	@Bean
	public BenefitResolver benefitResolver() {
		return new BenefitResolver();
	}

	@Bean
	public QuotePaymentUseCase quotePaymentUseCase(AnchorQueryPort anchorQueryPort, ConceptQueryPort conceptQueryPort,
			ConceptRateQueryPort conceptRateQueryPort, PaymentBenefitRepository paymentBenefitRepository,
			PaymentQuoter paymentQuoter, BenefitResolver benefitResolver, Clock clock) {
		return new QuotePaymentUseCaseImpl(anchorQueryPort, conceptQueryPort, conceptRateQueryPort,
				paymentBenefitRepository, paymentQuoter, benefitResolver, clock);
	}

	@Bean
	public CreatePaymentUseCase createPaymentUseCase(QuotePaymentUseCase quotePaymentUseCase,
			PaymentRepository paymentRepository, Clock clock) {
		return new CreatePaymentUseCaseImpl(quotePaymentUseCase, paymentRepository, clock);
	}

	/**
	 * Cross-context quoter consumed by {@code admission} to price a ficha with
	 * the applicant's discount (Fase 2). Same resolver + quoter the finance
	 * quoter uses, so the two cannot disagree on what a discount means.
	 */
	@Bean
	public QuoteBenefitUseCase quoteBenefitUseCase(PaymentBenefitRepository paymentBenefitRepository,
			BenefitResolver benefitResolver, PaymentQuoter paymentQuoter, Clock clock) {
		return new QuoteBenefitUseCaseImpl(paymentBenefitRepository, benefitResolver, paymentQuoter, clock);
	}

	/**
	 * The single pricing rule for an existing charge: the concept's rate in
	 * force for its program, minus the discount in force for that charge.
	 */
	@Bean
	public DebtQuoter debtQuoter(ConceptQueryPort conceptQueryPort, ConceptRateQueryPort conceptRateQueryPort,
			PaymentBenefitRepository paymentBenefitRepository, BenefitResolver benefitResolver,
			PaymentQuoter paymentQuoter) {
		return new DebtQuoter(conceptQueryPort, conceptRateQueryPort, paymentBenefitRepository, benefitResolver,
				paymentQuoter);
	}

	/** Header-only charge: who owes which catalog concept. Priced live, later. */
	@Bean
	public RaiseDebtUseCase raiseDebtUseCase(PaymentRepository paymentRepository, AnchorQueryPort anchorQueryPort,
			ConceptQueryPort conceptQueryPort, Clock clock) {
		return new RaiseDebtUseCaseImpl(paymentRepository, anchorQueryPort, conceptQueryPort, clock);
	}

	/** Live price of a charge, never written. */
	@Bean
	public QuoteDebtUseCase quoteDebtUseCase(PaymentRepository paymentRepository, DebtQuoter debtQuoter,
			Clock clock, PaymentBenefitRepository paymentBenefitRepository,
			PaymentCreditRepository paymentCreditRepository) {
		return new QuoteDebtUseCaseImpl(paymentRepository, debtQuoter, clock, paymentBenefitRepository,
				paymentCreditRepository);
	}

	/** The "photo" of the live price, taken when the payer acts. */
	@Bean
	public FreezeDebtQuoteUseCaseImpl freezeDebtQuoteUseCase(PaymentRepository paymentRepository,
			DebtQuoter debtQuoter, Clock clock) {
		return new FreezeDebtQuoteUseCaseImpl(paymentRepository, debtQuoter, clock);
	}

	@Bean
	public SettleDebtUseCase settleDebtUseCase(PaymentRepository paymentRepository,
			FreezeDebtQuoteUseCaseImpl freezeDebtQuoteUseCase, RegisterPaymentEntryUseCase registerPaymentEntryUseCase,
			RegisterExemptionUseCase registerExemptionUseCase, Clock clock) {
		return new SettleDebtUseCaseImpl(paymentRepository, freezeDebtQuoteUseCase, registerPaymentEntryUseCase,
				registerExemptionUseCase, clock);
	}

	@Bean
	public CancelDebtUseCase cancelDebtUseCase(PaymentRepository paymentRepository) {
		return new CancelDebtUseCaseImpl(paymentRepository);
	}

	@Bean
	public GetChargeStatusUseCase getChargeStatusUseCase(PaymentRepository paymentRepository,
			PaymentEntryRepository paymentEntryRepository) {
		return new GetChargeStatusUseCaseImpl(paymentRepository, paymentEntryRepository);
	}

	@Bean
	public RegisterPaymentEntryUseCase registerPaymentEntryUseCase(PaymentRepository paymentRepository,
			PaymentEntryRepository paymentEntryRepository, PaymentBenefitRepository paymentBenefitRepository,
			PaymentSettlementDecider paymentSettlementDecider, Clock clock) {
		return new RegisterPaymentEntryUseCaseImpl(paymentRepository, paymentEntryRepository, paymentBenefitRepository,
				paymentSettlementDecider, clock);
	}

	@Bean
	public GetAccountStatementUseCase getAccountStatementUseCase(AnchorQueryPort anchorQueryPort,
			PaymentRepository paymentRepository, PaymentEntryRepository paymentEntryRepository,
			PaymentBenefitRepository paymentBenefitRepository) {
		return new GetAccountStatementUseCaseImpl(anchorQueryPort, paymentRepository, paymentEntryRepository,
				paymentBenefitRepository);
	}

	@Bean
	public GetPaymentUseCase getPaymentUseCase(AnchorQueryPort anchorQueryPort, PaymentRepository paymentRepository,
			PaymentEntryRepository paymentEntryRepository, PaymentBenefitRepository paymentBenefitRepository) {
		return new GetAccountStatementUseCaseImpl(anchorQueryPort, paymentRepository, paymentEntryRepository,
				paymentBenefitRepository);
	}

	@Bean
	public RetroactiveDiscountGranter retroactiveDiscountGranter(PaymentRepository paymentRepository,
			PaymentCreditRepository paymentCreditRepository, PaymentQuoter paymentQuoter) {
		return new RetroactiveDiscountGranter(paymentRepository, paymentCreditRepository, paymentQuoter);
	}

	@Bean
	public CreateBenefitUseCase createBenefitUseCase(AnchorQueryPort anchorQueryPort,
			PaymentBenefitRepository paymentBenefitRepository, PaymentRepository paymentRepository,
			RetroactiveDiscountGranter retroactiveDiscountGranter, Clock clock) {
		return new CreateBenefitUseCaseImpl(anchorQueryPort, paymentBenefitRepository, paymentRepository,
				retroactiveDiscountGranter, clock);
	}

	@Bean
	public UpdateBenefitUseCase updateBenefitUseCase(PaymentBenefitRepository paymentBenefitRepository,
			PaymentRepository paymentRepository, Clock clock) {
		return new UpdateBenefitUseCaseImpl(paymentBenefitRepository, paymentRepository, clock);
	}

	@Bean
	public CancelBenefitUseCase cancelBenefitUseCase(PaymentBenefitRepository paymentBenefitRepository,
			PaymentRepository paymentRepository, Clock clock) {
		return new CancelBenefitUseCaseImpl(paymentBenefitRepository, paymentRepository, clock);
	}

	@Bean
	public ListBenefitsUseCase listBenefitsUseCase(PaymentBenefitRepository paymentBenefitRepository, Clock clock) {
		return new ListBenefitsUseCaseImpl(paymentBenefitRepository, clock);
	}

	@Bean
	public GetDiscountReportUseCase getDiscountReportUseCase(PaymentBenefitRepository paymentBenefitRepository,
			Clock clock) {
		return new GetDiscountReportUseCaseImpl(paymentBenefitRepository, clock);
	}

	@Bean
	public RegisterExemptionUseCase registerExemptionUseCase(PaymentRepository paymentRepository,
			PaymentEntryRepository paymentEntryRepository, PaymentBenefitRepository paymentBenefitRepository,
			Clock clock) {
		return new RegisterExemptionUseCaseImpl(paymentRepository, paymentEntryRepository, paymentBenefitRepository,
				clock);
	}

	@Bean
	public ApplyCreditToPaymentUseCase applyCreditToPaymentUseCase(PaymentRepository paymentRepository,
			PaymentEntryRepository paymentEntryRepository, PaymentCreditRepository paymentCreditRepository,
			PaymentBenefitRepository paymentBenefitRepository, PaymentSettlementDecider paymentSettlementDecider,
			Clock clock) {
		return new ApplyCreditToPaymentUseCaseImpl(paymentRepository, paymentEntryRepository, paymentCreditRepository,
				paymentBenefitRepository, paymentSettlementDecider, clock);
	}

	@Bean
	public ChangeChargeProgramUseCase changeChargeProgramUseCase(PaymentRepository paymentRepository,
			PaymentEntryRepository paymentEntryRepository, PaymentCreditRepository paymentCreditRepository,
			PaymentBenefitRepository paymentBenefitRepository, ConceptQueryPort conceptQueryPort,
			ConceptRateQueryPort conceptRateQueryPort, BenefitResolver benefitResolver, PaymentQuoter paymentQuoter,
			PaymentSettlementDecider paymentSettlementDecider, Clock clock) {
		return new ChangeChargeProgramUseCaseImpl(paymentRepository, paymentEntryRepository, paymentCreditRepository,
				paymentBenefitRepository, conceptQueryPort, conceptRateQueryPort, benefitResolver, paymentQuoter,
				paymentSettlementDecider, clock);
	}
}
