// SPDX-License-Identifier: MIT
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class Version {

    private static final Path CATALOG_FILE = Path.of("gradle", "libs.versions.toml");
    private static final Path README_FILE = Path.of("README.md");
    private static final Pattern DECLARATION =
            Pattern.compile("(?m)^nohorny-natives = \"(?<version>(?<major>\\d+)\\.(?<minor>\\d+)\\.\\d+)\"$");
    private static final Pattern DEPENDENCY = Pattern.compile("(com\\.xpdustry:nohorny-natives:)[^\"]+");

    public static void main(final String[] args) throws Exception {
        if (args.length != 1 || !(args[0].equals("get") || args[0].equals("bump"))) {
            System.err.println("::error::Usage: Version.java <get|bump>");
            System.exit(1);
        }

        final var content = Files.readString(CATALOG_FILE);
        final var matches = DECLARATION.matcher(content).results().toList();
        if (matches.size() != 1) {
            System.err.println(
                    "::error::Expected exactly one project version matching '" + DECLARATION + "' in " + CATALOG_FILE);
            System.exit(1);
        }

        final var declaration = matches.getFirst();
        if (args[0].equals("get")) {
            System.out.println(declaration.group("version"));
            return;
        }

        final var released = declaration.group("version");
        final var next = declaration.group("major") + "." + (Integer.parseInt(declaration.group("minor")) + 1) + ".0";

        final var readme = Files.readString(README_FILE);
        if (!DEPENDENCY.matcher(readme).find()) {
            System.err.println("::error::Expected the dependency matching '" + DEPENDENCY + "' in " + README_FILE);
            System.exit(1);
        }
        final var bumpedCatalog = content.replace(declaration.group(), "nohorny-natives = \"" + next + "\"");
        final var bumpedReadme = DEPENDENCY.matcher(readme).replaceAll("$1" + released);

        Files.writeString(CATALOG_FILE, bumpedCatalog);
        Files.writeString(README_FILE, bumpedReadme);
        System.out.println(next);
    }
}
