package ci.orange.subscription.infrastructure.persistence.entity;

import ci.orange.subscription.domain.client.ClientStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@SQLRestriction("deleted_at is null")
@Table(name = "client")
public class ClientEntity extends SoftDeletableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Setter(AccessLevel.NONE)
	private Long id;

	@Column(nullable = false, length = 100)
	private String firstName;

	@Column(nullable = false, length = 100)
	private String lastName;

	@Column(nullable = false, updatable = false)
	private String email;

	@Column(nullable = false, length = 10)
	private String phoneNumberClient;

	@Column(nullable = false, length = 100)
	private String city;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ClientStatus status;

}
