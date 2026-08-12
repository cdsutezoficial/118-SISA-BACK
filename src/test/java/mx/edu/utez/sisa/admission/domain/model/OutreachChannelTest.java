package mx.edu.utez.sisa.admission.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutreachChannelTest {

	@Test
	void constructor_defaultsToActiveStatus() {
		OutreachChannel channel = newChannel();

		assertThat(channel.getStatus()).isEqualTo(OutreachChannelStatus.ACTIVE);
	}

	@Test
	void constructor_setsName() {
		OutreachChannel channel = newChannel();

		assertThat(channel.getName()).isEqualTo("Facebook");
	}

	@Test
	void updateDetails_changesName() {
		OutreachChannel channel = newChannel();

		channel.updateDetails("Feria educativa");

		assertThat(channel.getName()).isEqualTo("Feria educativa");
	}

	@Test
	void deactivate_transitionsAnActiveChannelToInactive() {
		OutreachChannel channel = newChannel();

		channel.deactivate();

		assertThat(channel.getStatus()).isEqualTo(OutreachChannelStatus.INACTIVE);
	}

	@Test
	void deactivate_isIdempotentOnAnAlreadyInactiveChannel() {
		OutreachChannel channel = newChannel();
		channel.deactivate();

		channel.deactivate();

		assertThat(channel.getStatus()).isEqualTo(OutreachChannelStatus.INACTIVE);
	}

	@Test
	void activate_transitionsAnInactiveChannelToActive() {
		OutreachChannel channel = newChannel();
		channel.deactivate();

		channel.activate();

		assertThat(channel.getStatus()).isEqualTo(OutreachChannelStatus.ACTIVE);
	}

	@Test
	void activate_isIdempotentOnAnAlreadyActiveChannel() {
		OutreachChannel channel = newChannel();

		channel.activate();

		assertThat(channel.getStatus()).isEqualTo(OutreachChannelStatus.ACTIVE);
	}

	private OutreachChannel newChannel() {
		return new OutreachChannel("Facebook");
	}
}
