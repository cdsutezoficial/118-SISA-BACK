package mx.edu.utez.sisa.identity.infrastructure.bootstrap;

import mx.edu.utez.sisa.identity.domain.model.Permission;
import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.domain.model.RolePermission;
import mx.edu.utez.sisa.identity.domain.port.out.PermissionRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RolePermissionRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@Order(0)
public class IdentityAuthorizationCatalogSeedRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(IdentityAuthorizationCatalogSeedRunner.class);

	private static final List<RoleSeed> DEFAULT_ROLES = List.of(
			new RoleSeed(RoleType.ADMIN, "Administrador", "Acceso total al sistema."),
			new RoleSeed(RoleType.SERVICIOS_ESCOLARES, "Servicios Escolares",
					"Gestión operativa de usuarios y catálogos académicos autorizados."),
			new RoleSeed(RoleType.GESTOR_ACADEMICO, "Gestor Académico",
					"Seguimiento operativo académico con alcance por división."),
			new RoleSeed(RoleType.DIRECTOR_DIVISION, "Director de División",
					"Supervisión académica y operativa de una división."),
			new RoleSeed(RoleType.JEFATURA_ESTADIAS, "Jefatura de Estadías",
					"Gestión institucional del proceso de estadías."),
			new RoleSeed(RoleType.ASISTENTE_ESTADIAS, "Asistente de Estadías",
					"Apoyo operativo al proceso de estadías."),
			new RoleSeed(RoleType.COORDINACION_ESTADIAS_DIVISION, "Coordinación de Estadías por División",
					"Coordinación del proceso de estadías dentro de una división."),
			new RoleSeed(RoleType.PERSONAL_FINANZAS, "Personal de Finanzas",
					"Administración de catálogos y operaciones del módulo financiero."),
			new RoleSeed(RoleType.DOCENTE, "Docente", "Acceso docente a procesos académicos autorizados."),
			new RoleSeed(RoleType.ESTUDIANTE, "Estudiante", "Acceso del estudiante a su experiencia escolar."),
			new RoleSeed(RoleType.EGRESADO, "Egresado", "Acceso del egresado a funcionalidades habilitadas."));

	private static final List<PermissionSeed> DEFAULT_PERMISSIONS = List.of(
			permission("ROLES_READ", "Consultar roles"),
			permission("ROLES_CREATE", "Crear rol"),
			permission("ROLES_UPDATE", "Actualizar rol"),
			permission("ROLES_CHANGE_STATUS", "Cambiar estatus de rol"),
			permission("ROLES_ASSIGN_PERMISSIONS", "Asignar permisos a rol"),
			permission("PERMISSIONS_READ", "Consultar permisos"),
			permission("PERMISSIONS_CREATE", "Crear permiso"),
			permission("PERMISSIONS_UPDATE", "Actualizar permiso"),
			permission("PERMISSIONS_CHANGE_STATUS", "Cambiar estatus de permiso"),
			permission("USERS_READ", "Consultar usuarios"),
			permission("USERS_CREATE", "Crear usuario"),
			permission("USERS_ASSIGN_ROLE", "Asignar rol a usuario"),
			permission("USERS_REVOKE_ROLE", "Revocar rol de usuario"),
			permission("USERS_UNLOCK", "Desbloquear usuario"),
			permission("PERSONS_READ", "Consultar personas"),
			permission("PERSONS_CREATE", "Crear persona"),
			permission("DIVISIONS_READ", "Consultar divisiones"),
			permission("DIVISIONS_CREATE", "Crear división"),
			permission("DIVISIONS_UPDATE", "Actualizar división"),
			permission("DIVISIONS_CHANGE_STATUS", "Cambiar estatus de división"),
			permission("PROGRAMS_READ", "Consultar programas"),
			permission("PROGRAMS_CREATE", "Crear programa"),
			permission("PROGRAMS_UPDATE", "Actualizar programa"),
			permission("PROGRAMS_CHANGE_STATUS", "Cambiar estatus de programa"),
			permission("PLANS_READ", "Consultar planes"),
			permission("PLANS_CREATE", "Crear plan"),
			permission("PLANS_UPDATE", "Actualizar plan"),
			permission("PLANS_CHANGE_STATUS", "Cambiar estatus de plan"),
			permission("PLANS_DELETE", "Eliminar componente de plan"),
			permission("SUBJECT_CLASSIFICATIONS_READ", "Consultar clasificaciones de materia"),
			permission("SUBJECT_CLASSIFICATIONS_CREATE", "Crear clasificación de materia"),
			permission("SUBJECT_CLASSIFICATIONS_UPDATE", "Actualizar clasificación de materia"),
			permission("SUBJECT_CLASSIFICATIONS_CHANGE_STATUS", "Cambiar estatus de clasificación de materia"),
			permission("PERIODS_READ", "Consultar periodos"),
			permission("PERIODS_CREATE", "Crear periodo"),
			permission("PERIODS_ADVANCE_BY_DATE", "Avanzar periodo por fecha"),
			permission("PERIODS_UPDATE", "Actualizar periodo"),
			permission("PERIODS_CHANGE_STATUS", "Cambiar estatus de periodo"),
			permission("GENERATIONS_READ", "Consultar generaciones"),
			permission("GENERATIONS_CREATE", "Crear generación"),
			permission("GENERATIONS_UPDATE", "Actualizar generación"),
			permission("GENERATIONS_CHANGE_STATUS", "Cambiar estatus de generación"),
			permission("GROUPS_READ", "Consultar grupos"),
			permission("GROUPS_CREATE", "Crear grupo"),
			permission("GROUPS_UPDATE", "Actualizar grupo"),
			permission("GROUPS_CHANGE_STATUS", "Cambiar estatus de grupo"),
			permission("PAYMENT_CONCEPTS_READ", "Consultar conceptos de pago"),
			permission("PAYMENT_CONCEPTS_CREATE", "Crear concepto de pago"),
			permission("PAYMENT_CONCEPTS_UPDATE", "Actualizar concepto de pago"),
			permission("PAYMENT_CONCEPTS_CHANGE_STATUS", "Cambiar estatus de concepto de pago"),
			permission("PAYMENT_RATES_CREATE", "Crear tarifa de concepto de pago"),
			permission("PAYMENT_AREAS_READ", "Consultar áreas de facturación"),
			permission("PAYMENT_AREAS_CREATE", "Crear área de facturación"),
			permission("PAYMENT_AREAS_UPDATE", "Actualizar área de facturación"),
			permission("PAYMENT_AREAS_CHANGE_STATUS", "Cambiar estatus de área de facturación"),
			permission("PROGRAM_ADMISSION_CONFIGS_READ", "Consultar configuraciones de admisión por programa"),
			permission("PROGRAM_ADMISSION_CONFIGS_CREATE", "Crear configuración de admisión por programa"),
			permission("PROGRAM_ADMISSION_CONFIGS_UPDATE", "Actualizar configuración de admisión por programa"),
			permission("PROGRAM_ADMISSION_CONFIGS_CHANGE_STATUS", "Cambiar estatus de configuración de admisión por programa"),
			permission("OUTREACH_CHANNELS_READ", "Consultar canales de difusión"),
			permission("OUTREACH_CHANNELS_CREATE", "Crear canal de difusión"),
			permission("OUTREACH_CHANNELS_UPDATE", "Actualizar canal de difusión"),
			permission("OUTREACH_CHANNELS_CHANGE_STATUS", "Cambiar estatus de canal de difusión"),
			permission("HIGH_SCHOOL_TYPES_READ", "Consultar tipos de bachillerato"),
			permission("HIGH_SCHOOL_TYPES_CREATE", "Crear tipo de bachillerato"),
			permission("HIGH_SCHOOL_TYPES_UPDATE", "Actualizar tipo de bachillerato"),
			permission("HIGH_SCHOOL_TYPES_CHANGE_STATUS", "Cambiar estatus de tipo de bachillerato"),
			permission("STATES_READ", "Consultar estados"),
			permission("MUNICIPALITIES_READ", "Consultar municipios"));

	private static final Map<RoleType, Set<String>> DEFAULT_PERMISSION_KEYS_BY_ROLE = defaultPermissionsByRole();

	private final RoleRepository roleRepository;
	private final PermissionRepository permissionRepository;
	private final RolePermissionRepository rolePermissionRepository;

	public IdentityAuthorizationCatalogSeedRunner(RoleRepository roleRepository,
			PermissionRepository permissionRepository, RolePermissionRepository rolePermissionRepository) {
		this.roleRepository = roleRepository;
		this.permissionRepository = permissionRepository;
		this.rolePermissionRepository = rolePermissionRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		Map<String, Role> rolesByKey = seedRoles();
		Map<String, Permission> permissionsByKey = seedPermissions();
		seedRolePermissions(rolesByKey, permissionsByKey);
		log.info("Identity authorization catalog initialized: {} roles, {} permissions", rolesByKey.size(),
				permissionsByKey.size());
	}

	private Map<String, Role> seedRoles() {
		Map<String, Role> rolesByKey = new LinkedHashMap<>();
		for (RoleSeed seed : DEFAULT_ROLES) {
			Role role = roleRepository.findByKey(seed.key()).orElseGet(() -> roleRepository
					.save(new Role(seed.name(), seed.key(), seed.description())));
			rolesByKey.put(role.getKey(), role);
		}
		return rolesByKey;
	}

	private Map<String, Permission> seedPermissions() {
		Map<String, Permission> permissionsByKey = new LinkedHashMap<>();
		for (PermissionSeed seed : DEFAULT_PERMISSIONS) {
			Permission permission = permissionRepository.findByKey(seed.key())
					.orElseGet(() -> permissionRepository.save(new Permission(seed.name(), seed.key())));
			permissionsByKey.put(permission.getKey(), permission);
		}
		return permissionsByKey;
	}

	private void seedRolePermissions(Map<String, Role> rolesByKey, Map<String, Permission> permissionsByKey) {
		for (Map.Entry<RoleType, Set<String>> entry : DEFAULT_PERMISSION_KEYS_BY_ROLE.entrySet()) {
			Role role = rolesByKey.get(entry.getKey().name());
			if (role == null) {
				continue;
			}

			Set<UUID> existingPermissionIds = rolePermissionRepository.findByRoleId(role.getId()).stream()
					.map(RolePermission::getPermissionId)
					.collect(java.util.stream.Collectors.toSet());
			List<RolePermission> missingAssignments = entry.getValue().stream().map(permissionsByKey::get)
					.filter(permission -> permission != null && !existingPermissionIds.contains(permission.getId()))
					.map(permission -> new RolePermission(role.getId(), permission.getId())).toList();
			if (!missingAssignments.isEmpty()) {
				rolePermissionRepository.saveAll(missingAssignments);
			}
		}
	}

	private static PermissionSeed permission(String key, String name) {
		return new PermissionSeed(key, name);
	}

	private static Map<RoleType, Set<String>> defaultPermissionsByRole() {
		Map<RoleType, Set<String>> permissionsByRole = new EnumMap<>(RoleType.class);
		Set<String> allPermissions = DEFAULT_PERMISSIONS.stream().map(PermissionSeed::key)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		permissionsByRole.put(RoleType.ADMIN, allPermissions);

		permissionsByRole.put(RoleType.SERVICIOS_ESCOLARES, orderedSet(
				"USERS_READ", "PERSONS_READ", "DIVISIONS_READ", "DIVISIONS_CREATE", "DIVISIONS_UPDATE",
				"DIVISIONS_CHANGE_STATUS", "PROGRAMS_READ", "PROGRAMS_CREATE", "PROGRAMS_UPDATE",
				"PROGRAMS_CHANGE_STATUS", "PLANS_READ", "PLANS_CREATE", "PLANS_UPDATE",
				"PLANS_CHANGE_STATUS", "PLANS_DELETE", "SUBJECT_CLASSIFICATIONS_READ",
				"SUBJECT_CLASSIFICATIONS_CREATE", "SUBJECT_CLASSIFICATIONS_UPDATE",
				"SUBJECT_CLASSIFICATIONS_CHANGE_STATUS", "PERIODS_READ", "PERIODS_CREATE",
				"PERIODS_ADVANCE_BY_DATE", "PERIODS_UPDATE", "PERIODS_CHANGE_STATUS", "GENERATIONS_READ",
				"GENERATIONS_CREATE", "GENERATIONS_UPDATE", "GENERATIONS_CHANGE_STATUS", "GROUPS_READ",
				"GROUPS_CREATE", "GROUPS_UPDATE", "GROUPS_CHANGE_STATUS",
				"PROGRAM_ADMISSION_CONFIGS_READ", "PROGRAM_ADMISSION_CONFIGS_CREATE",
				"PROGRAM_ADMISSION_CONFIGS_UPDATE", "PROGRAM_ADMISSION_CONFIGS_CHANGE_STATUS",
				"OUTREACH_CHANNELS_READ", "OUTREACH_CHANNELS_CREATE", "OUTREACH_CHANNELS_UPDATE",
				"OUTREACH_CHANNELS_CHANGE_STATUS", "HIGH_SCHOOL_TYPES_READ", "HIGH_SCHOOL_TYPES_CREATE",
				"HIGH_SCHOOL_TYPES_UPDATE", "HIGH_SCHOOL_TYPES_CHANGE_STATUS", "STATES_READ",
				"MUNICIPALITIES_READ"));

		permissionsByRole.put(RoleType.PERSONAL_FINANZAS, orderedSet("PAYMENT_CONCEPTS_READ",
				"PAYMENT_CONCEPTS_CREATE", "PAYMENT_CONCEPTS_UPDATE", "PAYMENT_CONCEPTS_CHANGE_STATUS",
				"PAYMENT_RATES_CREATE", "PAYMENT_AREAS_READ", "PAYMENT_AREAS_CREATE", "PAYMENT_AREAS_UPDATE",
				"PAYMENT_AREAS_CHANGE_STATUS", "STATES_READ", "MUNICIPALITIES_READ"));

		permissionsByRole.put(RoleType.GESTOR_ACADEMICO, orderedSet("STATES_READ", "MUNICIPALITIES_READ"));
		permissionsByRole.put(RoleType.DIRECTOR_DIVISION, orderedSet("STATES_READ", "MUNICIPALITIES_READ"));
		permissionsByRole.put(RoleType.JEFATURA_ESTADIAS, orderedSet("STATES_READ", "MUNICIPALITIES_READ"));
		permissionsByRole.put(RoleType.ASISTENTE_ESTADIAS, orderedSet("STATES_READ", "MUNICIPALITIES_READ"));
		permissionsByRole.put(RoleType.COORDINACION_ESTADIAS_DIVISION,
				orderedSet("STATES_READ", "MUNICIPALITIES_READ"));
		permissionsByRole.put(RoleType.DOCENTE, orderedSet("STATES_READ", "MUNICIPALITIES_READ"));
		permissionsByRole.put(RoleType.ESTUDIANTE, orderedSet("STATES_READ", "MUNICIPALITIES_READ"));
		permissionsByRole.put(RoleType.EGRESADO, orderedSet("STATES_READ", "MUNICIPALITIES_READ"));
		return permissionsByRole;
	}

	private static Set<String> orderedSet(String... values) {
		return new LinkedHashSet<>(List.of(values));
	}

	private record RoleSeed(RoleType roleType, String name, String description) {
		private String key() {
			return roleType.name();
		}
	}

	private record PermissionSeed(String key, String name) {
	}
}