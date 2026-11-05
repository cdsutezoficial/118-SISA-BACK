package mx.edu.utez.sisa.shared.persistence;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;

/**
 * {@link MySQLDialect} that maps {@code @Enumerated(EnumType.STRING)} to
 * {@code VARCHAR} instead of MySQL's native {@code ENUM('A','B',…)}.
 *
 * <p>The schema is owned by Flyway migrations (see
 * {@code docs/plans/2026-10-30-flyway-migrations.md}). With a native
 * {@code ENUM}, every new constant in a Java enum would need an
 * {@code ALTER TABLE … MODIFY} migration, and forgetting it fails only at
 * runtime ("Data truncated") because {@code ddl-auto=validate} does not
 * compare enum values. As {@code VARCHAR}, the domain can add states without
 * touching the schema. {@code hibernate.type.prefer_native_enum_types=false}
 * does not cover MySQL's inline enums, hence a dialect instead of a setting
 * or a {@code @JdbcTypeCode} on every enum field.
 */
public class SisaMySQLDialect extends MySQLDialect {

	@Override
	public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
		super.contributeTypes(typeContributions, serviceRegistry);
		typeContributions.getTypeConfiguration()
				.getJdbcTypeRegistry()
				.addDescriptor(SqlTypes.ENUM, VarcharJdbcType.INSTANCE);
	}
}
