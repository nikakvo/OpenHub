package com.thirtydegreesray.openhub.util;

/**
 * Where secrets and variables of a repository live, as API addresses (relative to
 * api.github.com) and github.com settings pages. A scope is "actions" (the repository),
 * "dependabot" (secrets only) or "env:NAME" (an environment). Pure Java, unit tested.
 */
public final class ActionsScopes {

    public static final String ACTIONS = "actions";
    public static final String DEPENDABOT = "dependabot";
    private static final String ENV = "env:";

    private ActionsScopes() {
    }

    public static String environment(String name) {
        return ENV + name;
    }

    public static boolean isEnvironment(String scope) {
        return scope != null && scope.startsWith(ENV) && scope.length() > ENV.length();
    }

    /** The environment's name, or null for the other scopes. */
    public static String environmentName(String scope) {
        return isEnvironment(scope) ? scope.substring(ENV.length()) : null;
    }

    /** "repos/o/r/actions", "repos/o/r/dependabot" or "repos/o/r/environments/NAME" */
    public static String base(String owner, String repo, String scope) {
        String root = "repos/" + owner + "/" + repo + "/";
        if (isEnvironment(scope)) {
            return root + "environments/" + GitHubLinks.encodeSegment(environmentName(scope));
        }
        return root + (DEPENDABOT.equals(scope) ? DEPENDABOT : ACTIONS);
    }

    public static String environmentsUrl(String owner, String repo) {
        return "repos/" + owner + "/" + repo + "/environments";
    }

    public static String secrets(String base) {
        return base + "/secrets";
    }

    public static String publicKey(String base) {
        return base + "/secrets/public-key";
    }

    public static String secret(String base, String name) {
        return base + "/secrets/" + GitHubLinks.encodeSegment(name);
    }

    public static String variables(String base) {
        return base + "/variables";
    }

    public static String variable(String base, String name) {
        return base + "/variables/" + GitHubLinks.encodeSegment(name);
    }

    /** github.com page with the same list. */
    public static String settingsPage(String owner, String repo, String scope, boolean variables) {
        String root = "https://github.com/" + owner + "/" + repo + "/settings/";
        if (isEnvironment(scope)) return root + "environments";
        if (DEPENDABOT.equals(scope)) return root + "secrets/dependabot";
        return root + (variables ? "variables/actions" : "secrets/actions");
    }
}
