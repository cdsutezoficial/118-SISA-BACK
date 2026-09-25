package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetConfigurationStatisticsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.ConfigurationStatisticsRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Interactor for {@link GetConfigurationStatisticsUseCase}. Simple
 * read aggregation — framework-agnostic (no stereotype annotations), wired in
 * {@code UseCaseConfig} with its two out-ports.
 */
public class GetConfigurationStatisticsUseCaseImpl implements GetConfigurationStatisticsUseCase {

	/**
	 * "Most recent" ordering used to pick the current period: higher
	 * {@code year} first, then higher {@code periodNumber}.
	 */
	private static final Comparator<AcademicPeriod> MOST_RECENT =
			Comparator.comparingInt(AcademicPeriod::getYear)
					.thenComparingInt(AcademicPeriod::getPeriodNumber);

	private final ConfigurationStatisticsRepository statisticsRepository;

	private final AcademicPeriodRepository periodRepository;

	public GetConfigurationStatisticsUseCaseImpl(ConfigurationStatisticsRepository statisticsRepository,
			AcademicPeriodRepository periodRepository) {
		this.statisticsRepository = statisticsRepository;
		this.periodRepository = periodRepository;
	}

	@Override
	public GetConfigurationStatisticsResult getStatistics() {
		Optional<AcademicPeriod> current = currentPeriod();
		long groups = current.map(period -> statisticsRepository.countGroupsForPeriod(period.getId())).orElse(0L);
		CurrentPeriodStatistics period = current.map(
				p -> new CurrentPeriodStatistics(p.getId(), p.getName())).orElse(null);
		return new GetConfigurationStatisticsResult(statisticsRepository.countDivisions(),
				statisticsRepository.countPrograms(), statisticsRepository.countSubjects(), groups, period);
	}

	/**
	 * The dashboard's "current period": the {@link PeriodStatus#ACTIVE} one if
	 * it exists (there is at most one, enforced by the period lifecycle but
	 * taken as max anyway), otherwise the most recent period configured.
	 */
	private Optional<AcademicPeriod> currentPeriod() {
		List<AcademicPeriod> periods = periodRepository.findAll();
		return periods.stream().filter(period -> period.getStatus() == PeriodStatus.ACTIVE).max(MOST_RECENT)
				.or(() -> periods.stream().max(MOST_RECENT));
	}
}