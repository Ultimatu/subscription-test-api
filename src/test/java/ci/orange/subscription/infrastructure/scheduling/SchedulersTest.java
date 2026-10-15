package ci.orange.subscription.infrastructure.scheduling;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ci.orange.subscription.application.port.in.client.ClientUseCases;
import ci.orange.subscription.application.port.in.notification.NotificationUseCases;
import org.junit.jupiter.api.Test;

class SchedulersTest {

	@Test
	void dispatchesNotificationsBatchAfterBatchUntilNoneIsDue() {
		NotificationUseCases notifications = mock(NotificationUseCases.class);
		when(notifications.dispatchDue()).thenReturn(20, 20, 3, 0);

		new NotificationDispatchScheduler(notifications).dispatch();

		verify(notifications, times(4)).dispatchDue();
	}

	@Test
	void purgesTheClientsWhoseRetentionPeriodIsOver() {
		ClientUseCases clients = mock(ClientUseCases.class);
		when(clients.purgeExpired()).thenReturn(2);

		new RetentionPurgeScheduler(clients).purgeExpired();

		verify(clients).purgeExpired();
	}

}
