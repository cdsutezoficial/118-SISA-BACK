package mx.edu.utez.sisa.admission.infrastructure.web;

import jakarta.validation.Valid;
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
import mx.edu.utez.sisa.admission.infrastructure.web.dto.ChangeOutreachChannelStatusRequest;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.CreateOutreachChannelRequest;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.OutreachChannelListItemResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.OutreachChannelListResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.OutreachChannelResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.UpdateOutreachChannelRequest;
import mx.edu.utez.sisa.shared.web.dto.OptionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Thin controller for {@code OutreachChannel} — first real endpoint of the
 * {@code admission} bounded context: {@code GET /outreach-channels}
 * (paginated), {@code POST /outreach-channels} (201),
 * {@code GET /outreach-channels/{id}} (404 if missing),
 * {@code PUT /outreach-channels/{id}} (404 if missing), and
 * {@code PATCH /outreach-channels/{id}/status} (404 if missing). Role
 * authorization (ADMIN or SERVICIOS_ESCOLARES) is enforced by
 * {@code identity.SecurityFilterConfig}'s {@code /outreach-channels}
 * matchers, not here.
 */
@RestController
@RequestMapping("/outreach-channels")
public class OutreachChannelController {

	private final ListOutreachChannelsUseCase listOutreachChannelsUseCase;

	private final CreateOutreachChannelUseCase createOutreachChannelUseCase;

	private final GetOutreachChannelUseCase getOutreachChannelUseCase;

	private final UpdateOutreachChannelUseCase updateOutreachChannelUseCase;

	private final ChangeOutreachChannelStatusUseCase changeOutreachChannelStatusUseCase;

	private final OutreachChannelJpaRepository outreachChannelJpaRepository;

	public OutreachChannelController(ListOutreachChannelsUseCase listOutreachChannelsUseCase,
			CreateOutreachChannelUseCase createOutreachChannelUseCase,
			GetOutreachChannelUseCase getOutreachChannelUseCase,
			UpdateOutreachChannelUseCase updateOutreachChannelUseCase,
			ChangeOutreachChannelStatusUseCase changeOutreachChannelStatusUseCase,
			OutreachChannelJpaRepository outreachChannelJpaRepository) {
		this.listOutreachChannelsUseCase = listOutreachChannelsUseCase;
		this.createOutreachChannelUseCase = createOutreachChannelUseCase;
		this.getOutreachChannelUseCase = getOutreachChannelUseCase;
		this.updateOutreachChannelUseCase = updateOutreachChannelUseCase;
		this.changeOutreachChannelStatusUseCase = changeOutreachChannelStatusUseCase;
		this.outreachChannelJpaRepository = outreachChannelJpaRepository;
	}

	@PostMapping
	public ResponseEntity<OutreachChannelResponse> createChannel(
			@Valid @RequestBody CreateOutreachChannelRequest request) {
		OutreachChannelResult result = createOutreachChannelUseCase
				.createChannel(new CreateOutreachChannelCommand(request.name()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
	}

	@PutMapping("/{id}")
	public ResponseEntity<OutreachChannelResponse> updateChannel(@PathVariable UUID id,
			@Valid @RequestBody UpdateOutreachChannelRequest request) {
		OutreachChannelResult result = updateOutreachChannelUseCase
				.updateChannel(new UpdateOutreachChannelCommand(id, request.name()));
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping("/options")
	public List<OptionResponse> listChannelOptions() {
		return outreachChannelJpaRepository.findByStatusOrderByNameAsc(OutreachChannelStatus.ACTIVE).stream()
				.map(c -> new OptionResponse(c.getId(), c.getName(), null)).toList();
	}

	@GetMapping("/{id}")
	public ResponseEntity<OutreachChannelResponse> getChannel(@PathVariable UUID id) {
		OutreachChannelResult result = getOutreachChannelUseCase.getById(id);
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping
	public ResponseEntity<OutreachChannelListResponse> listChannels(
			@RequestParam(required = false) OutreachChannelStatus status,
			@RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		ListOutreachChannelsResult result = listOutreachChannelsUseCase
				.listChannels(new ListOutreachChannelsQuery(status, search, page, size));
		return ResponseEntity.ok(new OutreachChannelListResponse(
				result.items().stream().map(OutreachChannelController::toItem).toList(), result.totalElements(),
				result.totalPages(), result.page(), result.size()));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<OutreachChannelResponse> changeStatus(@PathVariable UUID id,
			@Valid @RequestBody ChangeOutreachChannelStatusRequest request) {
		OutreachChannelResult result = changeOutreachChannelStatusUseCase
				.changeStatus(new ChangeStatusCommand(currentUserId(), id, request.status()));
		return ResponseEntity.ok(toResponse(result));
	}

	/**
	 * Extracts the acting user's id from the JWT principal
	 * ({@link mx.edu.utez.sisa.identity.infrastructure.security.JwtAuthenticationFilter}
	 * sets it to the token's {@code sub} claim) — same mechanism as
	 * {@code SubjectClassificationController#currentUserId}. Only consumed by
	 * {@link ChangeStatusCommand#callerId()}, which is reserved for future
	 * audit-log attribution and not read by the use case yet.
	 */
	private static UUID currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return UUID.fromString(authentication.getName());
	}

	private static OutreachChannelResponse toResponse(OutreachChannelResult result) {
		return new OutreachChannelResponse(result.id(), result.name(), result.status());
	}

	private static OutreachChannelListItemResponse toItem(OutreachChannelSummary summary) {
		return new OutreachChannelListItemResponse(summary.id(), summary.name(), summary.status());
	}
}
