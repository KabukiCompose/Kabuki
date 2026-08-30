package kabuki.runner.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

/**
 * Guards what is written ABOUT the library against the library itself: artifact
 * coordinates and package names in the docs and in the migration skill.
 *
 * Why a test and not discipline: a wrong artifact name in the skill breaks no
 * build here - it breaks a stranger's project, because the skill GENERATES code
 * into it. It happened once, with `com.zhukovartemvl.kabuki:kabuki-dsl`, an
 * artifact that never existed.
 *
 * It lives in kabuki-runner only because there is no module for repository-wide
 * checks; it reads files, not classes.
 */
class DocumentationConsistencyTest {

    private val repoRoot: File = findRepoRoot()

    @Test
    fun everyArtifactMentionedInDocsIsARealModule() {
        val documents = markdownFiles()
        assumeTrue(NO_DOCUMENTS, documents.isNotEmpty())
        val modules = declaredModules()
        val mentions = documents.flatMap { file ->
            ARTIFACT.findAll(file.readText()).map { match -> file to match.groupValues[1] }
        }

        // A guard for the guard, checked on a known string rather than on whatever
        // the docs happen to contain: a regex that stopped matching would otherwise
        // make this test pass by checking nothing at all.
        assertTrue(
            ARTIFACT.containsMatchIn("io.github.kabukicompose:kabuki-core"),
            "The artifact regex no longer matches a known coordinate",
        )

        val unknown = mentions.filterNot { (_, artifact) -> artifact.withoutTargetSuffix() in modules }
        assertTrue(
            unknown.isEmpty(),
            "Documentation points at artifacts that do not exist:\n" +
                unknown.joinToString("\n") { (file, artifact) -> "  ${file.name}: $artifact" } +
                "\nDeclared modules: $modules",
        )
    }

    @Test
    fun everySourceFileMentionedInDocsExists() {
        val documents = markdownFiles()
        assumeTrue(NO_DOCUMENTS, documents.isNotEmpty())
        val mentions = documents.flatMap { file ->
            SOURCE_PATH.findAll(file.readText()).map { match -> file to match.value }
        }

        assertTrue(
            SOURCE_PATH.containsMatchIn("kabuki-core/src/commonMain/kotlin/kabuki/page/UiNode.kt"),
            "The source path regex no longer matches a known path",
        )

        // Abbreviated paths (".../Something.kt") fail here on purpose: a path that
        // cannot be checked is a path that quietly rots after the next move.
        val broken = mentions.filterNot { (_, path) -> repoRoot.resolve(path).exists() }
        assertTrue(
            broken.isEmpty(),
            "Documentation points at files that do not exist:\n" +
                broken.joinToString("\n") { (file, path) -> "  ${file.name}: $path" },
        )
    }

    @Test
    fun theRussianReadmeKeepsUpWithTheEnglishOne() {
        val (english, russian) = readmes()
        assumeTrue(NO_DOCUMENTS, english.isFile && russian.isFile)
        val englishText = english.readText()
        val russianText = russian.readText()

        // The translation is the first thing to rot, and the parts worth guarding
        // are the ones a reader copies: coordinates, and the list of sections.
        assertTrue(
            COORDINATE.containsMatchIn(englishText),
            "No artifact coordinates in README.md - format changed?",
        )
        assertEquals(
            COORDINATE.findAll(englishText).map { it.value }.toSet(),
            COORDINATE.findAll(russianText).map { it.value }.toSet(),
            "README.ru.md offers different artifact coordinates",
        )
        assertEquals(
            SECTION.findAll(englishText).count(),
            SECTION.findAll(russianText).count(),
            "README.ru.md has a different number of sections",
        )
    }

    @Test
    fun everyModuleNamedWithoutItsGroupIsRealToo() {
        val documents = markdownFiles()
        assumeTrue(NO_DOCUMENTS, documents.isNotEmpty())
        val modules = declaredModules()
        // Prose names a module without its group far more often than with it, and
        // that is how an invented `kabuki-interop-junit4` once lived in the
        // migration skill - invisible to the coordinate check above, the very
        // check that exists to stop names like it.
        val mentions = documents.flatMap { file ->
            BARE_MODULE.findAll(file.readText()).map { match -> file to match.value }
        }

        assertTrue(
            BARE_MODULE.containsMatchIn("add kabuki-runner to commonTest"),
            "The bare module regex no longer matches a known name",
        )

        val unknown = mentions.filterNot { (_, name) -> name.withoutTargetSuffix() in modules }.distinct()
        assertTrue(
            unknown.isEmpty(),
            "Documentation names modules that do not exist: " +
                unknown.joinToString { (file, name) -> "${file.name}: $name" },
        )
    }

    @Test
    fun everyKabukiPackageMentionedInTheSkillExists() {
        val skill = skillFile()
        assumeTrue("The migration skill is not part of this checkout", skill.isFile)
        val sources = repoRoot.resolve("kabuki-core/src/commonMain/kotlin")
        val mentioned = PACKAGE.findAll(skill.readText())
            .map { match -> match.groupValues[1] }
            .distinct()
            .toList()

        assertTrue(mentioned.isNotEmpty(), "No kabuki.* packages found in the skill - format changed?")

        val missing = mentioned.filterNot { pkg -> sources.resolve(pkg.replace('.', '/')).isDirectory }
        assertTrue(
            missing.isEmpty(),
            "The migration skill imports from packages that do not exist: $missing",
        )
    }

    @Test
    fun theSkillDoesNotOfferTheOldGroupId() {
        assumeTrue("The migration skill is not part of this checkout", skillFile().isFile)
        // The docs may mention it as history; the skill must not, it writes code.
        assertTrue(
            "com.zhukovartemvl" !in skillFile().readText(),
            "The migration skill still offers the pre-2026-08-09 groupId",
        )
    }

    private fun String.withoutTargetSuffix(): String {
        return removeSuffix("-jvm").removeSuffix("-android")
    }

    private fun declaredModules(): Set<String> {
        val settings = repoRoot.resolve("settings.gradle.kts").readText()
        return MODULE.findAll(settings).map { match -> match.groupValues[1] }.toSet()
    }

    private fun markdownFiles(): List<File> {
        val fromDirectories = listOf(repoRoot.resolve("docs"), repoRoot.resolve(".claude"))
            .filter { dir -> dir.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { file -> file.extension == "md" }.toList() }
        // Only the English one is added by hand - the translation lives in docs/
        // and arrives with the walk above.
        return listOf(repoRoot.resolve("README.md")).filter { file -> file.isFile } + fromDirectories
    }

    private fun readmes(): List<File> {
        return listOf(repoRoot.resolve("README.md"), repoRoot.resolve("docs/README.ru.md"))
    }

    private fun skillFile(): File {
        return repoRoot.resolve(".claude/skills/migrate-to-kabuki/SKILL.md")
    }

    private fun findRepoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !dir.resolve("settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return checkNotNull(dir) { "Repository root not found from ${File("").absolutePath}" }
    }

    private companion object {
        /**
         * Part of the documentation is kept outside the repository, so a clean
         * checkout may have less to check. That is a SKIP, not a pass: an empty
         * result must never look like a green guard.
         *
         * The `isNotEmpty` assertions below stay for the opposite case - documents
         * present, no matches found. That means the format changed and the regex
         * silently stopped matching, which is a real failure.
         */
        const val NO_DOCUMENTS = "Documentation is not part of this checkout"

        val ARTIFACT = Regex("io\\.github\\.kabukicompose:([a-z0-9-]+)")
        val BARE_MODULE = Regex("\\bkabuki-[a-z0-9-]+")
        val COORDINATE = Regex("io\\.github\\.kabukicompose:[a-z0-9-]+:[0-9A-Za-z.-]+")
        val PACKAGE = Regex("`(kabuki(?:\\.[a-z]+)+)`")
        val MODULE = Regex("\":([a-z0-9-]+)\"")
        val SECTION = Regex("^## ", RegexOption.MULTILINE)
        val SOURCE_PATH = Regex("(?:kabuki-[a-z0-9-]+|samples/[a-z0-9-]+)/src/[A-Za-z0-9_/.-]+\\.(?:kt|pro)")
    }
}
