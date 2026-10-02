package mx.edu.utez.sisa.academic_config.domain.model;

/**
 * Which row of a {@link PaymentRate} combination is the one in force.
 *
 * <p>
 * This replaces the {@code validFrom}/{@code validTo} date range the table used
 * to carry. The date range answered "is this price in force today?" by comparing
 * the current date against two columns, which meant a price could not be
 * scheduled or corrected without inventing dates, and an edit to the past was
 * indistinguishable from a future one. A status states the same thing directly:
 * exactly one {@code ACTIVE} row per {@code (conceptId, programId, level)} and
 * every other row is history.
 *
 * <p>
 * {@code INACTIVE} is never a soft delete of a price the catalog no longer
 * wants. It is how a superseded amount is recorded, and for a {@code
 * PERIODIC_QUOTA} concept it is also how a rate retires when its program stops
 * being ACTIVE — the row stays queryable, so reactivating the program restores
 * the last price instead of losing it.
 */
public enum PaymentRateStatus {
	ACTIVE,
	INACTIVE
}