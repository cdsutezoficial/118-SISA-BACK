package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /payment-areas}. Both {@code name} and
 * {@code code} are unique business keys (Fase 11).
 *
 * <p>Los tres mensajes de {@code name}/{@code code} van explícitos porque el
 * {@code @NotBlank} pelado de Spring ("must not be blank") se cuela en el
 * {@code message} del 400 y no es copy de esta aplicación.
 *
 * <ul>
 * <li>{@code name} — texto libre, como un canal de difusión
 * ({@code CreateOutreachChannelRequest}): se rechazan sólo los caracteres de
 * control, porque un área de pago se nombra como la institución quiera y hay
 * nombres con dígitos o signos —"Cuotas 2026", "Servicios miscellaneous"— que un
 * patrón de letras rechazaría sin que ningún documento del dominio lo pida. El
 * {@code @Size(max = 150)} es el mismo techo que
 * {@code CreateAcademicDivisionRequest.name}, {@code CreateOutreachChannelRequest}
 * y {@code CreateHighSchoolTypeRequest}: es un nombre de catálogo y 150 sobra.
 * Los {@code length} de la entidad deben coincidir.</li>
 * <li>{@code code} — <b>2 a 5 alfanuméricos en mayúscula</b>, regla de negocio
 * confirmada el 2026-10-05. Es la única diferencia de formato frente a los
 * catálogos hermanos: {@code AcademicDivision} usa letras Unicode
 * ({@code ^[\p{L}]+$}), y esta admite dígitos porque las claves de área lo hacen
 * (ej. {@code INS1}). El patrón es <b>ancorado y con clase explícita</b>
 * ({@code ^...$}), sin {@code \w}: {@code \w} incluye la línea nueva al final
 * con {@link Pattern#MULTILINE} y, sobre todo, no es lo que dice la regla.</li>
 * </ul>
 *
 * <p><b>El {@code @Pattern} exige mayúscula y el normalizador también las
 * pone</b>, y no es redundante: este se evalúa en el controller <b>antes</b> que
 * {@code PaymentAreaTextNormalizer}. El navegador nunca ve el 400 por teclear en
 * minúscula porque el frontend sube el valor ya normalizado con
 * {@code normalizeCode} ({@code trim().toUpperCase()}). Un cliente de API que
 * mande {@code "col"} en minúscula sí recibe un 400 con este mensaje, que es lo
 * que quiere el contrato: la clave guardada es siempre mayúscula y el que llama
 * sabe que el formato es explícito. Para aceptarlo en minúscula y normalizarlo
 * aquí, el patrón tendría que ser {@code ^[A-Za-z0-9]{2,5}$} — decisión que no se
 * tomó a propósito, porque entonces el {@code code} deja de describir la clave
 * que se guarda.
 *
 * <p>Los {@code @Size(min/max)} de {@code code} van además del {@code @Pattern}
 * para que el mensaje sea concreto ("entre 2 y 5") en vez de un genérico de
 * formato cuando el problema es sólo de longitud.
 *
 * <p>Ojo con el orden respecto al nombre: el patrón de {@code name} tiene que
 * aceptar todo lo que el normalizador arregla, y {@code ^[^\p{Cc}]*$} lo hace —
 * el espacio duro (U+00A0) y el BOM (U+FEFF) no son caracteres de control, sólo
 * whitespace, y los compacta el normalizador en vez de rechazarlos aquí.
 *
 * <p><b>{@code description} no lleva {@code @Size}</b>, a diferencia de
 * {@code CreateAcademicDivisionRequest.description} ({@code max = 500}). Es
 * deliberado: la columna es {@code TEXT} y el {@code TextAreaField} del
 * frontend no tiene {@code maxLength}, así que un techo puesto sólo aquí
 * rechazaría con 400 la edición de un área cuya descripción ya fuera más larga,
 * sin que ningún documento pida ese límite. Sólo se rechazan los caracteres de
 * control, que no tienen lectura en pantalla. Si negocio quiere un tope, tiene
 * que fijarlo en los dos lados a la vez.
 *
 * <p>Cada campo lleva un único {@code @Pattern} y no un {@code @Pattern.List}:
 * sobre los componentes de un {@code record} la versión repetible se propaga
 * también como anotación de tipo, y Hibernate Validator la encuentra duplicada y
 * lanza {@code AnnotationFormatError} (500 en lugar de 400).
 */
public record CreatePaymentAreaRequest(
		@NotBlank(message = "El nombre del área es obligatorio.") @Size(max = 150, message = "El nombre del área no puede superar 150 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "El nombre del área contiene caracteres no válidos.") String name,
		@NotBlank(message = "La clave del área es obligatoria.") @Size(min = 2, max = 5, message = "La clave del área debe tener entre 2 y 5 caracteres.") @Pattern(regexp = "^[A-Z0-9]{2,5}$", message = "La clave del área solo puede contener letras mayúsculas y números.") String code,
		@Pattern(regexp = "^[^\\p{Cc}]*$", message = "La descripción contiene caracteres no válidos.") String description) {
}
