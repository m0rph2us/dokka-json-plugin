package morpheus

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.renderers.OutputWriter
import org.jetbrains.dokka.model.*
import org.jetbrains.dokka.model.doc.DocTag
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.Text
import org.jetbrains.dokka.pages.*
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.plugability.plugin
import org.jetbrains.dokka.plugability.querySingle
import org.jetbrains.dokka.renderers.Renderer

data class ClassDocumentation(
    val comment: String,
    val fields: Map<String, PropertyDocumentation>,
    val methods: Map<String, FunctionDocumentation>
)

data class FunctionDocumentation(
    val comment: String,
    val parameters: Map<String, String>,
    val tags: Map<String, String>
)

data class PropertyDocumentation(
    val comment: String,
    val tags: Map<String, String>
)

class JsonDokkaPlugin : DokkaPlugin() {
    val dokkaBasePlugin by lazy { plugin<DokkaBase>() }

    val extensionRenderer by extending {
        CoreExtensions.renderer providing { ctx -> CustomRenderer(ctx) } override dokkaBasePlugin.htmlRenderer
    }
}

class CustomRenderer(val context: DokkaContext) : Renderer {
    private val outputWriter: OutputWriter = context.plugin<DokkaBase>().querySingle { outputWriter }

    private val mapper = jacksonObjectMapper()
        .configure(SerializationFeature.INDENT_OUTPUT, true)

    override fun render(root: RootPageNode) {
        runBlocking(Dispatchers.IO) {
            renderPage(root)
        }
    }

    private fun CoroutineScope.renderPage(node: PageNode) = launch {
        when (node) {
            is ModulePageNode -> renderModulePageNode(node)
        }
    }

    private fun CoroutineScope.renderModulePageNode(node: ModulePageNode) {
        (node.documentable as DModule).packages.forEach { packageItem ->
            packageItem.classlikes.forEach { classlike ->
                classDocumentation(classlike)
            }

            renderChildren(packageItem.children)
        }
    }

    private fun CoroutineScope.renderChildren(documentables: List<Documentable>) {
        documentables.forEach { documentable ->
            when (documentable) {
                is DClass -> {
                    documentable.classlikes.forEach { classlike ->
                        classDocumentation(classlike)
                    }

                    renderChildren(documentable.children)
                }
            }
        }
    }

    private fun CoroutineScope.classDocumentation(classlike: DClasslike) = launch {
        val packageName = classlike.dri.packageName?.replace(".", "/")
        val className = classlike.dri.classNames

        val json = mapper.writeValueAsString(
            ClassDocumentation(
                comment = extractComment(classlike.documentation),
                fields = propertyDocumentation(classlike.properties),
                methods = functionDocumentation(classlike.functions)
            )
        )

        outputWriter.write("$packageName/$className", json, ".json")
    }

    private fun extractComment(documentation: SourceSetDependent<DocumentationNode>): String {
        return StringBuilder().let { sb ->
            documentation.values.forEach { documentationNode ->
                sb.append(buildChildrenComment(documentationNode.children[0].root))
            }
            sb.toString()
        }
    }

    private fun buildChildrenComment(node: DocTag): String {
        return StringBuffer().let { sb ->
            node.children.forEach { docTag ->
                when (docTag) {
                    is Text -> {
                        sb.append(docTag.body + buildChildrenComment(docTag))
                    }
                    else -> {
                        sb.append(buildChildrenComment(docTag))
                    }
                }
            }
            sb.toString()
        }
    }

    private fun propertyDocumentation(properties: List<DProperty>): Map<String, PropertyDocumentation> {
        return properties.associate {
            Pair(
                it.name,
                PropertyDocumentation(
                    comment = extractComment(it.documentation),
                    tags = mapOf()
                )
            )
        }
    }

    private fun functionDocumentation(functions: List<DFunction>): Map<String, FunctionDocumentation> {
        return functions.associate {
            Pair(
                it.name,
                FunctionDocumentation(
                    comment = extractComment(it.documentation),
                    parameters = it.parameters.associate { param ->
                        Pair(
                            param.name!!,
                            extractComment(param.documentation)
                        )
                    },
                    tags = mapOf()
                )
            )
        }
    }
}