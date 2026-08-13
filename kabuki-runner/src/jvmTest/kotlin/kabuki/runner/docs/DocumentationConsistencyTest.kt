package kabuki.runner.docs

import java.io.File
import kotlin.test.Test
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

        // A guard for the guard: a regex that stops matching would make this test
        // pass by checking nothing at all.
        assertTrue(mentions.isNotEmpty(), "No artifact coordinates found - has the format changed?")

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

        assertTrue(mentions.isNotEmpty(), "No source paths found in the docs - has the format changed?")

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
        return listOf(repoRoot.resolve("docs"), repoRoot.resolve(".claude"))
            .filter { dir -> dir.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { file -> file.extension == "md" }.toList() }
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
         * The documents are kept outside the repository for now, so a clean
         * checkout has nothing to check. That is a SKIP, not a pass: an empty
         * result must never look like a green guard.
         *
         * The `isNotEmpty` assertions below stay for the opposite case - documents
         * present, no matches found. That means the format changed and the regex
         * silently stopped matching, which is a real failure.
         */
        const val NO_DOCUMENTS = "Documentation is not part of this checkout"

        val ARTIFACT = Regex("io\\.github\\.kabukicompose:([a-z0-9-]+)")
        val PACKAGE = Regex("`(kabuki(?:\\.[a-z]+)+)`")
        val MODULE = Regex("\":([a-z0-9-]+)\"")
        val SOURCE_PATH = Regex("(?:kabuki-[a-z0-9-]+|samples)/src/[A-Za-z0-9_/.-]+\\.(?:kt|pro)")
    }
}
