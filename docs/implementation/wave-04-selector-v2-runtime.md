# Wave 04 Task 03 — Selector V2 Runtime Continuation Plan

Date: 2026-08-07  
Status: **ACTIVE**  
Canonical source state: `../project/current-state.md`

> **For agentic workers:** Continue only the runtime work below. The public
> Selector V2 contract work that the original reviewed plan called Tasks 1–3 is
> already present in this snapshot as Wave 03 remediation and must not be repeated.

## Goal

Finish Wave 04 Task 03 by executing installed Selector V2 endpoint definitions through
bounded host-owned networking/DOM/binding/mapping operations and returning every current
Catalog and Content wire DTO through shared validation, while preserving V1 behavior.

## Baseline present in this snapshot

Already present and treated as dependencies:

- `PluginManifest.declarativeOrigin` and its validation;
- `SelectorDefinitionDecoder` with separate V1/V2 models;
- concrete closed Selector V2 binding AST;
- Catalog V2 endpoint contracts + install-time validation;
- Content V2 endpoint contracts + install-time validation;
- deterministic Selector V2 fixture definitions;
- ZIP package inspection that decodes/validates selectors before activation;
- bounded Selector V1 host interpreter;
- allowlisted HTTP gateway;
- transactional installer/registry.

## Remaining reviewed sequence

The 2026-08-06 reviewed implementation plan numbered the remaining production work as
Tasks 4–8 and checkpoint work as Task 9. **Those original numbers are intentionally
retained below** so test/commit references remain traceable to the reviewed package.

The current sequence is therefore:

1. reviewed Task 4 — shared URL policy + shared final wire DTO validation;
2. reviewed Task 5 — bounded V2 binding evaluator + richer HTML adapter;
3. reviewed Task 6 — Catalog mapping;
4. reviewed Task 7 — Content mapping;
5. reviewed Task 8 — V2 Catalog/Content plugin adapters and selector factory;
6. reviewed Task 9 — Wave 04 Task 03 compatibility/security checkpoint.

## Important correction to the archived plan

Any command or expected commit-history snippet below that lists the old contract commits
should be interpreted against the actual branch history. The **technical acceptance and
file responsibilities remain authoritative**, but already-completed Wave 03 contract
work is not recreated merely to match an old commit sequence.

---

### Task 4: Extract the shared URL policy and add shared wire DTO validators

**Commit:** `plugin-host: add shared plugin output validation`

**Files:**

- Create: `core/network/src/main/kotlin/app/openstory/network/PluginUrlPolicy.kt`
- Modify: `core/network/src/main/kotlin/app/openstory/network/AllowlistedHttpGateway.kt`
- Create: `core/network/src/test/kotlin/app/openstory/network/PluginUrlPolicyTest.kt`
- Modify: `core/network/src/test/kotlin/app/openstory/network/AllowlistedHttpGatewayTest.kt`
- Create: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/validation/PluginOutputLimits.kt`
- Create: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/validation/PluginWireDtoValidator.kt`
- Create: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/validation/CatalogWireDtoValidator.kt`
- Create: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/validation/ContentWireDtoValidator.kt`
- Create: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/validation/ChapterDocumentValidator.kt`
- Test: `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/validation/PluginWireDtoValidatorTest.kt`

**Interfaces:**

```kotlin
data class ValidatedPluginUrl(
    val value: String,
    val host: String,
)

enum class PluginUrlRejection {
    INVALID_URL,
    INSECURE_SCHEME,
    UNDECLARED_HOST,
    USER_INFORMATION,
    MISSING_BASE_URL,
}

sealed interface PluginUrlDecision {
    data class Allowed(
        val value: ValidatedPluginUrl,
    ) : PluginUrlDecision

    data class Rejected(
        val reason: PluginUrlRejection,
    ) : PluginUrlDecision
}

interface PluginUrlPolicy {
    fun resolve(
        candidate: String,
        baseUrl: String? = null,
    ): PluginUrlDecision
}
```

`DefaultPluginUrlPolicy` is constructed with normalized allowed hosts and a test-only cleartext flag.

- [ ] **Step 1: write failing URL policy tests**

Create `PluginUrlPolicyTest.kt`:

```kotlin
@Test
fun relativeUrlResolvesAgainstDeclaredBaseWithoutNetwork() {
    val policy =
        DefaultPluginUrlPolicy(
            allowedHosts = setOf("allowed.example"),
        )

    val result =
        policy.resolve(
            candidate = "../chapter/2",
            baseUrl = "https://allowed.example/story/1/",
        )

    assertEquals(
        PluginUrlDecision.Allowed(
            ValidatedPluginUrl(
                value = "https://allowed.example/story/chapter/2",
                host = "allowed.example",
            ),
        ),
        result,
    )
}

@Test
fun crossHostUrlIsRejected() {
    val result =
        DefaultPluginUrlPolicy(
            allowedHosts = setOf("allowed.example"),
        ).resolve(
            "https://evil.example/item",
        )

    assertEquals(
        PluginUrlDecision.Rejected(
            PluginUrlRejection.UNDECLARED_HOST,
        ),
        result,
    )
}
```

- [ ] **Step 2: run URL policy test and confirm RED**

```powershell
./gradlew :core:network:test `
  --tests "app.openstory.network.PluginUrlPolicyTest"
```

Expected: compilation failure because the policy does not exist.

- [ ] **Step 3: implement URL policy without network calls**

Use `HttpUrl` internally, but return only `ValidatedPluginUrl`.

Rules:

- reject blank/malformed candidate;
- reject user information;
- reject protocol-relative candidate;
- relative candidate requires valid `baseUrl`;
- production accepts HTTPS only;
- `forTesting` may accept HTTP;
- exact normalized host match only;
- no wildcard or suffix match;
- preserve path/query/fragment in normalized `value`;
- never log or include the candidate in a rejection message.

- [ ] **Step 4: refactor the gateway to use the policy**

Keep public constructors source-compatible.

Inside `AllowlistedHttpGateway`:

```kotlin
private val urlPolicy =
    DefaultPluginUrlPolicy(
        allowedHosts = allowedHosts,
        allowCleartextForTesting = allowCleartextForTesting,
    )
```

Replace initial URL checks with:

```kotlin
when (
    val decision =
        urlPolicy.resolve(request.url)
) {
    is PluginUrlDecision.Allowed ->
        executeAllowed(
            request = request,
            budget = budget,
            initialUrl =
                decision.value.value.toHttpUrl(),
        )

    is PluginUrlDecision.Rejected ->
        decision.toNetworkFailure()
}
```

Apply the same policy to every redirect target.

Keep all existing response, compression, decoding, rate, session, and redaction logic unchanged.

- [ ] **Step 5: run network regression tests**

```powershell
./gradlew :core:network:test `
  --tests "app.openstory.network.PluginUrlPolicyTest" `
  --tests "app.openstory.network.AllowlistedHttpGatewayTest"

./gradlew :core:network:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: write failing shared output validator tests**

Create `PluginWireDtoValidatorTest.kt`:

```kotlin
@Test
fun catalogCardsRejectDuplicateStableIds() {
    val cards =
        listOf(
            card(sourceId = "same"),
            card(sourceId = "same"),
        )

    val result =
        validator.validateCatalogSearch(
            Page(
                items = cards,
                nextToken = null,
            ),
        )

    assertPluginFailure(
        result,
        code = "plugin.output_duplicate_id",
        fieldPath = "items.1.sourceId",
    )
}

@Test
fun chapterImageRejectsUndeclaredHost() {
    val document =
        ChapterDocument(
            title = null,
            blocks = listOf(
                ChapterBlock.Image(
                    reference =
                        ChapterImageReference(
                            url =
                                "https://evil.example/image.jpg",
                            declaredHost =
                                "evil.example",
                        ),
                    altText = null,
                ),
            ),
        )

    val result =
        validator.validateChapterDocument(document)

    assertPluginFailure(
        result,
        code = "plugin.output_undeclared_host",
        fieldPath = "blocks.0.reference.url",
    )
}
```

- [ ] **Step 7: run validator test and confirm RED**

```powershell
./gradlew :core:plugin-host:testDebugUnitTest `
  --tests "app.openstory.plugin.host.selector.validation.PluginWireDtoValidatorTest"
```

Expected: compilation failure because validators do not exist.

- [ ] **Step 8: implement output limits**

Create:

```kotlin
data class PluginOutputLimits(
    val maxOutputItems: Int = 10_000,
    val maxOutputSections: Int = 100,
    val maxOutputItemsPerSection: Int = 1_000,
    val maxTotalOutputItems: Int = 10_000,
    val maxReleaseItems: Int = 20_000,
    val maxTombstoneIds: Int = 20_000,
    val maxChapterBlocks: Int = 20_000,
    val maxChapterTextCharacters: Int = 5_000_000,
    val maxSpansPerBlock: Int = 2_000,
    val maxTotalSpans: Int = 100_000,
)
```

Validate every value is positive.

These are host defaults. V2 requested limits may only reduce them.

- [ ] **Step 9: implement validator APIs**

Use named methods to avoid generic erasure:

```kotlin
class PluginWireDtoValidator(
    private val catalog:
        CatalogWireDtoValidator,
    private val content:
        ContentWireDtoValidator,
) {
    fun validateCatalogHome(
        value: List<CatalogSection>,
    ): AppResult<List<CatalogSection>>

    fun validateCatalogSearch(
        value: Page<CatalogCard>,
    ): AppResult<Page<CatalogCard>>

    fun validateCatalogDetails(
        value: CatalogDetails,
    ): AppResult<CatalogDetails>

    fun validateCatalogFilters(
        value: List<CatalogFilterDefinition>,
    ): AppResult<List<CatalogFilterDefinition>>

    fun validateContentSearch(
        value: Page<ContentStoryCandidate>,
    ): AppResult<Page<ContentStoryCandidate>>

    fun validateContentStory(
        value: ContentStoryDetails,
    ): AppResult<ContentStoryDetails>

    fun validateReleases(
        value: List<SourceChapterRelease>,
    ): AppResult<List<SourceChapterRelease>>

    fun validateChapterSyncDelta(
        value: ChapterSyncDelta,
    ): AppResult<ChapterSyncDelta>

    fun validateChapterDocument(
        value: ChapterDocument,
    ): AppResult<ChapterDocument>
}
```

Required validator behavior:

- blank required IDs/titles fail;
- duplicate stable IDs fail with the second item path;
- URLs use `PluginUrlPolicy` without network requests;
- declared host equals normalized URL host;
- score constructor invariants are rechecked;
- filter IDs/options are unique;
- `popularityRank >= 0`;
- direct mapping pairs are unique;
- release IDs are unique;
- upsert/tombstone conflict fails;
- heading levels are `1..6`;
- block count and text-character totals obey limits;
- spans satisfy `0 <= start < endExclusive <= value.length`;
- span counts obey per-block and total limits;
- all failures use `AppError.Plugin` codes beginning `plugin.output_`;
- diagnostics include safe `field_path` and `error_reason` only.

- [ ] **Step 10: run focused and affected module suites**

```powershell
./gradlew `
  :core:network:test `
  :core:plugin-host:testDebugUnitTest `
  --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 11: stage and commit**

```powershell
git add -- `
  core/network/src/main/kotlin/app/openstory/network/PluginUrlPolicy.kt `
  core/network/src/main/kotlin/app/openstory/network/AllowlistedHttpGateway.kt `
  core/network/src/test/kotlin/app/openstory/network/PluginUrlPolicyTest.kt `
  core/network/src/test/kotlin/app/openstory/network/AllowlistedHttpGatewayTest.kt `
  core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/validation `
  core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/validation

git diff --cached --check
git commit -m "plugin-host: add shared plugin output validation"
```

---

### Task 5: Implement the bounded V2 binding evaluator and richer HTML adapter

**Commit:** `plugin-host: evaluate typed selector bindings`

**Files:**

- Modify: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/SelectorExecutionContext.kt`
- Modify: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/SelectorInterpreter.kt`
- Modify: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/HtmlDocumentAdapter.kt`
- Create: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/binding/SelectorBoundValue.kt`
- Create: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/binding/SelectorFieldPath.kt`
- Create: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/binding/SelectorEvaluationBudget.kt`
- Create: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/binding/SelectorBindingEvaluator.kt`
- Test: `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/binding/SelectorBindingEvaluatorTest.kt`
- Modify: `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/SelectorRuntimeTest.kt`

**Interfaces:**

```kotlin
sealed interface SelectorBoundValue {
    data object Null : SelectorBoundValue
    data class Text(val value: String) : SelectorBoundValue
    data class Integer(val value: Int) : SelectorBoundValue
    data class LongValue(val value: Long) : SelectorBoundValue
    data class DoubleValue(val value: Double) : SelectorBoundValue
    data class BooleanValue(val value: Boolean) : SelectorBoundValue
    data class ListValue(
        val values: List<SelectorBoundValue>,
    ) : SelectorBoundValue
    data class ObjectValue(
        val fields: Map<String, SelectorBoundValue>,
    ) : SelectorBoundValue
}

class SelectorBindingEvaluator {
    suspend fun evaluate(
        binding: SelectorBinding,
        scope: HtmlScope,
        path: SelectorFieldPath,
        budget: SelectorEvaluationBudget,
    ): AppResult<SelectorBoundValue>
}
```

`HtmlScope` is opaque outside `core:plugin-host` and never exposes Jsoup types.

- [ ] **Step 1: write failing evaluator behavior tests**

Create tests:

```kotlin
@Test
fun evaluatorPreservesNestedItemOrderAndFieldPaths() = runTest {
    val document =
        parser.parse(
            html =
                """
                <main>
                  <article><a href="/n/1"><span>A</span></a></article>
                  <article><a href="/n/2"></a></article>
                </main>
                """.trimIndent(),
            baseUri = "https://allowed.example/search",
        )

    val binding =
        ListBinding(
            css = "article",
            item = ObjectBinding(
                fields = linkedMapOf(
                    "sourceId" to AttributeBinding(
                        css = "a",
                        attribute = "href",
                    ),
                    "title" to TextBinding(
                        css = "span",
                    ),
                ),
            ),
        )

    val result =
        evaluator.evaluate(
            binding = binding,
            scope = document,
            path = SelectorFieldPath.root("items"),
            budget = SelectorEvaluationBudget(),
        )

    assertPluginFailure(
        result,
        code = "plugin.selector_field_missing",
        fieldPath = "items.1.title",
    )
}
```

```kotlin
@Test
fun evaluatorStopsAtGlobalOutputBudget() = runTest {
    val document =
        parser.parse(
            html =
                "<ul>" +
                    (1..4)
                        .joinToString("") {
                            "<li>$it</li>"
                        } +
                    "</ul>",
            baseUri = "https://allowed.example/",
        )

    val result =
        evaluator.evaluate(
            binding =
                TextListBinding(
                    css = "li",
                ),
            scope = document,
            path = SelectorFieldPath.root("items"),
            budget =
                SelectorEvaluationBudget(
                    maxOutputItems = 3,
                ),
        )

    assertPluginFailure(
        result,
        code = "plugin.selector_output_limit",
        fieldPath = "items",
    )
}
```

- [ ] **Step 2: run evaluator tests and confirm RED**

```powershell
./gradlew :core:plugin-host:testDebugUnitTest `
  --tests "app.openstory.plugin.host.selector.binding.SelectorBindingEvaluatorTest"
```

Expected: compilation failure because evaluator types do not exist.

- [ ] **Step 3: add safe field-path builder**

Create:

```kotlin
@JvmInline
value class SelectorFieldPath private constructor(
    val value: String,
) {
    fun field(
        name: String,
    ): SelectorFieldPath =
        SelectorFieldPath(
            "$value.$name",
        )

    fun index(
        index: Int,
    ): SelectorFieldPath =
        SelectorFieldPath(
            "$value.$index",
        )

    companion object {
        fun root(
            name: String,
        ): SelectorFieldPath {
            require(
                name.matches(
                    Regex("[A-Za-z][A-Za-z0-9_]*"),
                ),
            )
            return SelectorFieldPath(name)
        }
    }
}
```

Every field segment comes from host-known DTO fields or validated schema keys.

- [ ] **Step 4: add endpoint-wide budget state**

Create `SelectorEvaluationBudget` with:

```kotlin
class SelectorEvaluationBudget(
    private val maxBoundFields: Int = 100_000,
    private val maxOutputItems: Int = 10_000,
    private val maxChapterBlocks: Int = 20_000,
    private val maxChapterTextCharacters: Int = 5_000_000,
    private val maxTotalSpans: Int = 100_000,
) {
    suspend fun consumeField()
    suspend fun consumeOutputItem()
    suspend fun consumeChapterBlock()
    suspend fun consumeChapterCharacters(count: Int)
    suspend fun consumeSpans(count: Int)
}
```

Each method:

- calls `currentCoroutineContext().ensureActive()`;
- increments one counter;
- returns typed `AppResult.Failure` or throws one internal private budget signal that the evaluator maps immediately;
- never resets for nested bindings;
- never includes actual content in errors.

- [ ] **Step 5: extend the HTML adapter additively**

Preserve all methods used by the V1 interpreter.

Add opaque handles:

```kotlin
interface HtmlScope
interface HtmlDocumentScope : HtmlScope
interface HtmlElementScope : HtmlScope
```

Add methods:

```kotlin
fun parseScope(
    html: String,
    baseUri: String,
): HtmlDocumentScope

fun selectAll(
    scope: HtmlScope,
    css: String,
): List<HtmlElementScope>

fun text(
    scope: HtmlScope,
    css: String? = null,
): String?

fun attribute(
    scope: HtmlScope,
    css: String? = null,
    attribute: String,
): HtmlAttributeValue

fun matches(
    element: HtmlElementScope,
    css: String,
): Boolean

fun semanticText(
    scope: HtmlScope,
): HtmlSemanticText
```

Define:

```kotlin
data class HtmlSemanticText(
    val value: String,
    val spans: List<HtmlSemanticSpan>,
)

data class HtmlSemanticSpan(
    val start: Int,
    val endExclusive: Int,
    val style: HtmlSemanticStyle,
)

enum class HtmlSemanticStyle {
    EMPHASIS,
    STRONG,
}
```

Jsoup behavior:

- walk text nodes in document order;
- collapse whitespace using the host transform only;
- map `em`/`i` to `EMPHASIS`;
- map `strong`/`b` to `STRONG`;
- ignore arbitrary style attributes;
- return deterministic spans;
- normalize source URL attributes using document base URI;
- do not fetch output URLs.

- [ ] **Step 6: expose bounded document acquisition from the interpreter**

Add a V2 method without changing `execute`:

```kotlin
suspend fun executeDocument(
    operations: List<SelectorOperation>,
    input: Map<String, String>,
): AppResult<HtmlDocumentScope>
```

Rules:

- uses the existing operation, request, document-character, node, and wall-clock budgets;
- requires final type `DOCUMENT`;
- permits `HttpGet` followed by zero or more `RemoveElements`;
- does not permit field extraction operations in a V2 request plan;
- preserves V1 `execute` behavior and tests.

- [ ] **Step 7: implement binding evaluation**

Required mapping:

```text
TextBinding          -> Text or missing failure
AttributeBinding     -> Text or missing failure
ConstantBinding      -> Text
OptionalBinding      -> Null when inner value is missing
IntegerBinding       -> Integer
LongBinding          -> LongValue
DoubleBinding        -> DoubleValue
BooleanBinding       -> BooleanValue
EnumBinding          -> normalized Text
TimestampBinding     -> LongValue epoch milliseconds
UrlBinding           -> normalized Text; host validation occurs in mapper/validator
TextListBinding      -> ListValue<Text>
TextSetBinding       -> ListValue<Text> with first-occurrence order
ObjectBinding        -> ObjectValue
ListBinding          -> ListValue<ObjectValue>
```

Missing required values return:

```text
plugin.selector_field_missing
```

Conversion failures return:

```text
plugin.selector_field_invalid
```

Budget failures return:

```text
plugin.selector_output_limit
```

Cancellation is rethrown and never converted to a plugin failure.

- [ ] **Step 8: add cancellation and diagnostic-redaction tests**

Add:

```kotlin
@Test
fun cancellationDuringNestedBindingReturnsNoPartialValue() = runTest {
    val job =
        launch {
            evaluator.evaluate(
                binding = veryLargeBinding(),
                scope = largeDocument(),
                path = SelectorFieldPath.root("items"),
                budget = SelectorEvaluationBudget(),
            )
        }

    yield()
    job.cancelAndJoin()

    assertTrue(job.isCancelled)
}
```

Add a failure test with secret fixture text and assert:

```kotlin
assertFalse(error.toString().contains("SECRET_CHAPTER_TEXT"))
assertFalse(error.toString().contains("<article"))
```

- [ ] **Step 9: run focused, V1 regression, and module tests**

```powershell
./gradlew :core:plugin-host:testDebugUnitTest `
  --tests "app.openstory.plugin.host.selector.binding.SelectorBindingEvaluatorTest" `
  --tests "app.openstory.plugin.host.selector.SelectorRuntimeTest"

./gradlew :core:plugin-host:testDebugUnitTest
```

Expected:

- all pre-existing V1 `SelectorRuntimeTest` methods still pass;
- new evaluator tests pass;
- `BUILD SUCCESSFUL`.

- [ ] **Step 10: stage and commit**

```powershell
git add -- `
  core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/SelectorExecutionContext.kt `
  core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/SelectorInterpreter.kt `
  core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/HtmlDocumentAdapter.kt `
  core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/binding `
  core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/SelectorRuntimeTest.kt `
  core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/binding

git diff --cached --check
git commit -m "plugin-host: evaluate typed selector bindings"
```

---

### Task 6: Map Catalog bindings to all Catalog wire DTOs

**Commit:** `plugin-host: map selector output to catalog DTOs`

**Files:**

- Create: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/mapper/CatalogSelectorMapper.kt`
- Test: `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/mapper/CatalogSelectorMapperTest.kt`
- Create:
  - `test/fixtures/src/main/resources/plugin-selector-v2/catalog-home.html`
  - `test/fixtures/src/main/resources/plugin-selector-v2/catalog-search.html`
  - `test/fixtures/src/main/resources/plugin-selector-v2/catalog-details.html`

**Interfaces:**

```kotlin
class CatalogSelectorMapper(
    private val outputValidator:
        PluginWireDtoValidator,
) {
    fun mapHome(
        value: SelectorBoundValue,
    ): AppResult<List<CatalogSection>>

    fun mapSearch(
        items: SelectorBoundValue,
        nextToken: SelectorBoundValue?,
        nextTokenKind: SelectorTokenKind,
    ): AppResult<Page<CatalogCard>>

    fun mapDetails(
        value: SelectorBoundValue,
    ): AppResult<CatalogDetails>

    fun mapFilters(
        value: CatalogFiltersSelector,
    ): AppResult<List<CatalogFilterDefinition>>
}
```

- [ ] **Step 1: write failing Catalog mapper tests**

Create tests for:

```kotlin
@Test
fun mapsPagedCatalogCardsWithNestedImageAndScore() {
    val result =
        mapper.mapSearch(
            items =
                listValue(
                    objectValue(
                        "sourceId" to text("/novel/1"),
                        "title" to text("Novel One"),
                        "authors" to listValue(text("Author A")),
                        "image" to objectValue(
                            "url" to text(
                                "https://cdn.allowed.example/1.jpg",
                            ),
                            "declaredHost" to text(
                                "cdn.allowed.example",
                            ),
                        ),
                        "score" to objectValue(
                            "value" to doubleValue(4.5),
                            "scale" to doubleValue(5.0),
                        ),
                    ),
                ),
            nextToken = text("/search?page=2"),
            nextTokenKind = SelectorTokenKind.URL,
        )

    val page = result.value()
    assertEquals("Novel One", page.items.single().title)
    assertEquals(
        "https://allowed.example/search?page=2",
        page.nextToken,
    )
}
```

```kotlin
@Test
fun missingCatalogTitleReturnsTypedFieldFailure() {
    val result =
        mapper.mapSearch(
            items =
                listValue(
                    objectValue(
                        "sourceId" to text("1"),
                    ),
                ),
            nextToken = null,
            nextTokenKind = SelectorTokenKind.OPAQUE,
        )

    assertPluginFailure(
        result,
        code = "plugin.selector_field_missing",
        fieldPath = "items.0.title",
    )
}
```

- [ ] **Step 2: run mapper tests and confirm RED**

```powershell
./gradlew :core:plugin-host:testDebugUnitTest `
  --tests "app.openstory.plugin.host.selector.mapper.CatalogSelectorMapperTest"
```

Expected: compilation failure because the mapper does not exist.

- [ ] **Step 3: implement strict bound-value readers**

Inside `CatalogSelectorMapper.kt`, create private readers:

```kotlin
private fun ObjectValue.requiredText(
    field: String,
    path: SelectorFieldPath,
): AppResult<String>

private fun ObjectValue.optionalText(
    field: String,
    path: SelectorFieldPath,
): AppResult<String?>

private fun ObjectValue.textList(
    field: String,
    path: SelectorFieldPath,
): AppResult<List<String>>

private fun ObjectValue.requiredDouble(
    field: String,
    path: SelectorFieldPath,
): AppResult<Double>
```

Readers:

- reject a wrong bound-value type;
- trim required strings;
- do not include rejected values in messages;
- attach exact nested field paths;
- use `plugin.selector_field_missing` and `plugin.selector_field_invalid`.

- [ ] **Step 4: implement Catalog mapping**

Map:

- `CatalogSection`;
- `CatalogCard`;
- `CatalogImageReference`;
- `CatalogScore`;
- `CatalogDetails`;
- all five `CatalogFilterDefinition` variants;
- `Page<CatalogCard>`.

Defaults:

```text
authors = emptyList()
aliases = emptyList()
genres = emptyList()
image = null
score = null
description = null
sourceUrl = null
popularityRank = null
nextToken = null
```

After constructing each top-level output, call the matching
`PluginWireDtoValidator` method.

Catch `IllegalArgumentException` from DTO constructors and convert it to
`plugin.selector_field_invalid`.

- [ ] **Step 5: add complete Catalog fixture tests**

Use synthetic fixture HTML to prove:

- `runtimeExtractsCatalogSectionsWithinDeclaredHost`;
- `runtimeExtractsPagedCatalogCards`;
- `runtimeExtractsCatalogDetails`;
- `runtimeReturnsDeclaredCatalogFilters`;
- relative image and next-page URLs normalize against the document base;
- undeclared image host fails;
- duplicate `sourceId` fails;
- optional fields use defaults;
- no raw fixture title appears in diagnostics.

The fixture gateway returns in-memory HTML and never calls the internet.

- [ ] **Step 6: run focused and module tests**

```powershell
./gradlew :core:plugin-host:testDebugUnitTest `
  --tests "app.openstory.plugin.host.selector.mapper.CatalogSelectorMapperTest"

./gradlew :core:plugin-host:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: stage and commit**

```powershell
git add -- `
  core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/mapper/CatalogSelectorMapper.kt `
  core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/mapper/CatalogSelectorMapperTest.kt `
  test/fixtures/src/main/resources/plugin-selector-v2/catalog-home.html `
  test/fixtures/src/main/resources/plugin-selector-v2/catalog-search.html `
  test/fixtures/src/main/resources/plugin-selector-v2/catalog-details.html

git diff --cached --check
git commit -m "plugin-host: map selector output to catalog DTOs"
```

---

### Task 7: Map Content bindings to all Content wire DTOs

**Commit:** `plugin-host: map selector output to content DTOs`

**Files:**

- Create: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/mapper/ContentSelectorMapper.kt`
- Test: `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/mapper/ContentSelectorMapperTest.kt`
- Create:
  - `test/fixtures/src/main/resources/plugin-selector-v2/content-search.html`
  - `test/fixtures/src/main/resources/plugin-selector-v2/content-story.html`
  - `test/fixtures/src/main/resources/plugin-selector-v2/content-releases.html`
  - `test/fixtures/src/main/resources/plugin-selector-v2/content-chapter.html`

**Interfaces:**

```kotlin
class ContentSelectorMapper(
    private val outputValidator:
        PluginWireDtoValidator,
) {
    fun mapSearch(
        items: SelectorBoundValue,
        nextToken: SelectorBoundValue?,
        nextTokenKind: SelectorTokenKind,
    ): AppResult<Page<ContentStoryCandidate>>

    fun mapStory(
        value: SelectorBoundValue,
    ): AppResult<ContentStoryDetails>

    fun mapReleases(
        value: SelectorBoundValue,
    ): AppResult<List<SourceChapterRelease>>

    fun mapSync(
        value: SelectorBoundValue,
        nextTokenKind: SelectorTokenKind,
    ): AppResult<ChapterSyncDelta>

    fun mapChapter(
        title: SelectorBoundValue?,
        blocks: List<BoundChapterBlock>,
    ): AppResult<ChapterDocument>
}
```

Define internal ordered block values in the same file or a focused sibling:

```kotlin
sealed interface BoundChapterBlock {
    data class Paragraph(
        val text: BoundChapterText,
    ) : BoundChapterBlock
    data class Heading(
        val level: Int,
        val text: BoundChapterText,
    ) : BoundChapterBlock
    data object Divider : BoundChapterBlock
    data class Image(
        val url: String,
        val declaredHost: String?,
        val altText: String?,
    ) : BoundChapterBlock
    data class Note(
        val text: BoundChapterText,
    ) : BoundChapterBlock
}

data class BoundChapterText(
    val value: String,
    val spans: List<HtmlSemanticSpan>,
)
```

- [ ] **Step 1: write failing Content mapper tests**

Add tests:

```kotlin
@Test
fun mapsChapterReleaseTimestampsAndDefaults() {
    val result =
        mapper.mapReleases(
            listValue(
                objectValue(
                    "sourceReleaseId" to text("r1"),
                    "sourceUrl" to text(
                        "https://allowed.example/chapter/1",
                    ),
                    "languageTag" to text("en"),
                    "rawTitle" to text("Chapter 1"),
                    "publishedAtEpochMillis" to longValue(
                        1_700_000_000_000,
                    ),
                ),
            ),
        )

    val release = result.value().single()
    assertEquals(
        ChapterKindHint.UNKNOWN,
        release.kindHint,
    )
    assertEquals(
        1_700_000_000_000,
        release.publishedAtEpochMillis,
    )
}
```

```kotlin
@Test
fun chapterMapperPreservesMixedBlockOrderAndSpans() {
    val result =
        mapper.mapChapter(
            title = text("Chapter"),
            blocks = listOf(
                BoundChapterBlock.Heading(
                    level = 2,
                    text =
                        BoundChapterText(
                            value = "Heading",
                            spans = emptyList(),
                        ),
                ),
                BoundChapterBlock.Paragraph(
                    text =
                        BoundChapterText(
                            value = "Very important",
                            spans = listOf(
                                HtmlSemanticSpan(
                                    start = 5,
                                    endExclusive = 14,
                                    style =
                                        HtmlSemanticStyle.STRONG,
                                ),
                            ),
                        ),
                ),
                BoundChapterBlock.Divider,
            ),
        )

    val document = result.value()
    assertIs<ChapterBlock.Heading>(document.blocks[0])
    assertIs<ChapterBlock.Paragraph>(document.blocks[1])
    assertIs<ChapterBlock.Divider>(document.blocks[2])
}
```

- [ ] **Step 2: run Content mapper tests and confirm RED**

```powershell
./gradlew :core:plugin-host:testDebugUnitTest `
  --tests "app.openstory.plugin.host.selector.mapper.ContentSelectorMapperTest"
```

Expected: compilation failure because the mapper does not exist.

- [ ] **Step 3: implement story and release mapping**

Map:

- `ContentStoryCandidate`;
- `ContentStoryDetails`;
- `DirectCatalogMapping`;
- `SourceChapterRelease`;
- `ChapterSyncDelta`.

Defaults:

```text
authors = emptyList()
aliases = emptyList()
description = null
directCatalogMappings = emptyList()
kindHint = UNKNOWN
all raw/normalized optional values = null
timestamps = null
contentFingerprint = null
tombstones = emptySet()
nextCursor = null
```

Enum mapping uses exact DTO enum names after alias normalization.

Language tags remain strings in DTOs but pass shared output validation.

Reject:

- duplicate story/release IDs;
- duplicate direct mapping pairs;
- invalid enum;
- wrong timestamp value type;
- upsert/tombstone conflict;
- URL policy failure.

- [ ] **Step 4: implement ordered chapter block mapping**

Map:

- paragraph;
- heading;
- divider;
- image;
- note;
- semantic emphasis and strong spans.

Convert:

```text
HtmlSemanticStyle.EMPHASIS -> ChapterTextStyle.EMPHASIS
HtmlSemanticStyle.STRONG   -> ChapterTextStyle.STRONG
```

Validate heading levels before DTO creation.

For images:

- use validated URL;
- derive `declaredHost` when omitted;
- explicit host must match validated URL host.

Run the final `ChapterDocument` through `PluginWireDtoValidator`.

- [ ] **Step 5: add complete Content fixture tests**

Required fixture tests:

```text
runtimeExtractsPagedContentStoryCandidates
runtimeExtractsContentStoryDetails
runtimeExtractsLatestChapterReleases
runtimeExtractsAllChapterReleases
runtimeMapsChapterSyncDelta
runtimePreservesChapterBlockOrder
runtimeMapsParagraphHeadingDividerImageAndNote
runtimeMapsSemanticTextSpans
runtimeRejectsUpsertTombstoneConflict
runtimeRejectsInvalidChapterImageHost
runtimeEnforcesChapterBlockBudget
runtimeReportsNestedBlockFieldPath
runtimeDoesNotExposeRawChapterHtmlInDiagnostics
```

Use only synthetic in-memory fixture HTML.

- [ ] **Step 6: run focused and module tests**

```powershell
./gradlew :core:plugin-host:testDebugUnitTest `
  --tests "app.openstory.plugin.host.selector.mapper.ContentSelectorMapperTest"

./gradlew :core:plugin-host:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: stage and commit**

```powershell
git add -- `
  core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/mapper/ContentSelectorMapper.kt `
  core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/mapper/ContentSelectorMapperTest.kt `
  test/fixtures/src/main/resources/plugin-selector-v2/content-search.html `
  test/fixtures/src/main/resources/plugin-selector-v2/content-story.html `
  test/fixtures/src/main/resources/plugin-selector-v2/content-releases.html `
  test/fixtures/src/main/resources/plugin-selector-v2/content-chapter.html

git diff --cached --check
git commit -m "plugin-host: map selector output to content DTOs"
```

---

### Task 8: Expose V2 Catalog and Content plugins through one selector factory

**Commit:** `plugin-host: expose selector v2 plugin endpoints`

**Files:**

- Create: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/runtime/SelectorEndpointExecutor.kt`
- Create: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/runtime/SelectorCatalogPlugin.kt`
- Create: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/runtime/SelectorContentPlugin.kt`
- Create: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/runtime/SelectorPluginFactory.kt`
- Modify: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/SelectorRuntime.kt`
- Modify: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/SelectorExecutionContext.kt`
- Modify: `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/SelectorRuntimeTest.kt`
- Test: `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/runtime/SelectorPluginFactoryTest.kt`
- Create: `test/fixtures/src/main/resources/plugin-selector-v2/selector-v2.json`
- Modify: `docs/plugin-sdk/declarative-plugin-schema.md`

**Interfaces:**

```kotlin
data class SelectorPluginHandle(
    val catalog: CatalogPlugin?,
    val content: ContentPlugin?,
)

class SelectorPluginFactory {
    fun create(
        manifest: PluginManifest,
        source: String,
        gateway: PluginHttpGateway,
    ): AppResult<SelectorPluginHandle>
}
```

`SelectorCatalogPlugin` implements every `CatalogPlugin` method.
`SelectorContentPlugin` implements every `ContentPlugin` method.

- [ ] **Step 1: write failing factory tests**

Create:

```kotlin
@Test
fun factoryCreatesBothPluginContractsFromOneV2Definition() {
    val handle =
        factory.create(
            manifest =
                manifest(
                    kinds =
                        setOf(
                            PluginKind.CATALOG,
                            PluginKind.CONTENT,
                        ),
                    declarativeOrigin =
                        "https://allowed.example/",
                ),
            source = fixtureText("selector-v2.json"),
            gateway = fixtureGateway(),
        ).value()

    assertNotNull(handle.catalog)
    assertNotNull(handle.content)
}
```

```kotlin
@Test
fun factoryRejectsRelativeRequestWithoutManifestOrigin() {
    val result =
        factory.create(
            manifest =
                manifest(
                    kinds = setOf(PluginKind.CATALOG),
                    declarativeOrigin = null,
                ),
            source = relativeCatalogDefinition(),
            gateway = fixtureGateway(),
        )

    assertPluginFailure(
        result,
        code = "plugin.selector_origin_required",
        fieldPath = "catalog.search.request",
    )
}
```

```kotlin
@Test
fun factoryPreservesVersionOneRuntimePath() {
    val decoded =
        SelectorDefinitionDecoder()
            .decode(versionOneDefinition())
            .getOrThrow()

    assertIs<DecodedSelectorDefinition.V1>(decoded)
}
```

- [ ] **Step 2: run factory tests and confirm RED**

```powershell
./gradlew :core:plugin-host:testDebugUnitTest `
  --tests "app.openstory.plugin.host.selector.runtime.SelectorPluginFactoryTest"
```

Expected: compilation failure because runtime adapters/factory do not exist.

- [ ] **Step 3: implement endpoint executor**

`SelectorEndpointExecutor` does one complete endpoint operation:

```kotlin
suspend fun execute(
    endpointName: String,
    request: SelectorRequestPlan,
    output: SelectorBinding,
    input: Map<String, String>,
    rootPath: SelectorFieldPath,
): AppResult<SelectorBoundValue>
```

Flow:

1. validate effective requested limits against host maxima;
2. call `SelectorInterpreter.executeDocument`;
3. create one endpoint-wide `SelectorEvaluationBudget`;
4. evaluate the output binding;
5. return bound value;
6. rethrow cancellation;
7. add safe `endpoint` and `field_path` diagnostics;
8. never return partial bound values.

Chapter execution has an additional method that uses ordered block variants and `HtmlDocumentAdapter.semanticText`.

- [ ] **Step 4: implement `SelectorCatalogPlugin`**

Method input maps:

```text
home:
  languageTags -> sorted comma-separated token
  contentTypes -> sorted enum names

search:
  query
  nextToken when non-null
  filter.<filterId> for each selected value, joined by comma

details:
  sourceId
```

Do not include undeclared map keys.

Each method:

- verifies its endpoint exists;
- renders only host-declared template variables;
- executes binding;
- maps DTO;
- validates DTO;
- returns typed failure.

Missing endpoint returns:

```text
plugin.output_contract_mismatch
```

with safe diagnostic `endpoint`.

- [ ] **Step 5: implement `SelectorContentPlugin`**

Method input maps:

```text
search:
  query
  nextToken when non-null

story:
  sourceStoryId

latest:
  sourceStoryId
  limit

allChapters:
  sourceStoryId

sync:
  sourceStoryId
  cursor when non-null

chapter:
  sourceReleaseId
```

Reject non-positive `latest.limit` before request execution.

- [ ] **Step 6: implement factory validation and construction**

Factory flow:

```text
decode
-> V1: return a handle only through the existing raw SelectorRuntime API;
       do not invent CatalogPlugin/ContentPlugin adapters
-> V2: validate manifest + definition
-> verify endpoint groups match manifest kinds
-> derive SelectorExecutionContext from manifest.declarativeOrigin
-> construct shared URL policy, interpreter, evaluator, mappers, validators
-> return typed CatalogPlugin/ContentPlugin adapters
```

For a V1 source, the factory returns:

```text
plugin.output_contract_mismatch
reason = v1_has_no_wire_dto_bindings
```

The existing `SelectorRuntime.execute(V1, ...)` remains available and unchanged.

- [ ] **Step 7: restore the original Task 03 acceptance test**

Add the concrete test:

```kotlin
@Test
fun runtimeExtractsCardsWithinDeclaredHost() = runTest {
    val catalog =
        factory.create(
            manifest = catalogManifest(),
            source = catalogSelectorDefinition(),
            gateway =
                fixtureGateway(
                    """
                    <article class="story">
                      <a href="/n/1">
                        <span class="title">Novel</span>
                      </a>
                    </article>
                    """.trimIndent(),
                ),
        ).value().catalog
            ?: error("Catalog plugin missing")

    val cards =
        catalog.search(
            CatalogSearchRequest(
                query = "Novel",
            ),
        ).value().items

    assertEquals("Novel", cards.single().title)
    assertEquals(
        "https://allowed.example/n/1",
        cards.single().sourceId,
    )
}
```

The schema for this fixture must bind `sourceId` as a URL. If the desired stable
source ID is `"1"` rather than the full URL, bind a dedicated `data-source-id`
attribute; do not add URL path parsing or arbitrary regex to the runtime.

- [ ] **Step 8: add all endpoint integration tests**

One test per method:

```text
catalog.home
catalog.search
catalog.details
catalog.filters
content.search
content.story
content.latest
content.allChapters
content.sync
content.chapter
```

Also test:

- definition group not declared in manifest kind;
- manifest kind with missing endpoint group;
- unknown template input;
- V1 compatibility path;
- V2 unknown version;
- relative request origin;
- timeout;
- cancellation;
- output limit;
- redacted diagnostics.

- [ ] **Step 9: update the SDK documentation**

Document:

- `declarativeOrigin`;
- V1 compatibility;
- V2 root JSON;
- every binding discriminator;
- every Catalog endpoint;
- every Content endpoint;
- static Catalog filters;
- chapter block variants;
- timestamp format IDs;
- token kind;
- URL/host rules;
- host budgets;
- install-time and runtime errors;
- one complete synthetic Catalog JSON example;
- one complete synthetic chapter JSON example;
- migration note: V1 is not automatically upgraded.

- [ ] **Step 10: run full affected verification**

```powershell
./gradlew `
  :core:plugin-api:test `
  :core:network:test `
  :core:plugin-host:testDebugUnitTest `
  :core:plugin-host:assembleDebug `
  :core:plugin-host:lintDebug `
  --rerun-tasks `
  --stacktrace
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 11: stage and commit**

```powershell
git add -- `
  core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/runtime `
  core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/SelectorRuntime.kt `
  core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/SelectorExecutionContext.kt `
  core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/SelectorRuntimeTest.kt `
  core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/runtime `
  test/fixtures/src/main/resources/plugin-selector-v2/selector-v2.json `
  docs/plugin-sdk/declarative-plugin-schema.md

git diff --cached --check
git commit -m "plugin-host: expose selector v2 plugin endpoints"
```

---

### Task 9: Close Wave 04 Task 03 with compatibility and security evidence

**Commit:** `docs: record selector v2 runtime checkpoint`

**Files:**

- Create: `docs/internal/checkpoints/2026-08-06-wave-04-task-03-selector-v2.md`
- Modify only when required by the repository's existing checkpoint convention:
  - `2026-08-03-plan-coverage-matrix.md`
  - `PLAN-STATS.txt`

**Interfaces:**

- Consumes all production tasks.
- Produces review evidence; no product behavior.

- [ ] **Step 1: run clean status and commit history checks**

```powershell
$ErrorActionPreference = "Stop"

if (git status --short) {
    throw "Worktree must be clean before final verification."
}

git log --oneline --decorate -12
```

Expected commit sequence contains:

```text
plugin-host: expose selector v2 plugin endpoints
plugin-host: map selector output to content DTOs
plugin-host: map selector output to catalog DTOs
plugin-host: evaluate typed selector bindings
plugin-host: add shared plugin output validation
plugin-api: add content selector bindings
plugin-api: add catalog selector bindings
plugin-api: add selector v2 binding core
docs: specify selector v2 output bindings
05bd13e plugin-host: add bounded selector interpreter
```

- [ ] **Step 2: run final verification from a clean checkout state**

```powershell
./gradlew --stop

./gradlew `
  clean `
  :core:common:test `
  :core:plugin-api:test `
  :core:network:test `
  :core:plugin-host:testDebugUnitTest `
  :core:plugin-host:assembleDebug `
  :core:plugin-host:lintDebug `
  --no-build-cache `
  --rerun-tasks `
  --stacktrace
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: run security-focused tests explicitly**

```powershell
./gradlew `
  :core:network:test `
  --tests "app.openstory.network.PluginUrlPolicyTest" `
  --tests "app.openstory.network.AllowlistedHttpGatewayTest"

./gradlew `
  :core:plugin-host:testDebugUnitTest `
  --tests "app.openstory.plugin.host.selector.SelectorRuntimeTest" `
  --tests "app.openstory.plugin.host.selector.binding.SelectorBindingEvaluatorTest" `
  --tests "app.openstory.plugin.host.selector.validation.PluginWireDtoValidatorTest" `
  --tests "app.openstory.plugin.host.selector.runtime.SelectorPluginFactoryTest"
```

Expected: both commands end with `BUILD SUCCESSFUL`.

- [ ] **Step 4: audit dependency and metadata changes**

```powershell
Write-Host "`n=== DEPENDENCY FILE DIFF ===" -ForegroundColor Cyan

git diff 05bd13e..HEAD -- `
  gradle/libs.versions.toml `
  gradle/verification-metadata.xml `
  core/plugin-host/build.gradle.kts `
  core/network/build.gradle.kts `
  core/plugin-api/build.gradle.kts

Write-Host "`n=== IDEA DIFF ===" -ForegroundColor Cyan

git diff --name-only 05bd13e..HEAD |
  Select-String -Pattern "^\.idea/"
```

Expected:

- no unexplained new dependency;
- no `.idea` file in the change set;
- any verification metadata change is justified by an actual resolved artifact.

- [ ] **Step 5: run static diff checks**

```powershell
git diff 05bd13e..HEAD --check

git diff --name-status 05bd13e..HEAD

git status --short
```

Expected:

- no whitespace errors;
- worktree clean.

- [ ] **Step 6: write the checkpoint document**

The checkpoint must record exact evidence:

```markdown
# Wave 04 Task 03 — Selector V2 Runtime Checkpoint

## Baseline

- Bounded V1 interpreter: `05bd13e`.

## Compatibility

- V1 decode and execution tests: PASS.
- Unknown schema version rejection: PASS.
- Existing V1 operation semantics changed: no.

## Catalog DTO coverage

- home: PASS
- search: PASS
- details: PASS
- filters: PASS

## Content DTO coverage

- search: PASS
- story: PASS
- latest: PASS
- allChapters: PASS
- sync: PASS
- chapter: PASS

## Security and limits

- HTTPS/allowed-host URL policy: PASS
- redirect policy reuse: PASS
- operation/document/node/regex/wall-clock budgets: PASS
- binding/output/chapter/span budgets: PASS
- cancellation with no partial output: PASS
- raw-content diagnostic redaction: PASS

## Verification

- `:core:common:test`: PASS
- `:core:plugin-api:test`: PASS
- `:core:network:test`: PASS
- `:core:plugin-host:testDebugUnitTest`: PASS
- `:core:plugin-host:assembleDebug`: PASS
- `:core:plugin-host:lintDebug`: PASS

## Remaining Wave 04 dependency

Task 04 JavaScript runtime must use `PluginWireDtoValidator` and must not
introduce an independent output-validation model.
```

Replace each `PASS` only with evidence from the actual command output. Do not record a pass before the command succeeds.

- [ ] **Step 7: commit checkpoint evidence**

```powershell
git add -- `
  docs/internal/checkpoints/2026-08-06-wave-04-task-03-selector-v2.md

git diff --cached --check
git commit -m "docs: record selector v2 runtime checkpoint"
```

- [ ] **Step 8: final worktree proof**

```powershell
git show --stat --oneline HEAD
git status --short
```

Expected: worktree is clean.

---

## Cross-task test matrix

The following behavior must have at least one named test before Task 03 closes.

| Requirement | Test owner |
|---|---|
| V1 decode unchanged | `SelectorDefinitionDecoderTest` |
| V1 runtime unchanged | `SelectorRuntimeTest` |
| Unknown V2 version rejected | `SelectorDefinitionDecoderTest` |
| Relative URL requires origin | `PluginManifestTest`, `SelectorPluginFactoryTest` |
| Origin belongs to allowed hosts | `PluginManifestTest` |
| URL validation sends no request | `PluginUrlPolicyTest` |
| Initial request and redirect share policy | `AllowlistedHttpGatewayTest` |
| Binding count/depth limits | `SelectorValidationTest` |
| Catalog field schema | `CatalogSelectorValidationTest` |
| Content field schema | `ContentSelectorValidationTest` |
| Required/optional/default behavior | Catalog and Content mapper tests |
| Nested field paths | evaluator and mapper tests |
| Global output budget | `SelectorBindingEvaluatorTest` |
| Cancellation returns no partial value | `SelectorBindingEvaluatorTest` |
| Duplicate stable IDs | `PluginWireDtoValidatorTest` |
| Catalog score validation | Catalog mapper/validator tests |
| Filter contract validation | Catalog validation/mapper tests |
| Release timestamp conversion | Content mapper tests |
| Sync upsert/tombstone conflict | Content mapper/validator tests |
| Chapter block order | Content mapper integration test |
| Chapter semantic spans | adapter/content mapper tests |
| Chapter image host policy | output validator/content mapper tests |
| Raw payload excluded from diagnostics | evaluator/runtime tests |
| Every Catalog method returns DTO | `SelectorPluginFactoryTest` |
| Every Content method returns DTO | `SelectorPluginFactoryTest` |
| Selector output uses shared validator | mapper/factory tests |
| JavaScript runtime can reuse validator | Task 04 compile/integration test |

## Review gates after every production task

Before each commit:

```powershell
git diff --check
git status --short
git diff --stat
```

After each commit:

```powershell
git show --stat --oneline HEAD
git status --short
```

A task is not complete when:

- a focused test was never observed failing;
- only focused tests ran and the module suite did not;
- the commit contains unrelated `.idea` or generated build output;
- diagnostics include fixture content;
- V1 tests were disabled or rewritten to accept changed semantics;
- a host maximum became plugin-controlled;
- output URL validation performs an HTTP request;
- a mapper bypasses `PluginWireDtoValidator`;
- the worktree is not clean.

## Execution handoff

Recommended execution mode:

1. Use `superpowers:subagent-driven-development`.
2. Assign one fresh implementation worker per numbered task.
3. Review contract correctness first.
4. Review code quality second.
5. Run the task's verification commands.
6. Commit only after both reviews pass.

Inline execution is also valid through `superpowers:executing-plans`, using one
numbered task per review checkpoint.
