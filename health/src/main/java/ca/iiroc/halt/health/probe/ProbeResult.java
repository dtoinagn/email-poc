package ca.iiroc.halt.health.probe;

/**
 * @param detail why the check failed; it is returned by the unauthenticated status endpoint, so it must
 *               not contain URLs or response bodies
 */
public record ProbeResult(boolean up, String detail) {

    public static ProbeResult healthy() {
        return new ProbeResult(true, null);
    }

    public static ProbeResult unhealthy(String detail) {
        return new ProbeResult(false, detail);
    }
}
