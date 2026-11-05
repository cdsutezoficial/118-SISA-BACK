package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.ChangeCandidateProgramUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.RegisterCandidateCommand;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateCandidateUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.HighSchoolTypeNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.OutreachChannelNotFoundException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public class UpdateCandidateUseCaseImpl implements UpdateCandidateUseCase {

	private final CandidateRepository candidateRepository;

	private final OutreachChannelRepository outreachChannelRepository;

	private final HighSchoolTypeRepository highSchoolTypeRepository;

	private final ChangeCandidateProgramUseCase changeCandidateProgramUseCase;

	public UpdateCandidateUseCaseImpl(CandidateRepository candidateRepository,
			OutreachChannelRepository outreachChannelRepository, HighSchoolTypeRepository highSchoolTypeRepository,
			ChangeCandidateProgramUseCase changeCandidateProgramUseCase) {
		this.candidateRepository = candidateRepository;
		this.outreachChannelRepository = outreachChannelRepository;
		this.highSchoolTypeRepository = highSchoolTypeRepository;
		this.changeCandidateProgramUseCase = changeCandidateProgramUseCase;
	}

	@Override
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void update(UUID candidateId, RegisterCandidateCommand command, UUID actingUserId) {
		Candidate candidate = candidateRepository.findById(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException("No existe el candidato: " + candidateId));

		validate(command);
		CandidateFichaMapper.apply(candidate, command);
		candidate.updateSelection(command.seleccionCarrera().outreachChannelId(),
				command.seleccionCarrera().isFirstChoice());
		candidateRepository.save(candidate);

		changeCandidateProgramUseCase.change(candidateId, command.seleccionCarrera().admissionConfigId(), actingUserId);
	}

	private void validate(RegisterCandidateCommand command) {
		UUID channelId = command.seleccionCarrera().outreachChannelId();
		if (channelId != null && outreachChannelRepository.findById(channelId).isEmpty()) {
			throw new OutreachChannelNotFoundException("No existe el canal de difusión: " + channelId);
		}

		UUID schoolTypeId = command.antecedentesEscolares() == null ? null
				: command.antecedentesEscolares().schoolTypeId();
		if (schoolTypeId != null && highSchoolTypeRepository.findById(schoolTypeId).isEmpty()) {
			throw new HighSchoolTypeNotFoundException("No existe el tipo de preparatoria: " + schoolTypeId);
		}
	}
}
